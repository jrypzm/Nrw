// The queue and the sound. One AVAudioPlayer at a time, the lock screen and headphone
// controls, calls and unplugged headphones handled, and where you were kept for next time.

import AVFoundation
import MediaPlayer
import UIKit

final class Player: NSObject, AVAudioPlayerDelegate {
    static let shared = Player()
    static let changed = Notification.Name("yrs.player")

    enum Repeat: Int { case off, all, one }

    /// Songs in the order they play; held as songs (by file), never as list positions,
    /// so a rescan of the library can't turn them into other songs.
    private(set) var queue: [Track] = []
    private var base: [Track] = []
    private(set) var at = 0
    private var audio: AVAudioPlayer?
    private(set) var shuffle = false
    private(set) var repeatMode = Repeat.all
    private var resumeAfterInterruption = false
    private var coverFor: String?

    var current: Track? { queue.indices.contains(at) ? queue[at] : nil }
    var isPlaying: Bool { audio?.isPlaying ?? false }
    var position: TimeInterval { audio?.currentTime ?? 0 }
    var duration: TimeInterval { audio.map { $0.duration } ?? current?.secs ?? 0 }
    var upcoming: [Track] { queue.isEmpty ? [] : Array(queue[(at + 1)...]) }

    private override init() {
        super.init()
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .default, options: [])
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(interrupted(_:)), name: AVAudioSession.interruptionNotification, object: s)
        nc.addObserver(self, selector: #selector(routeChanged(_:)), name: AVAudioSession.routeChangeNotification, object: s)
        nc.addObserver(self, selector: #selector(save), name: UIApplication.willResignActiveNotification, object: nil)
        remote()
        restore()
    }

    // MARK: playing

    func play(list: [Track], start: Int) {
        guard !list.isEmpty else { return }
        let start = min(max(start, 0), list.count - 1)
        base = list
        if shuffle {
            var rest = list
            let first = rest.remove(at: start)
            rest.shuffle()
            queue = [first] + rest
            at = 0
        } else {
            queue = list
            at = start
        }
        load(play: true)
    }

    private func load(play: Bool, from time: TimeInterval = 0) {
        audio?.stop()
        audio = nil
        var tries = 0
        while let t = current {
            if let p = try? AVAudioPlayer(contentsOf: Library.shared.url(t)) {
                p.delegate = self
                p.prepareToPlay()
                if time > 0 { p.currentTime = min(time, p.duration) }
                audio = p
                if play { start() }
                break
            }
            // a file that won't play is skipped, never stuck on
            tries += 1
            if tries >= queue.count { break }
            at = (at + 1) % queue.count
        }
        changed()
    }

    private func start() {
        try? AVAudioSession.sharedInstance().setActive(true)
        audio?.play()
    }

    func toggle() {
        guard let a = audio else {
            if current != nil { load(play: true) }
            return
        }
        if a.isPlaying { a.pause() } else { start() }
        changed()
    }

    func next() {
        guard !queue.isEmpty else { return }
        at = (at + 1) % queue.count
        load(play: true)
    }

    func previous() {
        guard !queue.isEmpty else { return }
        if position > 3 { seek(to: 0); return }
        at = at > 0 ? at - 1 : queue.count - 1
        load(play: true)
    }

    func seek(to t: TimeInterval) {
        guard let a = audio else { return }
        a.currentTime = max(0, min(t, a.duration))
        changed()
    }

    func setShuffle(_ on: Bool) {
        shuffle = on
        guard let cur = current else { changed(); return }
        if on {
            var rest = queue
            rest.remove(at: at)
            rest.shuffle()
            queue = [cur] + rest
            at = 0
        } else if !base.isEmpty {
            queue = base
            at = base.firstIndex(of: cur) ?? 0
        }
        changed()
    }

    func cycleRepeat() {
        repeatMode = Repeat(rawValue: (repeatMode.rawValue + 1) % 3) ?? .all
        changed()
    }

    /// After a rescan: songs that are gone drop out of the queue.
    func libraryChanged() {
        let have = Set(Library.shared.tracks.map { $0.path })
        let cur = current
        queue = queue.filter { have.contains($0.path) }
        base = base.filter { have.contains($0.path) }
        if let c = cur, let i = queue.firstIndex(of: c) { at = i } else if at >= queue.count { at = 0 }
        if queue.isEmpty { audio?.stop(); audio = nil }
        changed()
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        switch repeatMode {
        case .one: load(play: true)
        case .all: next()
        case .off:
            if at + 1 < queue.count { next() } else { at = 0; load(play: false) }
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        next()
    }

    // MARK: calls, Siri, unplugged headphones

    @objc private func interrupted(_ n: Notification) {
        guard let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began {
            resumeAfterInterruption = isPlaying
        } else {
            let opts = (n.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt).map(AVAudioSession.InterruptionOptions.init) ?? []
            if resumeAfterInterruption && opts.contains(.shouldResume) { start() }
            resumeAfterInterruption = false
        }
        DispatchQueue.main.async { self.changed() }
    }

    @objc private func routeChanged(_ n: Notification) {
        guard let raw = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable else { return }
        // headphones out: stop, don't play to the room
        DispatchQueue.main.async {
            self.audio?.pause()
            self.changed()
        }
    }

    // MARK: lock screen and headphone buttons

    private func remote() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in
            guard let s = self else { return .commandFailed }
            if !s.isPlaying { s.toggle() }
            return .success
        }
        c.pauseCommand.addTarget { [weak self] _ in
            guard let s = self else { return .commandFailed }
            if s.isPlaying { s.toggle() }
            return .success
        }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in self?.toggle(); return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in self?.next(); return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in self?.previous(); return .success }
        c.changePlaybackPositionCommand.addTarget { [weak self] e in
            guard let e = e as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: e.positionTime)
            return .success
        }
    }

    private func nowPlaying() {
        let center = MPNowPlayingInfoCenter.default()
        guard let t = current else { center.nowPlayingInfo = nil; return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: t.title,
            MPMediaItemPropertyArtist: t.artist,
            MPMediaItemPropertyAlbumTitle: t.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
        ]
        if coverFor == t.path, let old = center.nowPlayingInfo?[MPMediaItemPropertyArtwork] {
            info[MPMediaItemPropertyArtwork] = old
        } else {
            // the cover is decoded only when the lock screen asks for it, at the size it asks
            let url = Library.shared.url(t)
            if Library.hasCover(url) {
                info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: CGSize(width: 600, height: 600)) { size in
                    Library.cover(url, px: max(size.width, size.height)) ?? UIImage()
                }
            }
            coverFor = t.path
        }
        center.nowPlayingInfo = info
    }

    private func changed() {
        nowPlaying()
        NotificationCenter.default.post(name: Player.changed, object: nil)
    }

    // MARK: where you were

    @objc func save() {
        let d = UserDefaults.standard
        d.set(queue.map { $0.path }, forKey: "queue")
        d.set(base.map { $0.path }, forKey: "base")
        d.set(at, forKey: "at")
        d.set(position, forKey: "pos")
        d.set(shuffle, forKey: "shuffle")
        d.set(repeatMode.rawValue, forKey: "repeat")
    }

    private func restore() {
        let d = UserDefaults.standard
        shuffle = d.bool(forKey: "shuffle")
        repeatMode = Repeat(rawValue: d.object(forKey: "repeat") as? Int ?? 1) ?? .all
        let by = Dictionary(Library.shared.tracks.map { ($0.path, $0) }, uniquingKeysWith: { a, _ in a })
        queue = (d.stringArray(forKey: "queue") ?? []).compactMap { by[$0] }
        base = (d.stringArray(forKey: "base") ?? []).compactMap { by[$0] }
        at = min(d.integer(forKey: "at"), max(queue.count - 1, 0))
        if current != nil { load(play: false, from: d.double(forKey: "pos")) }
    }
}
