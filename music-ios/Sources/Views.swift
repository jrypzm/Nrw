// The screens: Songs and Albums as two words, a slim strip for what's playing, and the player.
// Plain UIKit, system fonts, no images in lists; the player draws one cover, at screen size.

import UIKit
import UniformTypeIdentifiers

let dim = UIColor(white: 0.36, alpha: 1)
let mute = UIColor(white: 0.56, alpha: 1)
let accent = UIColor(red: 0.95, green: 0.64, blue: 0.48, alpha: 1)

func clock(_ t: TimeInterval) -> String {
    guard t.isFinite, t >= 0 else { return "0:00" }
    let s = Int(t.rounded(.down))
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
}

func word(_ title: String, size: CGFloat, weight: UIFont.Weight = .bold) -> UIButton {
    let b = UIButton(type: .system)
    b.setTitle(title, for: .normal)
    b.titleLabel?.font = .systemFont(ofSize: size, weight: weight)
    b.contentHorizontalAlignment = .leading
    return b
}

// MARK: - Songs and Albums

final class RootViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UIDocumentPickerDelegate {
    private enum Mode { case songs, albums, album(Int) }
    private var mode = Mode.songs
    private let songsWord = word("Songs", size: 30)
    private let albumsWord = word("Albums", size: 30)
    private let back = word("‹ Albums", size: 15, weight: .semibold)
    private let add = UIButton(type: .system)
    private let table = UITableView(frame: .zero, style: .plain)
    private let empty = UILabel()
    private let strip = NowStrip()

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        songsWord.addTarget(self, action: #selector(showSongs), for: .touchUpInside)
        albumsWord.addTarget(self, action: #selector(showAlbums), for: .touchUpInside)
        back.addTarget(self, action: #selector(showAlbums), for: .touchUpInside)
        back.setTitleColor(mute, for: .normal)
        add.setImage(UIImage(systemName: "plus", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .semibold)), for: .normal)
        add.tintColor = .white
        add.accessibilityLabel = "Add music"
        add.addTarget(self, action: #selector(pick), for: .touchUpInside)

        table.backgroundColor = .black
        table.separatorStyle = .none
        table.indicatorStyle = .white
        table.rowHeight = 54
        table.dataSource = self
        table.delegate = self
        table.register(Row.self, forCellReuseIdentifier: "row")

        empty.text = "Add your music.\n\nTap + to choose songs or folders, or put them in Files › On My iPhone › yrs. music."
        empty.numberOfLines = 0
        empty.textColor = mute
        empty.font = .systemFont(ofSize: 16)

        strip.addTarget(self, action: #selector(openPlayer), for: .touchUpInside)

        for v in [songsWord, albumsWord, back, add, table, empty, strip] as [UIView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(v)
        }
        let g = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            songsWord.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 20),
            songsWord.topAnchor.constraint(equalTo: g.topAnchor, constant: 10),
            albumsWord.leadingAnchor.constraint(equalTo: songsWord.trailingAnchor, constant: 18),
            albumsWord.firstBaselineAnchor.constraint(equalTo: songsWord.firstBaselineAnchor),
            add.trailingAnchor.constraint(equalTo: g.trailingAnchor, constant: -14),
            add.centerYAnchor.constraint(equalTo: songsWord.centerYAnchor),
            add.widthAnchor.constraint(equalToConstant: 44),
            add.heightAnchor.constraint(equalToConstant: 44),
            back.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 20),
            back.topAnchor.constraint(equalTo: songsWord.bottomAnchor, constant: 0),
            table.topAnchor.constraint(equalTo: songsWord.bottomAnchor, constant: 8),
            table.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            table.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            table.bottomAnchor.constraint(equalTo: strip.topAnchor),
            empty.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 20),
            empty.trailingAnchor.constraint(equalTo: g.trailingAnchor, constant: -20),
            empty.topAnchor.constraint(equalTo: songsWord.bottomAnchor, constant: 30),
            strip.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            strip.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            strip.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            strip.topAnchor.constraint(equalTo: g.bottomAnchor, constant: -60),
        ])

        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(libraryChanged), name: Library.changed, object: nil)
        nc.addObserver(self, selector: #selector(playerChanged), name: Player.changed, object: nil)
        refresh()
        playerChanged()
    }

    // MARK: modes

    @objc private func showSongs() { mode = .songs; refresh() }
    @objc private func showAlbums() { mode = .albums; refresh() }

    private func refresh() {
        let inAlbum: Bool
        if case .album = mode { inAlbum = true } else { inAlbum = false }
        let songs: Bool
        if case .songs = mode { songs = true } else { songs = false }
        songsWord.setTitleColor(songs ? .white : dim, for: .normal)
        albumsWord.setTitleColor(songs ? dim : .white, for: .normal)
        back.isHidden = !inAlbum
        table.contentInset.top = inAlbum ? 26 : 0
        empty.isHidden = !Library.shared.tracks.isEmpty
        table.isHidden = Library.shared.tracks.isEmpty
        table.reloadData()
        if table.numberOfRows(inSection: 0) > 0 { table.scrollToRow(at: IndexPath(row: 0, section: 0), at: .top, animated: false) }
    }

    @objc private func libraryChanged() {
        Player.shared.libraryChanged()
        if case .album(let i) = mode, i >= Library.shared.albums.count { mode = .albums }
        empty.isHidden = !Library.shared.tracks.isEmpty
        table.isHidden = Library.shared.tracks.isEmpty
        table.reloadData()
    }

    @objc private func playerChanged() {
        strip.update()
        for case let r as Row in table.visibleCells { r.mark(playing: r.path != nil && r.path == Player.shared.current?.path) }
    }

    private var list: [Track] {
        switch mode {
        case .songs: return Library.shared.tracks
        case .album(let i): return Library.shared.albums.indices.contains(i) ? Library.shared.albums[i].tracks : []
        case .albums: return []
        }
    }

    // MARK: table

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if case .albums = mode { return Library.shared.albums.count }
        return list.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let r = tableView.dequeueReusableCell(withIdentifier: "row", for: indexPath) as! Row
        if case .albums = mode {
            let a = Library.shared.albums[indexPath.row]
            r.show(title: a.name, detail: a.tracks.count == 1 ? "1 song" : "\(a.tracks.count) songs", path: nil)
        } else {
            let t = list[indexPath.row]
            r.show(title: t.title, detail: clock(t.secs), path: t.path)
            r.mark(playing: t.path == Player.shared.current?.path)
        }
        return r
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if case .albums = mode {
            mode = .album(indexPath.row)
            refresh()
            return
        }
        // one tap plays
        Player.shared.play(list: list, start: indexPath.row)
    }

    func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        if case .albums = mode { return nil }
        let t = list[indexPath.row]
        let del = UIContextualAction(style: .destructive, title: "Delete") { _, _, done in
            Library.shared.delete(t)
            done(true)
        }
        return UISwipeActionsConfiguration(actions: [del])
    }

    // MARK: adding music

    @objc private func pick() {
        let p = UIDocumentPickerViewController(forOpeningContentTypes: [.audio, .folder], asCopy: false)
        p.allowsMultipleSelection = true
        p.delegate = self
        present(p, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        Library.shared.importItems(urls)
    }

    // MARK: the player

    @objc private func openPlayer() {
        guard Player.shared.current != nil else { return }
        let p = PlayerViewController()
        // full screen: UIKit then lets go of the list underneath while the player is up
        p.modalPresentationStyle = .fullScreen
        present(p, animated: true)
    }

    func demoPlay(open: Bool) {
        guard !Library.shared.tracks.isEmpty else { return }
        Player.shared.play(list: Library.shared.tracks, start: 0)
        if open { openPlayer() }
    }
}

