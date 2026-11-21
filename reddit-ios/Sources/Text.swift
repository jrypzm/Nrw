// Posts and comments drawn from markdown as native text: bold, italics, links, lists, quotes, code.

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
    /// Moves everything added to the right (replies to replies).
    var indent: CGFloat = 0

    func add(_ s: NSAttributedString, _ p: NSParagraphStyle = Style.para()) {
        var p = p
        if indent > 0, let q = p.mutableCopy() as? NSMutableParagraphStyle {
            q.headIndent += indent
            q.firstLineHeadIndent += indent
            q.tabStops = q.tabStops.map { NSTextTab(textAlignment: $0.alignment, location: $0.location + indent) }
            p = q
        }
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
                    d.add(NSAttributedString(string: code!.joined(separator: "\u{2028}"), attributes: [.font: Style.mono, .foregroundColor: Style.text, .backgroundColor: Style.codeBG]), Style.para(indent: 0, after: 10))
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
                let cells = t.trimmingCharacters(in: CharacterSet(charactersIn: "|")).components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                if cells.isEmpty { continue }
                if cells.allSatisfy({ $0.allSatisfy { "-:".contains($0) } }) { continue }
                d.add(inline(cells.joined(separator: "   ·   "), Style.body), Style.para(after: 4))
                continue
            }
            para.append(t)
        }
        if let c = code { d.add(NSAttributedString(string: c.joined(separator: "\u{2028}"), attributes: [.font: Style.mono, .foregroundColor: Style.text, .backgroundColor: Style.codeBG])) }
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
        (">!([\\s\\S]*?)!<", "$1"),
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

