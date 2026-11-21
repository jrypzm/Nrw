// The songs: every audio file in the app's own folder (Files app: On My iPhone > yrs. music).
// Read once, on a background queue, and remembered; next time only new or changed files are read.

import AVFoundation
import ImageIO
import UIKit

struct Track: Codable, Equatable {
    /// Relative to the app's Documents folder, so it survives app updates.
    var path: String
    var title: String
    var artist: String
    var album: String
    var secs: Double
    var mtime: Double
}

struct Album {
    var name: String
    var artist: String
    var tracks: [Track]
}

final class Library {
    static let shared = Library()
    static let changed = Notification.Name("yrs.library")
    static let kinds: Set<String> = ["mp3", "m4a", "m4b", "mp4", "aac", "wav", "aif", "aiff", "aifc", "caf", "flac", "alac"]

    private(set) var tracks: [Track] = []
    private(set) var albums: [Album] = []
    private(set) var scanning = false
    let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    private let work = DispatchQueue(label: "yrs.library", qos: .utility)
    private var again = false

    private var cacheURL: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("library.json")
    }

    init() {
        if let d = try? Data(contentsOf: cacheURL), let t = try? JSONDecoder().decode([Track].self, from: d) {
            set(t)
        }
    }

    func url(_ t: Track) -> URL { docs.appendingPathComponent(t.path) }

    private func set(_ t: [Track]) {
        tracks = t
        var by: [String: Album] = [:]
        var order: [String] = []
        for tr in t {
            let key = tr.album.lowercased()
            if by[key] == nil { by[key] = Album(name: tr.album.isEmpty ? "Unknown album" : tr.album, artist: tr.artist, tracks: []); order.append(key) }
            by[key]!.tracks.append(tr)
        }
        albums = order.compactMap { k in
            guard var a = by[k] else { return nil }
            a.tracks.sort { $0.path.localizedStandardCompare($1.path) == .orderedAscending }
            return a
        }.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    func scan() {
        if scanning { again = true; return }
        scanning = true
        let known = Dictionary(tracks.map { ($0.path, $0) }, uniquingKeysWith: { a, _ in a })
        let docs = self.docs
        work.async {
            let found = Library.walk(docs, known: known)
            if let d = try? JSONEncoder().encode(found) { try? d.write(to: self.cacheURL, options: .atomic) }
            DispatchQueue.main.async {
                self.set(found)
                self.scanning = false
                NotificationCenter.default.post(name: Library.changed, object: nil)
                if self.again { self.again = false; self.scan() }
            }
        }
    }

    private static func walk(_ docs: URL, known: [String: Track]) -> [Track] {
        let keys: [URLResourceKey] = [.contentModificationDateKey, .isRegularFileKey]
        let base = docs.standardizedFileURL.resolvingSymlinksInPath().path
        var found: [Track] = []
        guard let e = FileManager.default.enumerator(at: docs, includingPropertiesForKeys: keys, options: [.skipsHiddenFiles]) else { return [] }
        for case let u as URL in e {
            guard kinds.contains(u.pathExtension.lowercased()) else { continue }
            let v = try? u.resourceValues(forKeys: Set(keys))
            guard v?.isRegularFile == true else { continue }
            let full = u.standardizedFileURL.resolvingSymlinksInPath().path
            guard full.hasPrefix(base + "/") else { continue }
            let rel = String(full.dropFirst(base.count + 1))
            let m = v?.contentModificationDate?.timeIntervalSince1970 ?? 0
            if let t = known[rel], t.mtime == m { found.append(t); continue }
            if let t = autoreleasepool(invoking: { read(u, rel: rel, mtime: m) }) { found.append(t) }
        }
        found.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        return found
    }

    /// Title, artist, album and length from the file's tags; the file name and folder otherwise.
    private static func read(_ u: URL, rel: String, mtime: Double) -> Track? {
        let a = AVURLAsset(url: u, options: [AVURLAssetPreferPreciseDurationAndTimingKey: false])
        var title = u.deletingPathExtension().lastPathComponent
        var artist = "", album = ""
        for item in a.commonMetadata {
            switch item.commonKey {
            case .commonKeyTitle?: if let s = item.stringValue?.trimmingCharacters(in: .whitespaces), !s.isEmpty { title = s }
            case .commonKeyArtist?: if let s = item.stringValue { artist = s.trimmingCharacters(in: .whitespaces) }
            case .commonKeyAlbumName?: if let s = item.stringValue { album = s.trimmingCharacters(in: .whitespaces) }
            default: break
            }
        }
        if album.isEmpty, rel.contains("/") { album = u.deletingLastPathComponent().lastPathComponent }
        let d = a.duration.seconds
        return Track(path: rel, title: title, artist: artist, album: album, secs: d.isFinite ? d : 0, mtime: mtime)
    }

    /// The cover, shrunk to `px` on its longest side: from the file, or a cover image in its folder.
    static func cover(_ u: URL, px: CGFloat) -> UIImage? {
        var data: Data?
        for item in AVURLAsset(url: u).commonMetadata where item.commonKey == .commonKeyArtwork {
            data = item.dataValue
            break
        }
        if data == nil {
            let dir = u.deletingLastPathComponent()
            for n in ["cover.jpg", "cover.png", "folder.jpg", "folder.png", "Cover.jpg", "Folder.jpg", "front.jpg"] {
                if let d = try? Data(contentsOf: dir.appendingPathComponent(n)) { data = d; break }
            }
        }
        guard let d = data, let src = CGImageSourceCreateWithData(d as CFData, nil) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: px,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }

    /// Copies songs or whole folders picked in the Files app into the library.
    /// Calls back with the path of the first song added.
    func importItems(_ urls: [URL], done: ((String?) -> Void)? = nil) {
        let docs = self.docs
        work.async {
            var first: String?
            let fm = FileManager.default
            let inbox = docs.appendingPathComponent("Inbox")
            for u in urls {
                let scoped = u.startAccessingSecurityScopedResource()
                defer { if scoped { u.stopAccessingSecurityScopedResource() } }
                // a file already inside our folder (opened from Files "in place") stays where it is
                if u.standardizedFileURL.path.hasPrefix(docs.standardizedFileURL.path + "/"), !u.path.hasPrefix(inbox.path) { continue }
                var dest = docs.appendingPathComponent(u.lastPathComponent)
                var n = 2
                while fm.fileExists(atPath: dest.path) {
                    let stem = u.deletingPathExtension().lastPathComponent
                    let ext = u.pathExtension
                    dest = docs.appendingPathComponent(ext.isEmpty ? "\(stem) \(n)" : "\(stem) \(n).\(ext)")
                    n += 1
                }
                if u.path.hasPrefix(inbox.path) { try? fm.moveItem(at: u, to: dest) } else { try? fm.copyItem(at: u, to: dest) }
                if first == nil, kinds.contains(dest.pathExtension.lowercased()) { first = dest.lastPathComponent }
            }
            DispatchQueue.main.async {
                self.scan()
                if let done = done {
                    // wait for the scan that includes the new songs
                    var token: NSObjectProtocol?
                    token = NotificationCenter.default.addObserver(forName: Library.changed, object: nil, queue: .main) { _ in
                        if let t = token { NotificationCenter.default.removeObserver(t) }
                        done(first)
                    }
                }
            }
        }
    }

    func delete(_ t: Track) {
        try? FileManager.default.removeItem(at: url(t))
        scan()
    }
}
