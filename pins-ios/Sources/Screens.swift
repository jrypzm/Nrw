// The screens: home (a search bar), one pin, and pictures full screen.

import AVKit
import UIKit

// MARK: - home

/// Just a search bar.
final class HomeVC: UIViewController, UISearchBarDelegate {
    private let bar = UISearchBar()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "pins"
        navigationItem.backButtonDisplayMode = .minimal
        view.backgroundColor = .black
        bar.placeholder = "Search Pinterest"
        bar.searchBarStyle = .minimal
        bar.barStyle = .black
        bar.autocorrectionType = .no
        bar.delegate = self
        view.addSubview(bar)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard)))
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let top = view.safeAreaInsets.top
        bar.frame = CGRect(x: 8, y: top + 8, width: view.bounds.width - 16, height: 56)
    }

    @objc private func dismissKeyboard() { bar.resignFirstResponder() }

    func searchBarSearchButtonClicked(_ b: UISearchBar) {
        let q = (b.text ?? "").trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        b.resignFirstResponder()
        navigationController?.pushViewController(HomeVC.search(q), animated: true)
    }

    static func search(_ q: String) -> GridVC {
        GridVC(q) { bm, done in Pinterest.search(q, bookmark: bm, done: done) }
    }
}

// MARK: - one pin

/// A pin: its picture and details sit in the header, and similar pins fill the grid below.
final class PinVC: GridVC {
    private let pin: Pin
    private let head: PinHeader

    init(_ p: Pin) {
        pin = p
        head = PinHeader(p)
        // similar pins come from a search of the pin's words
        let query = !p.title.isEmpty ? p.title : (!p.board.isEmpty ? p.board : p.about)
        super.init(p.user.isEmpty ? "Pin" : p.user) { bm, done in
            if query.trimmingCharacters(in: .whitespaces).isEmpty { done(.success(([], nil))); return }
            Pinterest.search(query, bookmark: bm) { r in
                // drop the pin itself if it comes back
                done(r.map { (pins, next) in (pins.filter { $0.id != p.id }, next) })
            }
        }
        headerView = head
        head.onPicture = { [weak self] in self?.openPicture() }
        head.onUser = { [weak self] in self?.openUser() }
        head.onLink = { [weak self] in if let l = p.link { UIApplication.shared.open(l) } }
        head.onPinterest = { [weak self] in if let u = p.page { UIApplication.shared.open(u) } }
        head.onSave = { [weak self] in self?.save() }
    }

    required init?(coder: NSCoder) { fatalError() }

    private func openPicture() {
        if let v = pin.video { play(v); return }
        if let (u, _) = pin.image("orig") ?? pin.image("736x") { present(ViewerVC([u]), animated: true) }
    }

    private func play(_ u: URL) {
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        let p = AVPlayerViewController()
        p.player = AVPlayer(url: u)
        present(p, animated: true) { p.player?.play() }
    }

    private func openUser() {
        let name = pin.user
        guard !name.isEmpty else { return }
        navigationController?.pushViewController(GridVC("@" + name) { bm, done in Pinterest.user(name, bookmark: bm, done: done) }, animated: true)
    }

    /// The full-size picture goes to Photos; it's read, saved and let go of.
    private func save() {
        guard let (u, _) = pin.image("orig") ?? pin.image("736x") else { return }
        URLSession.shared.dataTask(with: u) { d, _, _ in
            let img = d.flatMap { UIImage(data: $0) }
            DispatchQueue.main.async {
                guard let i = img else { self.tell("Couldn't fetch the picture."); return }
                UIImageWriteToSavedPhotosAlbum(i, self, #selector(self.saved(_:error:context:)), nil)
            }
        }.resume()
    }

    @objc private func saved(_ image: UIImage, error: Error?, context: UnsafeMutableRawPointer?) {
        tell(error == nil ? "Saved to Photos." : "Couldn't save: allow yrs. pins to add photos in Settings › Privacy › Photos.")
    }

    private func tell(_ t: String) {
        let a = UIAlertController(title: t, message: nil, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "OK", style: .default))
        present(a, animated: true)
    }
}

