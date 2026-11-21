// Text screens: READMEs and comments drawn from markdown as native text (no web view, no
// images), code in a fixed-width font, changes as a coloured diff. Links inside GitHub open here.

import UIKit

enum Style {
    static let text = UIColor(white: 0.9, alpha: 1)
    static let dim = UIColor(white: 0.55, alpha: 1)
    static let link = UIColor(red: 0.42, green: 0.68, blue: 1, alpha: 1)
    static let add = UIColor(red: 0.45, green: 0.85, blue: 0.5, alpha: 1)
    static let del = UIColor(red: 1, green: 0.47, blue: 0.45, alpha: 1)
    static let codeBG = UIColor(white: 0.11, alpha: 1)
    static let body = UIFont.systemFont(ofSize: 15)
    static let mono = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)

    static func bold(_ size: CGFloat) -> UIFont { .systemFont(ofSize: size, weight: .semibold) }

    static func para(indent: CGFloat = 0, first: CGFloat? = nil, after: CGFloat = 8) -> NSParagraphStyle {
        let p = NSMutableParagraphStyle()
        p.headIndent = indent
        p.firstLineHeadIndent = first ?? indent
        p.paragraphSpacing = after
        p.lineSpacing = 2
        return p
    }
}

/// Builds attributed text a block at a time.
final class Doc {
    let out = NSMutableAttributedString()

    func add(_ s: NSAttributedString, _ p: NSParagraphStyle = Style.para()) {
        let m = NSMutableAttributedString(attributedString: s)
        m.append(NSAttributedString(string: "\n"))
        m.addAttribute(.paragraphStyle, value: p, range: NSRange(location: 0, length: m.length))
        out.append(m)
    }

    func text(_ s: String, _ f: UIFont = Style.body, _ c: UIColor = Style.text, _ p: NSParagraphStyle = Style.para()) {
        add(NSAttributedString(string: s, attributes: [.font: f, .foregroundColor: c]), p)
    }

    func gap(_ h: CGFloat = 10) {
        add(NSAttributedString(string: " ", attributes: [.font: UIFont.systemFont(ofSize: 2)]), Style.para(after: h))
    }

    var result: NSAttributedString {
        while out.string.hasSuffix("\n") { out.deleteCharacters(in: NSRange(location: out.length - 1, length: 1)) }
        return out
    }
}

// MARK: - markdown

enum Markdown {
    @discardableResult
    static func render(_ src: String, into d: Doc = Doc()) -> Doc {
        var para: [String] = []
        func flush() {
            if !para.isEmpty { d.add(inline(para.joined(separator: " "), Style.body)); para = [] }
        }
        var code: [String]?
        var fence = ""
        for raw in clean(src).components(separatedBy: "\n") {
            let t = raw.trimmingCharacters(in: .whitespaces)
            if code != nil {
                if t.hasPrefix(fence) {
                    d.add(NSAttributedString(string: code!.joined(separator: "\n"), attributes: [.font: Style.mono, .foregroundColor: Style.text, .backgroundColor: Style.codeBG]), Style.para(indent: 0, after: 10))
                    code = nil
                } else {
                    code!.append(raw)
                }
                continue
            }
            if t.hasPrefix("```") || t.hasPrefix("~~~") { flush(); code = []; fence = String(t.prefix(3)); continue }
            if t.isEmpty { flush(); continue }
            if t.hasPrefix("#") {
                let level = t.prefix { $0 == "#" }.count
                if level <= 6, t.dropFirst(level).first == " " {
                    flush()
                    let sizes: [CGFloat] = [23, 20, 18, 16, 15, 15]
                    let h = t.dropFirst(level).trimmingCharacters(in: CharacterSet(charactersIn: " #"))
                    d.gap(4)
                    d.add(inline(h, Style.bold(sizes[level - 1]), Style.text, bold: true), Style.para(after: 6))
                    continue
                }
            }
            if t.count >= 3, Set(t.replacingOccurrences(of: " ", with: "")).count == 1, "-*_".contains(t.first!) {
                flush()
                d.text(String(repeating: "─", count: 24), Style.body, Style.dim)
                continue
            }
            if let (mark, rest) = listItem(t) {
                flush()
                let depth = CGFloat(min((raw.prefix { $0 == " " }.count) / 2, 4))
                let indent = 18 + depth * 16
                let s = NSMutableAttributedString(string: mark + "\t", attributes: [.font: Style.body, .foregroundColor: Style.dim])
                s.append(inline(rest, Style.body))
                let p = NSMutableParagraphStyle()
                p.firstLineHeadIndent = indent - 18
                p.headIndent = indent
                p.tabStops = [NSTextTab(textAlignment: .left, location: indent)]
                p.paragraphSpacing = 4
                p.lineSpacing = 2
                d.add(s, p)
                continue
            }
            if t.hasPrefix(">") {
                flush()
                let q = t.drop { $0 == ">" || $0 == " " }
                d.add(inline(String(q), Style.body, Style.dim), Style.para(indent: 14, after: 6))
                continue
            }
            if t.hasPrefix("|") {
                flush()
                let cells = t.trimmingCharacters(in: CharacterSet(charactersIn: "|")).components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
                if cells.allSatisfy({ $0.allSatisfy { "-:".contains($0) } }) { continue }
                d.add(inline(cells.joined(separator: "   ·   "), Style.body), Style.para(after: 4))
                continue
            }
            para.append(t)
        }
        if let c = code { d.add(NSAttributedString(string: c.joined(separator: "\n"), attributes: [.font: Style.mono, .foregroundColor: Style.text, .backgroundColor: Style.codeBG])) }
        flush()
        return d
    }

