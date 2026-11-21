// yrs. info: your iPhone's memory, storage, battery and model, in plain words and numbers.
// Native UIKit, no libraries, no timers: the numbers are read when you open the app or pull down,
// and the app does nothing in between.

import Darwin
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.backgroundColor = .black
        w.overrideUserInterfaceStyle = .dark
        w.tintColor = .white
        w.rootViewController = InfoVC()
        w.makeKeyAndVisible()
        window = w
        return true
    }
}

final class InfoVC: UITableViewController {
    private var sections: [(String, [(String, String)])] = []

    init() { super.init(style: .insetGrouped) }
    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.backgroundColor = .black
        tableView.separatorColor = UIColor(white: 0.2, alpha: 1)
        let head = UILabel(frame: CGRect(x: 0, y: 0, width: 0, height: 64))
        head.text = "    info."
        head.font = .systemFont(ofSize: 30, weight: .bold)
        head.textColor = .white
        tableView.tableHeaderView = head
        let r = UIRefreshControl()
        r.tintColor = .gray
        r.addTarget(self, action: #selector(pulled), for: .valueChanged)
        refreshControl = r
        NotificationCenter.default.addObserver(self, selector: #selector(refresh), name: UIApplication.didBecomeActiveNotification, object: nil)
        refresh()
    }

    @objc private func pulled() {
        refresh()
        refreshControl?.endRefreshing()
    }

    @objc func refresh() {
        sections = Info.read()
        tableView.reloadData()
    }

    override func numberOfSections(in tableView: UITableView) -> Int { sections.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection s: Int) -> Int { sections[s].1.count }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection s: Int) -> String? { sections[s].0 }

    override func tableView(_ tableView: UITableView, titleForFooterInSection s: Int) -> String? {
        s == sections.count - 1 ? "Pull down to read the numbers again." : nil
    }

    override func tableView(_ tableView: UITableView, cellForRowAt ip: IndexPath) -> UITableViewCell {
        let c = tableView.dequeueReusableCell(withIdentifier: "c") ?? UITableViewCell(style: .value1, reuseIdentifier: "c")
        let (name, value) = sections[ip.section].1[ip.row]
        c.backgroundColor = UIColor(white: 0.09, alpha: 1)
        c.selectionStyle = .none
        c.textLabel?.text = name
        c.textLabel?.textColor = UIColor(white: 0.6, alpha: 1)
        c.textLabel?.font = .systemFont(ofSize: 17)
        c.detailTextLabel?.text = value
        c.detailTextLabel?.textColor = .white
        c.detailTextLabel?.font = .systemFont(ofSize: 20, weight: .semibold)
        return c
    }
}

enum Info {
    static func read() -> [(String, [(String, String)])] {
        [("Memory", memory()), ("Storage", storage()), ("Battery", battery()), ("Phone", phone())]
    }

    // MARK: memory

