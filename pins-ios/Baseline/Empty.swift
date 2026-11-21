// The smallest possible iPhone app with a screen: one black window, nothing else.
// Built only to measure the memory iOS itself gives every such app, to compare yrs. music with.
import UIKit

@main
final class EmptyApp: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .black
        let vc = UIViewController()
        vc.view.backgroundColor = .black
        w.rootViewController = vc
        w.makeKeyAndVisible()
        window = w
        return true
    }
}