    private static func listItem(_ t: String) -> (String, String)? {
        for b in ["- ", "* ", "+ "] where t.hasPrefix(b) {
            var rest = String(t.dropFirst(2))
            var mark = "•"
            if rest.hasPrefix("[ ] ") { mark = "○"; rest = String(rest.dropFirst(4)) }
            else if rest.lowercased().hasPrefix("[x] ") { mark = "●"; rest = String(rest.dropFirst(4)) }
            return (mark, rest)
        }
        let digits = t.prefix { $0.isNumber }
        if !digits.isEmpty, digits.count < 4 {
            let after = t.dropFirst(digits.count)
            if after.hasPrefix(". ") || after.hasPrefix(") ") { return (String(digits) + ".", String(after.dropFirst(2))) }
        }
        return nil
    }

    /// Bold, italic, code, links and strikethrough inside a line, through iOS's own parser.
    static func inline(_ s: String, _ font: UIFont, _ color: UIColor = Style.text, bold: Bool = false) -> NSAttributedString {
        let opts = AttributedString.MarkdownParsingOptions(allowsExtendedAttributes: false, interpretedSyntax: .inlineOnlyPreservingWhitespace, failurePolicy: .returnPartiallyParsedIfPossible)
        guard let a = try? AttributedString(markdown: s, options: opts) else {
            return NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color])
        }
        let out = NSMutableAttributedString()
        for run in a.runs {
            var f = font
            var attrs: [NSAttributedString.Key: Any] = [.foregroundColor: color]
            if let ip = run.inlinePresentationIntent {
                if ip.contains(.code) {
                    f = .monospacedSystemFont(ofSize: font.pointSize - 2, weight: bold ? .semibold : .regular)
                    attrs[.backgroundColor] = Style.codeBG
                }
                var traits: UIFontDescriptor.SymbolicTraits = []
                if ip.contains(.stronglyEmphasized) { traits.insert(.traitBold) }
                if ip.contains(.emphasized) { traits.insert(.traitItalic) }
                if !traits.isEmpty, let dsc = f.fontDescriptor.withSymbolicTraits(f.fontDescriptor.symbolicTraits.union(traits)) {
                    f = UIFont(descriptor: dsc, size: f.pointSize)
                }
                if ip.contains(.strikethrough) { attrs[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
            }
            if let l = run.link { attrs[.link] = l }
            attrs[.font] = f
            out.append(NSAttributedString(string: String(a[run.range].characters), attributes: attrs))
        }
        return out
    }

    /// READMEs mix in HTML and badge pictures; keep the words and the links, drop the rest.
    /// Code blocks are left exactly as written.
    static func clean(_ src: String) -> String {
        var out: [String] = []
        var chunk: [String] = []
        var inCode = false
        func cleanChunk() {
            guard !chunk.isEmpty else { return }
            var s = chunk.joined(separator: "\n")
            for (pattern, with) in rules {
                s = s.replacingOccurrences(of: pattern, with: with, options: [.regularExpression, .caseInsensitive])
            }
            for (e, c) in entities { s = s.replacingOccurrences(of: e, with: c) }
            out.append(s)
            chunk = []
        }
        for line in src.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n") {
            let t = line.trimmingCharacters(in: .whitespaces)
            if t.hasPrefix("```") || t.hasPrefix("~~~") {
                if !inCode { cleanChunk() }
                inCode.toggle()
                out.append(line)
            } else if inCode {
                out.append(line)
            } else {
                chunk.append(line)
            }
        }
        cleanChunk()
        return out.joined(separator: "\n")
    }

    private static let rules: [(String, String)] = [
        ("<!--[\\s\\S]*?-->", ""),
        ("<img[^>]*>", ""),
        ("<br\\s*/?>", "\n"),
        ("<h([1-6])[^>]*>([\\s\\S]*?)</h[1-6]>", "\n### $2\n"),
        ("<a\\s[^>]*href=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</a>", "[$2]($1)"),
        ("</?(p|div|details|summary|picture|source|center|table|tr|td|th|tbody|thead|sup|sub|span|kbd|b|i|em|strong|code|ul|ol|li|dl|dt|dd|video|figure|figcaption)\\b[^>]*>", "\n"),
        ("!\\[[^\\]]*\\]\\([^)]*\\)", ""),
        ("!\\[[^\\]]*\\]\\[[^\\]]*\\]", ""),
        ("\\[\\s*\\]\\([^)]*\\)", ""),
        ("\n{3,}", "\n\n"),
    ]
    private static let entities = [("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'"), ("&copy;", "©"), ("&mdash;", "—")]
}