    static func memory() -> [(String, String)] {
        let total = ProcessInfo.processInfo.physicalMemory
        var rows: [(String, String)] = [("Total", size(total, round: true))]
        var stats = vm_statistics64()
        var n = mach_msg_type_number_t(MemoryLayout<vm_statistics64>.size / MemoryLayout<integer_t>.size)
        let ok = withUnsafeMutablePointer(to: &stats) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(n)) { host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &n) }
        }
        if ok == KERN_SUCCESS {
            let page = UInt64(vm_kernel_page_size)
            let free = UInt64(stats.free_count) * page
            let cached = UInt64(stats.inactive_count + stats.purgeable_count) * page
            let used = total > free + cached ? total - free - cached : 0
            rows += [("In use", size(used)), ("Free", size(free)), ("Kept for apps, freed when needed", size(cached))]
        }
        rows.append(("This app uses", size(footprint())))
        return rows
    }

    static func footprint() -> UInt64 {
        var info = task_vm_info_data_t()
        var n = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let ok = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(n)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &n) }
        }
        return ok == KERN_SUCCESS ? UInt64(info.phys_footprint) : 0
    }

    // MARK: storage

    static func storage() -> [(String, String)] {
        let home = URL(fileURLWithPath: NSHomeDirectory())
        guard let v = try? home.resourceValues(forKeys: [.volumeTotalCapacityKey, .volumeAvailableCapacityForImportantUsageKey]),
              let total = v.volumeTotalCapacity, let free = v.volumeAvailableCapacityForImportantUsage else { return [("Storage", "unknown")] }
        let t = UInt64(total), f = UInt64(max(free, 0))
        return [("Total", size(t, round: true)), ("Used", size(t > f ? t - f : 0)), ("Free", size(f))]
    }

    // MARK: battery

    static func battery() -> [(String, String)] {
        let d = UIDevice.current
        d.isBatteryMonitoringEnabled = true
        let level = d.batteryLevel
        let state = d.batteryState
        d.isBatteryMonitoringEnabled = false
        var rows: [(String, String)] = []
        rows.append(("Charge", level < 0 ? "unknown" : "\(Int((level * 100).rounded())) out of 100"))
        switch state {
        case .charging: rows.append(("Power", "charging"))
        case .full: rows.append(("Power", "full, plugged in"))
        case .unplugged: rows.append(("Power", "on battery"))
        default: break
        }
        rows.append(("Low Power Mode", ProcessInfo.processInfo.isLowPowerModeEnabled ? "on" : "off"))
        let heat: String
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: heat = "normal"
        case .fair: heat = "a little warm"
        case .serious: heat = "hot"
        case .critical: heat = "too hot"
        @unknown default: heat = "unknown"
        }
        rows.append(("Temperature", heat))
        return rows
    }

    // MARK: the phone

    static func phone() -> [(String, String)] {
        let p = ProcessInfo.processInfo
        let s = UIScreen.main.nativeBounds.size
        return [
            ("Model", model()),
            ("iOS", UIDevice.current.systemVersion),
            ("Processor", "\(p.processorCount) cores"),
            ("Screen", "\(Int(s.width)) by \(Int(s.height))"),
            ("On for", uptime(p.systemUptime)),
        ]
    }

    static func model() -> String {
        var id = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] ?? ""
        if id.isEmpty {
            var u = utsname()
            uname(&u)
            id = withUnsafeBytes(of: &u.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
        }
        let names = [
            "iPhone8,1": "iPhone 6s", "iPhone8,2": "iPhone 6s Plus", "iPhone8,4": "iPhone SE",
            "iPhone9,1": "iPhone 7", "iPhone9,3": "iPhone 7", "iPhone9,2": "iPhone 7 Plus", "iPhone9,4": "iPhone 7 Plus",
            "iPhone10,1": "iPhone 8", "iPhone10,4": "iPhone 8", "iPhone10,2": "iPhone 8 Plus", "iPhone10,5": "iPhone 8 Plus",
            "iPhone10,3": "iPhone X", "iPhone10,6": "iPhone X", "iPhone12,8": "iPhone SE (2nd)",
        ]
        return names[id] ?? id
    }

    static func uptime(_ secs: TimeInterval) -> String {
        let m = Int(secs) / 60, h = m / 60, d = h / 24
        if d > 0 { return "\(d) day\(d == 1 ? "" : "s") \(h % 24) hour\(h % 24 == 1 ? "" : "s")" }
        if h > 0 { return "\(h) hour\(h == 1 ? "" : "s") \(m % 60) min" }
        return "\(m) min"
    }

    /// "1.4 GB" or "380 MB"
    static func size(_ bytes: UInt64, round: Bool = false) -> String {
        let mb = Double(bytes) / 1_048_576
        if mb >= 1000 {
            let gb = mb / 1024
            return round ? "\(Int(gb.rounded())) GB" : String(format: "%.1f GB", gb)
        }
        return "\(Int(mb.rounded())) MB"
    }
}
