// Reading Reddit. Reddit gives new apps no API keys any more and turns away requests that don't
// come from a browser, so this reads Reddit the way Safari does: first a plain request (fast,
// light); if Reddit turns that away, through a hidden Safari view that exists only while it's
// fetching and is let go of 45 seconds after the last request. Signed in, it always goes through
// Safari, which holds your Reddit sign-in. Pictures are fetched at the size they're shown.

import ImageIO
import UIKit
import WebKit

enum APIError: Error, CustomStringConvertible {
    case http(Int)
    case offline(String)
    case bad

    var description: String {
        switch self {
        case .http(let c) where c == 401 || c == 403: return "Reddit won't show this (\(c)): private, banned, or you need to sign in. Pull down to try again."
        case .http(404): return "Not found."
        case .http(429): return "Reddit says too many requests. Wait a minute and pull down to try again."
        case .http(let c): return "Reddit answered \(c)."
        case .offline(let m): return m
        case .bad: return "Reddit sent something unexpected. Pull down to try again."
        }
    }
}

typealias JSON = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func s(_ k: String) -> String { self[k] as? String ?? "" }
    func i(_ k: String) -> Int { (self[k] as? NSNumber)?.intValue ?? 0 }
    func d(_ k: String) -> Double { (self[k] as? NSNumber)?.doubleValue ?? 0 }
    func b(_ k: String) -> Bool { (self[k] as? NSNumber)?.boolValue ?? false }
    func o(_ k: String) -> JSON { self[k] as? JSON ?? [:] }
    func a(_ k: String) -> [JSON] { self[k] as? [JSON] ?? [] }
}

/// What Safari on this iPhone calls itself.
let safariUA: String = {
    let v = UIDevice.current.systemVersion
    let major = v.split(separator: ".").first.map(String.init) ?? "15"
    return "Mozilla/5.0 (iPhone; CPU iPhone OS \(v.replacingOccurrences(of: ".", with: "_")) like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/\(major).0 Mobile/15E148 Safari/604.1"
}()

final class Reddit {
    static let shared = Reddit()
    static let signedIn = Notification.Name("yrs.reddit.signin")

    private let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpShouldSetCookies = false
        c.httpCookieAcceptPolicy = .never
        c.httpMaximumConnectionsPerHost = 2
        c.timeoutIntervalForRequest = 20
        return URLSession(configuration: c)
    }()

    var isSignedIn: Bool { UserDefaults.standard.bool(forKey: "signedIn") }
    /// Set once Reddit has turned a plain request away; remembered.
    private var viaSafari: Bool {
        get { UserDefaults.standard.bool(forKey: "viaSafari") }
        set { UserDefaults.standard.set(newValue, forKey: "viaSafari") }
    }

    func url(_ path: String, _ q: [String: String] = [:]) -> URL {
        var p = path.hasPrefix("/") ? path : "/" + path
        while p.count > 1, p.hasSuffix("/") { p.removeLast() }
        var c = URLComponents()
        c.scheme = "https"
        c.host = "old.reddit.com"
        c.path = p == "/" ? "/.json" : p + ".json"
        var items = [URLQueryItem(name: "raw_json", value: "1")]
        items += q.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        c.queryItems = items
        return c.url!
    }

    /// GET some JSON from Reddit; the reply on the main queue.
    func get(_ path: String, _ q: [String: String] = [:], done: @escaping (Result<Any, APIError>) -> Void) {
        let u = url(path, q)
        let finish: (Result<Data, APIError>) -> Void = { r in
            done(r.flatMap { d in
                guard let j = try? JSONSerialization.jsonObject(with: d) else { return .failure(.bad) }
                return .success(j)
            })
        }
        if viaSafari || isSignedIn { Web.shared.fetch(u, finish); return }
        var req = URLRequest(url: u)
        req.setValue(safariUA, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        req.setValue("over18=1", forHTTPHeaderField: "Cookie")
        session.dataTask(with: req) { data, resp, err in
            DispatchQueue.main.async {
                if let err = err {
                    finish(.failure(.offline((err as NSError).code == NSURLErrorNotConnectedToInternet ? "No internet connection." : err.localizedDescription)))
                    return
                }
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                let d = data ?? Data()
                let first = d.first { !($0 == 32 || $0 == 10 || $0 == 13 || $0 == 9) }
                let isJSON = first == UInt8(ascii: "{") || first == UInt8(ascii: "[")
                if (200 ..< 300).contains(code), isJSON { finish(.success(d)); return }
                if code == 404 { finish(.failure(.http(404))); return }
                // turned away (blocked, or a page instead of JSON): read it the way Safari does from now on
                self.viaSafari = true
                Web.shared.fetch(u, finish)
            }
        }.resume()
    }

    // MARK: signing in

    func signedIn(_ yes: Bool) {
        UserDefaults.standard.set(yes, forKey: "signedIn")
        Web.shared.reset()
        NotificationCenter.default.post(name: Reddit.signedIn, object: nil)
    }

    func signOut() {
        let store = WKWebsiteDataStore.default()
        store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) { [weak self] in
            self?.signedIn(false)
        }
    }
}

