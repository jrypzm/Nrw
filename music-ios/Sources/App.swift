// yrs. music for iPhone. Native UIKit, no libraries: the system decodes the music
// (in hardware for MP3 and AAC), and the app does nothing while you aren't looking at it.

import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .black
        w.overrideUserInterfaceStyle = .dark
        w.tintColor = .white
        let root = RootViewController()
        w.rootViewController = root
        w.makeKeyAndVisible()
        window = w
        _ = Player.shared
        Library.shared.scan()
        // used only to take the screenshots shown with each build
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-demo-player") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { root.demoPlay(open: true) }
        } else if args.contains("-demo-play") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { root.demoPlay(open: false) }
        }
        return true
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // songs may have been added in the Files app meanwhile
        Library.shared.scan()
    }

    // Songs opened in yrs. music from another app (Files, Safari, AirDrop) are kept and played.
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        Library.shared.importItems([url]) { first in
            if let p = first, let t = Library.shared.tracks.first(where: { $0.path == p }) {
                Player.shared.play(list: [t], start: 0)
            }
        }
        return true
    }
}
