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
        case .http(401), .http(403): return "Reddit won't show this (private, banned, or you need to sign in)."
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
        c.host = "www.reddit.com"
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

/// A hidden Safari view on reddit.com, used to fetch when a plain request isn't let through.
final class Web: NSObject, WKNavigationDelegate {
    static let shared = Web()
    private var view: WKWebView?
    private var ready = false
    private var waiting: [() -> Void] = []
    private var idle: Timer?

    func fetch(_ u: URL, _ done: @escaping (Result<Data, APIError>) -> Void) {
        idle?.invalidate()
        whenReady { [weak self] ok in
            guard ok, let v = self?.view else { done(.failure(.offline("Couldn't reach Reddit."))); return }
            let js = "const r = await fetch(u, {credentials: 'include', headers: {'Accept': 'application/json'}}); return [r.status, await r.text()];"
            v.callAsyncJavaScript(js, arguments: ["u": u.absoluteString], in: nil, in: .defaultClient) { r in
                self?.letGoLater()
                switch r {
                case .failure(let e):
                    done(.failure(.offline(e.localizedDescription)))
                case .success(let any):
                    guard let a = any as? [Any], a.count == 2, let text = a[1] as? String else { done(.failure(.bad)); return }
                    let code = (a[0] as? NSNumber)?.intValue ?? 0
                    done((200 ..< 300).contains(code) ? .success(Data(text.utf8)) : .failure(.http(code)))
                }
            }
        }
    }

    private func whenReady(_ f: @escaping (Bool) -> Void) {
        if ready, view != nil { f(true); return }
        waiting.append { [weak self] in f(self?.ready == true) }
        guard view == nil else { return }
        let c = WKWebViewConfiguration()
        c.websiteDataStore = .default()
        let v = WKWebView(frame: CGRect(x: 0, y: 0, width: 10, height: 10), configuration: c)
        v.customUserAgent = safariUA
        v.navigationDelegate = self
        view = v
        ready = false
        // grown-up posts are shown when you're signed out too, as far as Reddit allows
        let adult = HTTPCookie(properties: [.domain: ".reddit.com", .path: "/", .name: "over18", .value: "1", .secure: "TRUE", .expires: Date(timeIntervalSinceNow: 31_536_000)])
        let go = { v.load(URLRequest(url: URL(string: "https://www.reddit.com/robots.txt")!)) }
        if let adult = adult { c.websiteDataStore.httpCookieStore.setCookie(adult) { go() } } else { go() }
    }

    private func flush() {
        let w = waiting
        waiting = []
        w.forEach { $0() }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        ready = true
        flush()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed() }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed() }

    private func failed() {
        ready = false
        flush()
        view = nil
    }

    private func letGoLater() {
        idle?.invalidate()
        idle = Timer.scheduledTimer(withTimeInterval: 45, repeats: false) { [weak self] _ in self?.reset() }
    }

    /// Lets go of the hidden Safari view (and the memory it holds).
    func reset() {
        idle?.invalidate()
        idle = nil
        guard waiting.isEmpty else { return }
        view?.navigationDelegate = nil
        view = nil
        ready = false
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