/// A hidden Safari view that opens each Reddit address the way Safari does when you open a link
/// (Reddit hands its pages to that, but refuses background requests), then reads the page's text.
/// One page at a time; let go of 45 seconds after the last one.
final class Web: NSObject, WKNavigationDelegate {
    static let shared = Web()
    private var view: WKWebView?
    private var queue: [(URL, (Result<Data, APIError>) -> Void)] = []
    private var current: ((Result<Data, APIError>) -> Void)?
    private var status = 0
    private var cookieSet = false
    private var idle: Timer?
    private var url: URL?
    private var tries = 0
    /// The old.reddit.com front page is opened first, so a browser check runs on a normal page.
    private var warming = false
    private static let home = URL(string: "https://old.reddit.com/")!

    func fetch(_ u: URL, _ done: @escaping (Result<Data, APIError>) -> Void) {
        idle?.invalidate()
        queue.append((u, done))
        next()
    }

    private func next() {
        guard current == nil, !queue.isEmpty else { return }
        let fresh = view == nil
        let v = view ?? make()
        let (u, done): (URL, (Result<Data, APIError>) -> Void) = fresh ? (Web.home, { (_: Result<Data, APIError>) in }) : queue.removeFirst()
        warming = fresh
        current = done
        status = 0
        tries = 0
        url = u
        let go = { v.load(URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 25)) }
        guard !cookieSet else { go(); return }
        cookieSet = true
        // grown-up posts are shown when you're signed out too, as far as Reddit allows
        if let adult = HTTPCookie(properties: [.domain: ".reddit.com", .path: "/", .name: "over18", .value: "1", .secure: "TRUE", .expires: Date(timeIntervalSinceNow: 31_536_000)]) {
            v.configuration.websiteDataStore.httpCookieStore.setCookie(adult) { go() }
        } else {
            go()
        }
    }

    private func make() -> WKWebView {
        let c = WKWebViewConfiguration()
        c.websiteDataStore = .default()
        let v = WKWebView(frame: UIScreen.main.bounds, configuration: c)
        v.customUserAgent = safariUA
        v.navigationDelegate = self
        v.isUserInteractionEnabled = false
        // on screen, behind the app, so the page runs as it would in Safari
        if let w = UIApplication.shared.windows.first(where: { $0.isKeyWindow }) ?? UIApplication.shared.windows.first {
            w.insertSubview(v, at: 0)
        }
        view = v
        return v
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if navigationResponse.isForMainFrame { status = (navigationResponse.response as? HTTPURLResponse)?.statusCode ?? 0 }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if warming {
            // let any check on the page finish, then go on
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
                self?.warming = false
                self?.finish(.success(Data()))
            }
            return
        }
        read(webView)
    }

    private func read(_ webView: WKWebView) {
        let asked = url
        webView.evaluateJavaScript("document.body ? document.body.innerText : ''") { [weak self] any, _ in
            guard let s = self, s.url != nil, s.url == asked else { return }
            let text = any as? String ?? ""
            let first = text.first { !$0.isWhitespace }
            if first == "{" || first == "[" {
                s.finish(.success(Data(text.utf8)))
                return
            }
            if s.status == 404 { s.finish(.failure(.http(404))); return }
            // perhaps a browser check that reloads the page: wait, then open it again
            if s.tries < 2, let u = s.url {
                s.tries += 1
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    guard s.url == u, let v = s.view else { return }
                    v.load(URLRequest(url: u, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 25))
                }
                return
            }
            let said = text.split(whereSeparator: { $0.isWhitespace }).prefix(24).joined(separator: " ")
            let code = s.status > 0 ? "\(s.status)" : "?"
            if text.lowercased().contains("blocked") {
                s.finish(.failure(.offline("Reddit blocked this connection (\(code)). Reddit said: \"\(said)\"")))
            } else {
                s.finish(.failure(.offline("Reddit sent a page instead of posts (\(code)): \"\(said)\"")))
            }
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(error) }

    private func failed(_ e: Error) {
        let ns = e as NSError
        if ns.code == NSURLErrorCancelled { return }
        finish(.failure(.offline(ns.code == NSURLErrorNotConnectedToInternet ? "No internet connection." : ns.localizedDescription)))
    }

    private func finish(_ r: Result<Data, APIError>) {
        let done = current
        current = nil
        url = nil
        done?(r)
        letGoLater()
        next()
    }

    private func letGoLater() {
        idle?.invalidate()
        idle = Timer.scheduledTimer(withTimeInterval: 45, repeats: false) { [weak self] _ in self?.reset() }
    }

    /// Lets go of the hidden Safari view (and the memory it holds).
    func reset() {
        idle?.invalidate()
        idle = nil
        guard current == nil, queue.isEmpty else { return }
        view?.stopLoading()
        view?.navigationDelegate = nil
        view?.removeFromSuperview()
        view = nil
        cookieSet = false
        DispatchQueue.main.async { malloc_zone_pressure_relief(nil, 0) }
    }
}

