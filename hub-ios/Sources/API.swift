// Talking to GitHub: one small HTTP client, no cache kept in memory or on disk.
// Signed out it reads public GitHub (60 requests an hour); with a token, 5,000 an hour,
// plus your notifications, repositories, stars and pull requests.

import Foundation
import Security

enum APIError: Error, CustomStringConvertible {
    case http(Int, String)
    case offline(String)
    case bad

    var description: String {
        switch self {
        case .http(401, _): return "GitHub didn't accept the token. Sign in again."
        case .http(403, let m) where m.lowercased().contains("rate limit"): return "GitHub's hourly limit is used up. Sign in for a higher one, or wait a little."
        case .http(404, _): return "Not found, or private."
        case .http(let c, let m): return m.isEmpty ? "GitHub answered \(c)." : m
        case .offline(let m): return m
        case .bad: return "GitHub sent something unexpected."
        }
    }
}

final class API {
    static let shared = API()
    static let base = URL(string: "https://api.github.com")!
    static let signedIn = Notification.Name("yrs.hub.signin")

    private let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpMaximumConnectionsPerHost = 2
        c.timeoutIntervalForRequest = 20
        c.httpAdditionalHeaders = ["User-Agent": "yrs-hub", "X-GitHub-Api-Version": "2022-11-28"]
        return URLSession(configuration: c)
    }()

    private(set) var token: String? = Keychain.read()
    /// The signed-in user's login, once known.
    private(set) var me: String?
    var isSignedIn: Bool { token != nil }
    /// Used only for the screenshots taken with each build (more requests an hour); never saved.
    var lentToken: String?

    // MARK: requests

    func url(_ path: String, _ query: [String: String] = [:]) -> URL {
        if path.hasPrefix("http"), let u = URL(string: path) { return add(query, to: u) }
        return add(query, to: API.base.appendingPathComponent(path))
    }

    private func add(_ q: [String: String], to u: URL) -> URL {
        guard !q.isEmpty, var c = URLComponents(url: u, resolvingAgainstBaseURL: false) else { return u }
        c.queryItems = (c.queryItems ?? []) + q.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        return c.url ?? u
    }

    /// GET some JSON; the reply on the main queue.
    func json(_ path: String, _ query: [String: String] = [:], done: @escaping (Result<Any, APIError>) -> Void) {
        send("GET", url(path, query), accept: "application/vnd.github+json") { r in
            done(r.flatMap { data in
                guard let j = try? JSONSerialization.jsonObject(with: data) else { return .failure(.bad) }
                return .success(j)
            })
        }
    }

    /// GET a file or README as plain text (raw); nil text for a binary file.
    func raw(_ path: String, _ query: [String: String] = [:], done: @escaping (Result<String?, APIError>) -> Void) {
        send("GET", url(path, query), accept: "application/vnd.github.raw") { r in
            done(r.map { data in
                if data.prefix(4096).contains(0) { return nil }
                return String(decoding: data, as: UTF8.self)
            })
        }
    }

    /// A change: star, unstar, mark read.
    func act(_ method: String, _ path: String, done: @escaping (Bool) -> Void) {
        send(method, url(path), accept: "application/vnd.github+json") { r in
            if case .success = r { done(true) } else { done(false) }
        }
    }

    private func send(_ method: String, _ u: URL, accept: String, done: @escaping (Result<Data, APIError>) -> Void) {
        var req = URLRequest(url: u)
        req.httpMethod = method
        req.setValue(accept, forHTTPHeaderField: "Accept")
        if method == "PUT" { req.setValue("0", forHTTPHeaderField: "Content-Length") }
        if let t = token ?? lentToken { req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        session.dataTask(with: req) { data, resp, err in
            let r: Result<Data, APIError>
            if let err = err {
                r = .failure(.offline((err as NSError).code == NSURLErrorNotConnectedToInternet ? "No internet connection." : err.localizedDescription))
            } else if let h = resp as? HTTPURLResponse, !(200 ..< 300).contains(h.statusCode) {
                let msg = (data.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any])?["message"] as? String ?? ""
                r = .failure(.http(h.statusCode, msg))
            } else {
                r = .success(data ?? Data())
            }
            DispatchQueue.main.async { done(r) }
        }.resume()
    }

    // MARK: signing in

    /// Checks a token with GitHub and keeps it in the Keychain if it works.
    func signIn(_ t: String, done: @escaping (String?) -> Void) {
        let old = token
        token = t.trimmingCharacters(in: .whitespacesAndNewlines)
        json("user") { r in
            switch r {
            case .success(let j):
                self.me = (j as? [String: Any])?["login"] as? String
                Keychain.save(self.token!)
                NotificationCenter.default.post(name: API.signedIn, object: nil)
                done(nil)
            case .failure(let e):
                self.token = old
                done(e.description)
            }
        }
    }

    func signOut() {
        token = nil
        me = nil
        Keychain.delete()
        NotificationCenter.default.post(name: API.signedIn, object: nil)
    }

    /// Finds the signed-in user's login (needed for a few lists).
    func whoAmI(_ done: @escaping (String?) -> Void) {
        if let m = me { done(m); return }
        guard isSignedIn else { done(nil); return }
        json("user") { r in
            if case .success(let j) = r { self.me = (j as? [String: Any])?["login"] as? String }
            done(self.me)
        }
    }
}

/// The token lives in the iPhone's Keychain, not in the app's files.
enum Keychain {
    private static let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "yrs.hub", kSecAttrAccount as String: "token"]

    static func read() -> String? {
        var item: CFTypeRef?
        var query = q
        query[kSecReturnData as String] = true
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let d = item as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func save(_ t: String) {
        delete()
        var a = q
        a[kSecValueData as String] = Data(t.utf8)
        a[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(a as CFDictionary, nil)
    }

    static func delete() { SecItemDelete(q as CFDictionary) }
}

// MARK: - reading the JSON

typealias JSON = [String: Any]

extension Dictionary where Key == String, Value == Any {
    func s(_ k: String) -> String { self[k] as? String ?? "" }
    func i(_ k: String) -> Int { self[k] as? Int ?? 0 }
    func o(_ k: String) -> JSON { self[k] as? JSON ?? [:] }
}

private let isoParser: ISO8601DateFormatter = ISO8601DateFormatter()
private let relative: RelativeDateTimeFormatter = {
    let f = RelativeDateTimeFormatter()
    f.unitsStyle = .short
    return f
}()

/// "3 days ago", from GitHub's timestamps.
func ago(_ iso: String) -> String {
    guard let d = isoParser.date(from: iso) else { return "" }
    return relative.localizedString(for: d, relativeTo: Date())
}

func count(_ n: Int) -> String {
    n >= 1000 ? String(format: "%.1fk", Double(n) / 1000).replacingOccurrences(of: ".0k", with: "k") : "\(n)"
}
