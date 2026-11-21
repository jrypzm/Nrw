// yrs. pins: Pinterest for iPhone, light. Native UIKit, no libraries, no ads, no tracking.
// Signed in, the main screen is your own home feed; signed out, it's search. Pictures are fetched
// at the size they're shown and let go of when you move on.

import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let nav = UINavigationController()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let bar = UINavigationBarAppearance()
        bar.configureWithOpaqueBackground()
        bar.backgroundColor = .black
        bar.shadowColor = UIColor(white: 0.17, alpha: 1)
        bar.titleTextAttributes = [.foregroundColor: UIColor.white, .font: UIFont.systemFont(ofSize: 17, weight: .semibold)]
        UINavigationBar.appearance().standardAppearance = bar
        UINavigationBar.appearance().scrollEdgeAppearance = bar

        nav.setViewControllers([Screen.root()], animated: false)
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .black
        w.overrideUserInterfaceStyle = .dark
        w.tintColor = .white
        w.rootViewController = nav
        w.makeKeyAndVisible()
        window = w
        // signing in or out swaps the main screen between the home feed and the welcome screen
        NotificationCenter.default.addObserver(self, selector: #selector(accountChanged), name: Account.changed, object: nil)
        // the sign-in cookies may already be there from last time
        Account.refresh()
        demo(nav)
        return true
    }

    @objc private func accountChanged() {
        nav.setViewControllers([Screen.root()], animated: false)
    }

    /// Only for the screenshots taken with each build: `-demo search:cats` or `-demo pin:cats`.
    private func demo(_ nav: UINavigationController) {
        guard let d = UserDefaults.standard.string(forKey: "demo"), let colon = d.firstIndex(of: ":") else { return }
        let kind = d[..<colon], arg = String(d[d.index(after: colon)...])
        switch kind {
        case "search": nav.pushViewController(Screen.search(arg), animated: false)
        case "pin":
            Pinterest.search(arg, bookmark: nil) { r in
                if case .success(let (pins, _)) = r, let p = pins.first(where: { !$0.title.isEmpty }) ?? pins.first {
                    nav.pushViewController(PinVC(p), animated: false)
                }
            }
        default: break
        }
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        Images.forget()
        malloc_zone_pressure_relief(nil, 0)
    }

    func applicationDidReceiveMemoryWarning(_ application: UIApplication) { Images.forget() }
}