// MARK: - code and diffs

enum Code {
    static let limit = 400_000

    static func render(_ s: String) -> NSAttributedString {
        var s = s.replacingOccurrences(of: "\t", with: "    ")
        var note = ""
        if s.utf8.count > limit {
            s = String(s.utf8.prefix(limit)) ?? String(s.prefix(limit / 2))
            note = "\n\n… the rest of this file is too long to show here."
        }
        let p = NSMutableParagraphStyle()
        p.lineSpacing = 1
        return NSAttributedString(string: s + note, attributes: [.font: Style.mono, .foregroundColor: Style.text, .paragraphStyle: p])
    }

    /// One changed file: its name and counts, then its lines in green and red.
    static func diff(file: String, status: String, adds: Int, dels: Int, patch: String?, into d: Doc) {
        let head = NSMutableAttributedString(string: file, attributes: [.font: Style.bold(14), .foregroundColor: Style.text])
        head.append(NSAttributedString(string: "  +\(adds)", attributes: [.font: Style.bold(13), .foregroundColor: Style.add]))
        head.append(NSAttributedString(string: " −\(dels)", attributes: [.font: Style.bold(13), .foregroundColor: Style.del]))
        if status != "modified", !status.isEmpty {
            head.append(NSAttributedString(string: "  \(status)", attributes: [.font: UIFont.systemFont(ofSize: 13), .foregroundColor: Style.dim]))
        }
        d.add(head, Style.para(after: 6))
        guard let patch = patch, !patch.isEmpty else {
            d.text("No preview of this change (a binary file, or too large).", .systemFont(ofSize: 13), Style.dim, Style.para(after: 18))
            return
        }
        let body = NSMutableAttributedString()
        for line in patch.prefix(Code.limit).components(separatedBy: "\n") {
            let c: UIColor
            switch line.first {
            case "+": c = Style.add
            case "-": c = Style.del
            case "@": c = Style.link
            default: c = Style.text
            }
            body.append(NSAttributedString(string: line.replacingOccurrences(of: "\t", with: "    ") + "\n", attributes: [.foregroundColor: c]))
        }
        body.addAttribute(.font, value: Style.mono, range: NSRange(location: 0, length: body.length))
        d.add(body, Style.para(after: 18))
    }
}