/// A song or album: its name, and the length or count on the right.
final class Row: UITableViewCell {
    private let title = UILabel()
    private let detail = UILabel()
    private(set) var path: String?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .black
        let sel = UIView()
        sel.backgroundColor = UIColor(white: 0.1, alpha: 1)
        selectedBackgroundView = sel
        title.font = .systemFont(ofSize: 17, weight: .semibold)
        title.lineBreakMode = .byTruncatingTail
        detail.font = .monospacedDigitSystemFont(ofSize: 14, weight: .medium)
        detail.textColor = mute
        detail.setContentHuggingPriority(.required, for: .horizontal)
        detail.setContentCompressionResistancePriority(.required, for: .horizontal)
        for v in [title, detail] {
            v.translatesAutoresizingMaskIntoConstraints = false
            contentView.addSubview(v)
        }
        NSLayoutConstraint.activate([
            title.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 20),
            title.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            detail.leadingAnchor.constraint(greaterThanOrEqualTo: title.trailingAnchor, constant: 12),
            detail.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -20),
            detail.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func show(title t: String, detail d: String, path p: String?) {
        title.text = t
        detail.text = d
        path = p
        mark(playing: false)
    }

    func mark(playing: Bool) {
        title.textColor = playing ? accent : .white
        detail.textColor = playing ? accent : mute
    }
}

