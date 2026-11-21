// yrs. reddit: opens Reddit in the iPhone's own Safari engine, so it shares your real Safari's
// login and "real browser" clearance — the only way that gets past Reddit's block on other apps.
// A clean app icon that goes straight to old.reddit.com (light, fast, your account, your settings).

import SafariServices
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .black
        w.overrideUserInterfaceStyle = .dark
        w.rootViewController = RedditHost()
        w.makeKeyAndVisible()
        window = w
        return true
    }
}

/// Keeps Reddit on screen in a real-Safari view, and reopens it if you close it.
final class RedditHost: UIViewController, SFSafariViewControllerDelegate {
    private var showing = false
    // old.reddit.com works on iOS 15 and is lighter; change the start page here if you like.
    private let start = URL(string: "https://old.reddit.com/")!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if presentedViewController == nil { open() }
    }

    private func open() {
        let cfg = SFSafariViewController.Configuration()
        cfg.barCollapsingEnabled = true
        cfg.entersReaderIfAvailable = false
        let s = SFSafariViewController(url: start, configuration: cfg)
        s.delegate = self
        s.modalPresentationStyle = .fullScreen
        s.preferredBarTintColor = .black
        s.preferredControlTintColor = .white
        s.dismissButtonStyle = .close
        showing = true
        present(s, animated: false)
    }

    // "Close/Done" returns here; bring Reddit back so the app always lands on Reddit.
    func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        showing = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            guard let s = self, !s.showing, s.view.window != nil else { return }
            s.open()
        }
    }
}