// MARK: - the screen

final class TextVC: UIViewController, UITextViewDelegate {
    typealias Load = (@escaping (Result<NSAttributedString, APIError>) -> Void) -> Void

    private let load: Load
    private let tv = UITextView()
    /// Where relative links point: the repository, its branch, the folder of this file.
    var repo: String?
    var ref = ""
    var dir = ""

    init(_ title: String, repo: String? = nil, ref: String = "", dir: String = "", load: @escaping Load) {
        self.load = load
        self.repo = repo
        self.ref = ref
        self.dir = dir
        super.init(nibName: nil, bundle: nil)
        self.title = title
        navigationItem.backButtonDisplayMode = .minimal
    }

    required init?(coder: NSCoder) { fatalError() }

    override func loadView() {
        tv.backgroundColor = .black
        tv.isEditable = false
        tv.indicatorStyle = .white
        tv.dataDetectorTypes = []
        tv.delegate = self
        tv.textContainerInset = UIEdgeInsets(top: 14, left: 10, bottom: 30, right: 10)
        tv.linkTextAttributes = [.foregroundColor: Style.link]
        tv.layoutManager.allowsNonContiguousLayout = true
        tv.attributedText = NSAttributedString(string: "Loading…", attributes: [.font: Style.body, .foregroundColor: Style.dim])
        view = tv
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        refresh()
    }

    func refresh() {
        load { [weak self] r in
            guard let s = self else { return }
            switch r {
            case .success(let t): s.tv.attributedText = t
            case .failure(let e): s.tv.attributedText = NSAttributedString(string: e.description, attributes: [.font: Style.body, .foregroundColor: Style.dim])
            }
        }
    }

    func textView(_ textView: UITextView, shouldInteractWith url: URL, in range: NSRange, interaction: UITextItemInteraction) -> Bool {
        if interaction == .invokeDefaultAction { Router.open(url, from: self, repo: repo, ref: ref, dir: dir) }
        return false
    }
}

// MARK: - links

enum Router {
    /// GitHub links open as screens here; anything else goes to Safari.
    static func open(_ url: URL, from vc: UIViewController, repo: String? = nil, ref: String = "", dir: String = "") {
        let nav = vc.navigationController
        if url.scheme == nil || url.host == nil {
            // a link inside the repository, like docs/setup.md
            guard let repo = repo else { return }
            var p = url.path
            if p.isEmpty { return }   // "#section"
            p = p.hasPrefix("/") ? String(p.dropFirst()) : (dir.isEmpty ? p : dir + "/" + p)
            var parts: [String] = []
            for c in p.split(separator: "/") {
                if c == ".." { _ = parts.popLast() } else if c != "." { parts.append(String(c)) }
            }
            nav?.pushViewController(Screens.path(repo, parts.joined(separator: "/"), ref: ref), animated: true)
            return
        }
        guard let host = url.host?.lowercased(), host == "github.com" || host == "www.github.com" else {
            UIApplication.shared.open(url)
            return
        }
        let parts = url.path.split(separator: "/").map(String.init)
        var next: UIViewController?
        switch parts.count {
        case 1: next = UserVC(parts[0])
        case 2: next = RepoVC("\(parts[0])/\(parts[1])")
        default:
            let full = "\(parts[0])/\(parts[1])"
            switch parts[2] {
            case "issues", "pull", "pulls":
                if parts.count >= 4, let n = Int(parts[3]) { next = ThreadVC(full, n, pr: parts[2] != "issues") }
            case "commit":
                if parts.count >= 4 { next = Screens.commit(full, parts[3]) }
            case "blob", "tree":
                if parts.count >= 4 { next = Screens.path(full, parts.dropFirst(4).joined(separator: "/"), ref: parts[3], folder: parts[2] == "tree") }
            default: break
            }
        }
        if let n = next { nav?.pushViewController(n, animated: true) } else { UIApplication.shared.open(url) }
    }
}