/// What's playing, along the bottom: tap it for the player, or the button to pause.
final class NowStrip: UIControl {
    private let title = UILabel()
    private let artist = UILabel()
    private let button = UIButton(type: .system)
    private let line = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(white: 0.07, alpha: 1)
        line.backgroundColor = UIColor(white: 0.15, alpha: 1)
        title.font = .systemFont(ofSize: 15, weight: .semibold)
        title.textColor = .white
        artist.font = .systemFont(ofSize: 13)
        artist.textColor = mute
        button.tintColor = .white
        button.addTarget(self, action: #selector(tap), for: .touchUpInside)
        for v in [line, title, artist, button] as [UIView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            v.isUserInteractionEnabled = v === button
            addSubview(v)
        }
        NSLayoutConstraint.activate([
            line.topAnchor.constraint(equalTo: topAnchor),
            line.leadingAnchor.constraint(equalTo: leadingAnchor),
            line.trailingAnchor.constraint(equalTo: trailingAnchor),
            line.heightAnchor.constraint(equalToConstant: 0.5),
            title.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            title.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            title.trailingAnchor.constraint(lessThanOrEqualTo: button.leadingAnchor, constant: -10),
            artist.leadingAnchor.constraint(equalTo: title.leadingAnchor),
            artist.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 2),
            artist.trailingAnchor.constraint(lessThanOrEqualTo: button.leadingAnchor, constant: -10),
            button.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            button.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            button.widthAnchor.constraint(equalToConstant: 44),
            button.heightAnchor.constraint(equalToConstant: 44),
        ])
        update()
    }

    required init?(coder: NSCoder) { fatalError() }

    @objc private func tap() { Player.shared.toggle() }

    func update() {
        let p = Player.shared
        isHidden = p.current == nil
        title.text = p.current?.title
        artist.text = p.current.map { $0.artist.isEmpty ? $0.album : $0.artist }
        button.setImage(UIImage(systemName: p.isPlaying ? "pause.fill" : "play.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20)), for: .normal)
        button.accessibilityLabel = p.isPlaying ? "Pause" : "Play"
    }
}

// MARK: - The player

final class PlayerViewController: UIViewController {
    private let cover = UIImageView()
    private let letter = UILabel()
    private let songTitle = UILabel()
    private let artist = UILabel()
    private let slider = UISlider()
    private let now = UILabel()
    private let left = UILabel()
    private let prevButton = UIButton(type: .system)
    private let play = UIButton(type: .system)
    private let nextButton = UIButton(type: .system)
    private let shuffleWord = word("Shuffle", size: 15, weight: .semibold)
    private let repeatWord = word("Repeat", size: 15, weight: .semibold)
    private let format = UILabel()
    private let close = UIButton(type: .system)
    private var timer: Timer?
    private var dragging = false
    private var shownCover: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        cover.contentMode = .scaleAspectFill
        cover.clipsToBounds = true
        cover.layer.cornerRadius = 8
        cover.backgroundColor = UIColor(white: 0.08, alpha: 1)
        letter.font = .systemFont(ofSize: 120, weight: .bold)
        letter.textColor = UIColor(white: 0.22, alpha: 1)
        letter.textAlignment = .center
        songTitle.font = .systemFont(ofSize: 24, weight: .bold)
        songTitle.textColor = .white
        artist.font = .systemFont(ofSize: 16)
        artist.textColor = mute
        for l in [now, left] {
            l.font = .monospacedDigitSystemFont(ofSize: 12, weight: .medium)
            l.textColor = mute
        }
        left.textAlignment = .right
        slider.minimumTrackTintColor = accent
        slider.maximumTrackTintColor = UIColor(white: 0.2, alpha: 1)
        slider.setThumbImage(Self.dot(10), for: .normal)
        slider.setThumbImage(Self.dot(16), for: .highlighted)
        slider.addTarget(self, action: #selector(scrubbing), for: .valueChanged)
        slider.addTarget(self, action: #selector(scrubbed), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        let big = UIImage.SymbolConfiguration(pointSize: 34, weight: .bold)
        let mid = UIImage.SymbolConfiguration(pointSize: 24, weight: .bold)
        prevButton.setImage(UIImage(systemName: "backward.fill", withConfiguration: mid), for: .normal)
        nextButton.setImage(UIImage(systemName: "forward.fill", withConfiguration: mid), for: .normal)
        play.setPreferredSymbolConfiguration(big, forImageIn: .normal)
        prevButton.accessibilityLabel = "Previous"
        nextButton.accessibilityLabel = "Next"
        prevButton.addTarget(self, action: #selector(goPrev), for: .touchUpInside)
        play.addTarget(self, action: #selector(goPlay), for: .touchUpInside)
        nextButton.addTarget(self, action: #selector(goNext), for: .touchUpInside)
        shuffleWord.addTarget(self, action: #selector(goShuffle), for: .touchUpInside)
        repeatWord.addTarget(self, action: #selector(goRepeat), for: .touchUpInside)
        repeatWord.contentHorizontalAlignment = .trailing
        format.font = .systemFont(ofSize: 11, weight: .semibold)
        format.textColor = dim

        close.setImage(UIImage(systemName: "chevron.down", withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .semibold)), for: .normal)
        close.tintColor = mute
        close.accessibilityLabel = "Close"
        close.addTarget(self, action: #selector(goClose), for: .touchUpInside)
        let swipe = UISwipeGestureRecognizer(target: self, action: #selector(goClose))
        swipe.direction = .down
        view.addGestureRecognizer(swipe)

        let controls = UIStackView(arrangedSubviews: [prevButton, play, nextButton])
        controls.axis = .horizontal
        controls.distribution = .equalCentering
        let words = UIStackView(arrangedSubviews: [shuffleWord, repeatWord])
        words.distribution = .fillEqually

        for v in [close, cover, letter, songTitle, artist, slider, now, left, controls, words, format] as [UIView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(v)
        }
        let g = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            close.topAnchor.constraint(equalTo: g.topAnchor, constant: 2),
            close.centerXAnchor.constraint(equalTo: g.centerXAnchor),
            close.widthAnchor.constraint(equalToConstant: 60),
            close.heightAnchor.constraint(equalToConstant: 36),
            cover.topAnchor.constraint(equalTo: close.bottomAnchor, constant: 8),
            cover.leadingAnchor.constraint(equalTo: g.leadingAnchor, constant: 28),
            cover.trailingAnchor.constraint(equalTo: g.trailingAnchor, constant: -28),
            cover.heightAnchor.constraint(equalTo: cover.widthAnchor),
            letter.centerXAnchor.constraint(equalTo: cover.centerXAnchor),
            letter.centerYAnchor.constraint(equalTo: cover.centerYAnchor),
            songTitle.topAnchor.constraint(equalTo: cover.bottomAnchor, constant: 26),
            songTitle.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
            songTitle.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
            artist.topAnchor.constraint(equalTo: songTitle.bottomAnchor, constant: 4),
            artist.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
            artist.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
            slider.topAnchor.constraint(equalTo: artist.bottomAnchor, constant: 22),
            slider.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
            slider.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
            now.topAnchor.constraint(equalTo: slider.bottomAnchor, constant: 2),
            now.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
            left.topAnchor.constraint(equalTo: slider.bottomAnchor, constant: 2),
            left.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
            controls.topAnchor.constraint(equalTo: now.bottomAnchor, constant: 18),
            controls.leadingAnchor.constraint(equalTo: cover.leadingAnchor, constant: 40),
            controls.trailingAnchor.constraint(equalTo: cover.trailingAnchor, constant: -40),
            words.topAnchor.constraint(equalTo: controls.bottomAnchor, constant: 22),
            words.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
            words.trailingAnchor.constraint(equalTo: cover.trailingAnchor),
            format.topAnchor.constraint(equalTo: words.bottomAnchor, constant: 10),
            format.leadingAnchor.constraint(equalTo: cover.leadingAnchor),
        ])
        NotificationCenter.default.addObserver(self, selector: #selector(changed), name: Player.changed, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(stopClock), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(startClock), name: UIApplication.willEnterForegroundNotification, object: nil)
        changed()
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        if view.window == nil { cover.image = nil; shownCover = nil }
    }

    private static func dot(_ d: CGFloat) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: d, height: d)).image { c in
            UIColor.white.setFill()
            c.cgContext.fillEllipse(in: CGRect(x: 0, y: 0, width: d, height: d))
        }
    }

    // the clock runs only while the player is on screen, twice a second
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); startClock() }
    override func viewDidDisappear(_ animated: Bool) { super.viewDidDisappear(animated); stopClock() }

    @objc private func startClock() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.tick() }
        timer?.tolerance = 0.2
        tick()
    }

    @objc private func stopClock() { timer?.invalidate(); timer = nil }

    private func tick() {
        guard !dragging else { return }
        let p = Player.shared
        let d = max(p.duration, 0.1)
        slider.value = Float(p.position / d)
        now.text = clock(p.position)
        left.text = "-" + clock(max(d - p.position, 0))
    }

    @objc private func changed() {
        let p = Player.shared
        guard let t = p.current else { dismiss(animated: true); return }
        songTitle.text = t.title
        artist.text = t.artist.isEmpty ? t.album : t.artist
        play.setImage(UIImage(systemName: p.isPlaying ? "pause.fill" : "play.fill"), for: .normal)
        play.accessibilityLabel = p.isPlaying ? "Pause" : "Play"
        shuffleWord.setTitleColor(p.shuffle ? accent : mute, for: .normal)
        repeatWord.setTitle(p.repeatMode == .one ? "Repeat one" : "Repeat", for: .normal)
        repeatWord.setTitleColor(p.repeatMode == .off ? mute : accent, for: .normal)
        format.text = Library.shared.url(t).pathExtension.uppercased()
        if shownCover != t.path {
            shownCover = t.path
            letter.text = String(t.title.prefix(1)).uppercased()
            cover.image = nil
            letter.isHidden = false
            let url = Library.shared.url(t)
            let px = max(view.bounds.width - 56, 100) * UIScreen.main.scale
            DispatchQueue.global(qos: .userInitiated).async {
                let img = Library.cover(url, px: px)
                DispatchQueue.main.async {
                    guard self.shownCover == t.path else { return }
                    self.cover.image = img
                    self.letter.isHidden = img != nil
                }
            }
        }
        tick()
    }

    @objc private func scrubbing() {
        dragging = true
        now.text = clock(Double(slider.value) * Player.shared.duration)
    }

    @objc private func scrubbed() {
        Player.shared.seek(to: Double(slider.value) * Player.shared.duration)
        dragging = false
        tick()
    }

    @objc private func goClose() { dismiss(animated: true) }
    @objc private func goPrev() { Player.shared.previous() }
    @objc private func goPlay() { Player.shared.toggle() }
    @objc private func goNext() { Player.shared.next() }
    @objc private func goShuffle() { Player.shared.setShuffle(!Player.shared.shuffle) }
    @objc private func goRepeat() { Player.shared.cycleRepeat() }
}