// MARK: - pictures

enum Images {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.totalCostLimit = 6_000_000   // a few screens' worth, then the oldest go
        return c
    }()

    private static let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpMaximumConnectionsPerHost = 3
        c.httpAdditionalHeaders = ["User-Agent": safariUA, "Accept": "image/jpeg,image/png,image/*;q=0.8"]
        return URLSession(configuration: c)
    }()

    static func forget() { cache.removeAllObjects() }

    /// A picture shrunk to `px` pixels on its longest side.
    @discardableResult
    static func load(_ u: URL, px: CGFloat, done: @escaping (UIImage?) -> Void) -> URLSessionDataTask? {
        get(u, key: "\(Int(px))", make: { downsample($0, px: px) }, done: done)
    }

    /// A square thumbnail, filled to `side` points.
    @discardableResult
    static func thumb(_ u: URL, side: CGFloat, done: @escaping (UIImage?) -> Void) -> URLSessionDataTask? {
        let scale = UIScreen.main.scale
        return get(u, key: "t\(Int(side))", make: { d in
            guard let img = downsample(d, px: side * scale * 2) else { return nil }
            let f = UIGraphicsImageRendererFormat()
            f.scale = scale
            f.opaque = true
            return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: f).image { _ in
                let s = img.size, k = max(side / s.width, side / s.height)
                img.draw(in: CGRect(x: (side - s.width * k) / 2, y: (side - s.height * k) / 2, width: s.width * k, height: s.height * k))
            }
        }, done: done)
    }

    private static func get(_ u: URL, key: String, make: @escaping (Data) -> UIImage?, done: @escaping (UIImage?) -> Void) -> URLSessionDataTask? {
        let k = (key + u.absoluteString) as NSString
        if let i = cache.object(forKey: k) { done(i); return nil }
        let t = session.dataTask(with: u) { data, _, _ in
            let img = data.flatMap { d in autoreleasepool { make(d) } }
            DispatchQueue.main.async {
                if let i = img { cache.setObject(i, forKey: k, cost: Int(i.size.width * i.size.height * i.scale * i.scale * 4)) }
                done(img)
            }
        }
        t.resume()
        return t
    }

    private static func downsample(_ d: Data, px: CGFloat) -> UIImage? {
        guard let src = CGImageSourceCreateWithData(d as CFData, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: px,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}

// MARK: - words

private let relative: RelativeDateTimeFormatter = {
    let f = RelativeDateTimeFormatter()
    f.unitsStyle = .short
    return f
}()

func ago(_ unix: Double) -> String {
    unix > 0 ? relative.localizedString(for: Date(timeIntervalSince1970: unix), relativeTo: Date()) : ""
}

func count(_ n: Int) -> String {
    if n >= 1_000_000 { return String(format: "%.1fm", Double(n) / 1_000_000).replacingOccurrences(of: ".0m", with: "m") }
    if n >= 1000 { return String(format: "%.1fk", Double(n) / 1000).replacingOccurrences(of: ".0k", with: "k") }
    return "\(n)"
}

func joined(_ bits: [String]) -> String { bits.filter { !$0.isEmpty }.joined(separator: " · ") }
