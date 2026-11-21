// Reading Pinterest the way its own website does (no sign-in, no API key), and pictures fetched
// at the size they're shown, with only a few screens' worth kept.

import ImageIO
import UIKit

enum APIError: Error, CustomStringConvertible {
    case http(Int)
    case offline(String)
    case bad

    var description: String {
        switch self {
        case .http(404): return "Not found."
        case .http(429): return "Pinterest says too many requests. Wait a minute and pull down to try again."
        case .http(let c): return "Pinterest answered \(c). Pull down to try again."
        case .offline(let m): return m
        case .bad: return "Pinterest sent something unexpected. Pull down to try again."
        }
    }
}

typealias JSON = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func s(_ k: String) -> String { self[k] as? String ?? "" }
    func i(_ k: String) -> Int { (self[k] as? NSNumber)?.intValue ?? 0 }
    func o(_ k: String) -> JSON { self[k] as? JSON ?? [:] }
    func a(_ k: String) -> [JSON] { self[k] as? [JSON] ?? [] }
}

enum Style {
    static let text = UIColor(white: 0.9, alpha: 1)
    static let dim = UIColor(white: 0.55, alpha: 1)
    static let link = UIColor(red: 0.42, green: 0.68, blue: 1, alpha: 1)
}

/// What Safari on this iPhone calls itself.
let safariUA: String = {
    let v = UIDevice.current.systemVersion
    let major = v.split(separator: ".").first.map(String.init) ?? "15"
    return "Mozilla/5.0 (iPhone; CPU iPhone OS \(v.replacingOccurrences(of: ".", with: "_")) like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/\(major).0 Mobile/15E148 Safari/604.1"
}()

/// A pin, from a search or a person's pins.
struct Pin {
    let j: JSON
    var id: String { j.s("id") }
    var title: String {
        let t = j.s("grid_title").isEmpty ? j.s("title") : j.s("grid_title")
        return t.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    var about: String { j.s("description").trimmingCharacters(in: .whitespacesAndNewlines) }
    var user: String { j.o("pinner").s("username") }
    var userName: String { j.o("pinner").s("full_name") }
    var board: String { j.o("board").s("name") }
    var link: URL? { URL(string: j.s("link")) }
    var page: URL? { URL(string: "https://www.pinterest.com/pin/\(id)/") }
    var color: UIColor {
        let h = j.s("dominant_color").replacingOccurrences(of: "#", with: "")
        guard h.count == 6, let v = Int(h, radix: 16) else { return UIColor(white: 0.12, alpha: 1) }
        // darkened, so it's a quiet placeholder while the picture loads
        return UIColor(red: CGFloat(v >> 16 & 255) / 600, green: CGFloat(v >> 8 & 255) / 600, blue: CGFloat(v & 255) / 600, alpha: 1)
    }

    /// The picture at one of Pinterest's sizes ("236x", "474x", "736x", "orig"), with height / width.
    func image(_ size: String) -> (URL, CGFloat)? {
        let imgs = j.o("images")
        for k in [size, "474x", "736x", "236x", "orig"] {
            let im = imgs.o(k)
            if let u = URL(string: im.s("url")), im.i("width") > 0 {
                return (u, CGFloat(im.i("height")) / CGFloat(im.i("width")))
            }
        }
        return nil
    }

    var video: URL? {
        let list = j.o("videos").o("video_list")
        for k in ["V_HLSV4", "V_HLSV3_MOBILE", "V_720P", "V_EXP7", "V_EXP6", "V_EXP5", "V_EXP4"] {
            if let u = URL(string: list.o(k).s("url")), !list.o(k).s("url").isEmpty { return u }
        }
        return nil
    }
}

enum Pinterest {
    private static let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpMaximumConnectionsPerHost = 2
        c.timeoutIntervalForRequest = 20
        return URLSession(configuration: c)
    }()

    /// One of the website's own requests. Replies on the main queue with its data and the
    /// bookmark for the next page (nil at the end).
    static func get(_ resource: String, source: String, handler: String, options: JSON, done: @escaping (Result<(Any?, String?), APIError>) -> Void) {
        let body: JSON = ["options": options, "context": [:] as JSON]
        guard let data = try? JSONSerialization.data(withJSONObject: body), let ds = String(data: data, encoding: .utf8) else { done(.failure(.bad)); return }
        var c = URLComponents(string: "https://www.pinterest.com/resource/\(resource)/get/")!
        c.queryItems = [URLQueryItem(name: "source_url", value: source), URLQueryItem(name: "data", value: ds)]
        guard let u = c.url else { done(.failure(.bad)); return }
        var req = URLRequest(url: u)
        req.setValue(safariUA, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("www/\(handler).js", forHTTPHeaderField: "X-Pinterest-PWS-Handler")
        session.dataTask(with: req) { d, resp, err in
            let r: Result<(Any?, String?), APIError>
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            if let err = err {
                r = .failure(.offline((err as NSError).code == NSURLErrorNotConnectedToInternet ? "No internet connection." : err.localizedDescription))
            } else if !(200 ..< 300).contains(code) {
                r = .failure(.http(code))
            } else if let j = d.flatMap({ try? JSONSerialization.jsonObject(with: $0) }) as? JSON {
                let rr = j.o("resource_response")
                let bm = rr.s("bookmark")
                r = .success((rr["data"], bm.isEmpty || bm == "-end-" ? nil : bm))
            } else {
                r = .failure(.bad)
            }
            DispatchQueue.main.async { done(r) }
        }.resume()
    }

    /// Pins in a search or a list reply, including the ones grouped inside "stories".
    static func pins(_ data: Any?) -> [Pin] {
        let list: [JSON] = (data as? [JSON]) ?? ((data as? JSON)?.a("results") ?? [])
        var out: [Pin] = []
        for x in list {
            switch x.s("type") {
            case "pin": out.append(Pin(j: x))
            case "story": out += x.a("objects").filter { $0.s("type") == "pin" }.map { Pin(j: $0) }
            default: if x["images"] != nil { out.append(Pin(j: x)) }
            }
        }
        return out.filter { $0.image("236x") != nil }
    }

    static func search(_ q: String, bookmark: String?, done: @escaping (Result<([Pin], String?), APIError>) -> Void) {
        var o: JSON = ["query": q, "scope": "pins", "page_size": 25]
        if let b = bookmark { o["bookmarks"] = [b] }
        get("BaseSearchResource", source: "/search/pins/?q=" + (q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? q), handler: "search/[scope]", options: o) { r in
            done(r.map { (pins($0.0), $0.1) })
        }
    }

    static func user(_ name: String, bookmark: String?, done: @escaping (Result<([Pin], String?), APIError>) -> Void) {
        var o: JSON = ["username": name, "field_set_key": "grid_item"]
        if let b = bookmark { o["bookmarks"] = [b] }
        get("UserActivityPinsResource", source: "/\(name)/_created/", handler: "[username]/_created", options: o) { r in
            done(r.map { (pins($0.0), $0.1) })
        }
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