/// The picture and details shown above the similar-pins grid on a pin screen.
final class PinHeader: UIView, GridHeader {
    private let pin: Pin
    private let picture = UIImageView()
    private let badge = UILabel()
    private let stack = UIStackView()
    private var shownPictureFor = -1

    var onPicture: (() -> Void)?
    var onUser: (() -> Void)?
    var onLink: (() -> Void)?
    var onSave: (() -> Void)?
    var onPinterest: (() -> Void)?

    init(_ p: Pin) {
        pin = p
        super.init(frame: .zero)
        picture.contentMode = .scaleAspectFit
        picture.backgroundColor = p.color
        picture.clipsToBounds = true
        picture.layer.cornerRadius = 14
        picture.isUserInteractionEnabled = true
        picture.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapPicture)))
        addSubview(picture)
        if p.video != nil {
            badge.text = "▶ play"
            badge.font = .systemFont(ofSize: 15, weight: .semibold)
            badge.textColor = .white
            badge.backgroundColor = UIColor(white: 0, alpha: 0.6)
            badge.textAlignment = .center
            badge.layer.cornerRadius = 8
            badge.clipsToBounds = true
            picture.addSubview(badge)
        }
        stack.axis = .vertical
        stack.spacing = 10
        stack.alignment = .leading
        addSubview(stack)

        if !p.title.isEmpty { stack.addArrangedSubview(label(p.title, .systemFont(ofSize: 20, weight: .semibold), .white)) }
        if !p.about.isEmpty, p.about != p.title { stack.addArrangedSubview(label(p.about, .systemFont(ofSize: 15), Style.text)) }
        if !p.user.isEmpty {
            let who = p.userName.isEmpty ? p.user : "\(p.userName) (@\(p.user))"
            stack.addArrangedSubview(button("By \(who)", #selector(tapUser)))
        }
        if !p.board.isEmpty { stack.addArrangedSubview(label("On the board \"\(p.board)\"", .systemFont(ofSize: 14), Style.dim)) }
        if let l = p.link { stack.addArrangedSubview(button("Open the link (\(l.host?.replacingOccurrences(of: "www.", with: "") ?? "website"))", #selector(tapLink))) }
        stack.addArrangedSubview(button("Save picture to Photos", #selector(tapSave)))
        stack.addArrangedSubview(button("Open on Pinterest", #selector(tapPinterest)))
        stack.addArrangedSubview(label("More like this", .systemFont(ofSize: 17, weight: .semibold), .white))
    }

    required init?(coder: NSCoder) { fatalError() }

    private func label(_ t: String, _ f: UIFont, _ c: UIColor) -> UILabel {
        let l = UILabel(); l.text = t; l.font = f; l.textColor = c; l.numberOfLines = 0; return l
    }

    private func button(_ t: String, _ a: Selector) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(t, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 16)
        b.titleLabel?.numberOfLines = 0
        b.contentHorizontalAlignment = .leading
        b.tintColor = Style.link
        b.addTarget(self, action: a, for: .touchUpInside)
        return b
    }

    private var ratio: CGFloat { min(max(pin.image("736x")?.1 ?? 1, 0.4), 2.0) }
    private func picHeight(_ w: CGFloat) -> CGFloat { ceil((w - 24) * ratio) }
    private func stackHeight(_ w: CGFloat) -> CGFloat {
        stack.systemLayoutSizeFitting(CGSize(width: w - 28, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
    }

    func height(for width: CGFloat) -> CGFloat {
        12 + picHeight(width) + 16 + stackHeight(width) + 14
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let ph = picHeight(w)
        picture.frame = CGRect(x: 12, y: 12, width: w - 24, height: ph)
        badge.frame = CGRect(x: w - 24 - 96, y: ph - 44, width: 84, height: 32)
        stack.frame = CGRect(x: 14, y: 12 + ph + 16, width: w - 28, height: stackHeight(w))
        loadPicture(w)
    }

    private func loadPicture(_ w: CGFloat) {
        let px = (w - 24) * UIScreen.main.scale
        let key = Int(px)
        guard key != shownPictureFor, let (u, _) = pin.image(px > 500 ? "736x" : "474x") else { return }
        shownPictureFor = key
        Images.load(u, px: px) { [weak self] img in self?.picture.image = img }
    }

    @objc private func tapPicture() { onPicture?() }
    @objc private func tapUser() { onUser?() }
    @objc private func tapLink() { onLink?() }
    @objc private func tapSave() { onSave?() }
    @objc private func tapPinterest() { onPinterest?() }
}

// MARK: - pictures, full screen

/// Swipe between pictures, pinch to zoom. Only the picture on screen and its neighbours are kept.
final class ViewerVC: UIViewController, UIScrollViewDelegate {
    private let urls: [URL]
    private let pager = UIScrollView()
    private var pages: [ZoomPage] = []
    private let counter = UILabel()
    private var current = -1

    init(_ urls: [URL]) {
        self.urls = urls
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { fatalError() }

    override var prefersStatusBarHidden: Bool { true }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        pager.isPagingEnabled = true
        pager.showsHorizontalScrollIndicator = false
        pager.delegate = self
        pager.contentInsetAdjustmentBehavior = .never
        view.addSubview(pager)
        for _ in urls {
            let p = ZoomPage()
            pages.append(p)
            pager.addSubview(p)
        }
        let close = UIButton(type: .system)
        close.setTitle("Close", for: .normal)
        close.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        close.tintColor = .white
        close.frame = CGRect(x: 16, y: 20, width: 70, height: 40)
        close.addTarget(self, action: #selector(done), for: .touchUpInside)
        view.addSubview(close)
        counter.textColor = UIColor(white: 0.7, alpha: 1)
        counter.font = .systemFont(ofSize: 15)
        counter.textAlignment = .right
        view.addSubview(counter)
        let down = UISwipeGestureRecognizer(target: self, action: #selector(done))
        down.direction = .down
        view.addGestureRecognizer(down)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let b = view.bounds
        pager.frame = b
        pager.contentSize = CGSize(width: b.width * CGFloat(urls.count), height: b.height)
        for (i, p) in pages.enumerated() { p.frame = CGRect(x: b.width * CGFloat(i), y: 0, width: b.width, height: b.height) }
        counter.frame = CGRect(x: b.width - 116, y: 20, width: 100, height: 40)
        show(Int(round(pager.contentOffset.x / max(b.width, 1))))
    }

    func scrollViewDidEndDecelerating(_ s: UIScrollView) {
        show(Int(round(s.contentOffset.x / max(s.bounds.width, 1))))
    }

    private func show(_ i: Int) {
        let i = max(0, min(i, urls.count - 1))
        guard i != current else { return }
        current = i
        counter.text = urls.count > 1 ? "\(i + 1) of \(urls.count)" : ""
        let px = max(view.bounds.width, view.bounds.height) * UIScreen.main.scale
        for (k, p) in pages.enumerated() {
            if abs(k - i) <= 1 { p.show(urls[k], px: px) } else { p.clear() }
        }
    }

    @objc private func done() { dismiss(animated: true) }
}

final class ZoomPage: UIScrollView, UIScrollViewDelegate {
    private let iv = UIImageView()
    private var url: URL?

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 4
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        iv.contentMode = .scaleAspectFit
        addSubview(iv)
        let dbl = UITapGestureRecognizer(target: self, action: #selector(toggleZoom))
        dbl.numberOfTapsRequired = 2
        addGestureRecognizer(dbl)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        if zoomScale == 1 { iv.frame = bounds }
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { iv }

    @objc private func toggleZoom() { setZoomScale(zoomScale > 1 ? 1 : 2.5, animated: true) }

    func show(_ u: URL, px: CGFloat) {
        guard url != u else { return }
        url = u
        Images.load(u, px: px) { [weak self] img in
            guard let s = self, s.url == u else { return }
            s.iv.image = img
        }
    }

    func clear() {
        url = nil
        iv.image = nil
        zoomScale = 1
    }
}

