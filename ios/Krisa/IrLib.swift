import SwiftUI

/**
 Вкладка «Библиотека»: тысячи ИК-пультов (Flipper-IRDB) с сайта.
 index.json = {"cats":{"TVs":["Samsung",…],…}}; <Категория>/<Бренд>.json = [{"m":модель,"b":[кнопки]}].
 Кнопка: parsed {"n","p","a","c"} — развернуть в тайминги, или raw {"n","r":[…],"f"} — слать как есть.
 */
struct LibIndex: Codable {
    let cats: [String: [String]]
}

struct LibButton: Codable {
    let n: String
    let p: String?
    let a: String?
    let c: String?
    let r: [Int]?
    let f: Int?
}

struct LibModel: Codable {
    let m: String
    let b: [LibButton]
}

enum IrLib {
    static let base: String = "https://mikhailkulinskii1910-jpg.github.io/vsluh/irbis/lib/"

    /// Младшие байты из hex-строки, младший байт первым: "0700" → [0x07, 0x00].
    static func bytes(_ hex: String?) -> [Int] {
        guard let hex = hex else { return [] }
        let clean: String = hex.hasPrefix("0x") || hex.hasPrefix("0X") ? String(hex.dropFirst(2)) : hex
        let chars: [Character] = Array(clean)
        var out: [Int] = []
        var i: Int = 0
        while i + 1 < chars.count {
            if let v = Int(String(chars[i...i + 1]), radix: 16) { out.append(v) }
            i += 2
        }
        if chars.count % 2 == 1, let v = Int(String(chars[chars.count - 1]), radix: 16) { out.append(v) }
        return out
    }

    private static func byte(_ arr: [Int], _ i: Int) -> Int { i < arr.count ? arr[i] & 0xFF : 0 }

    /// Биты значения, младшим вперёд, добавляются как пары mark/space (NEC-подобные).
    private static func necBits(_ value: Int, into out: inout [Int]) {
        for i in 0..<8 {
            let bit: Int = (value >> i) & 1
            out.append(560)
            out.append(bit == 1 ? 1690 : 560)
        }
    }

    /// Паттерн (мкс) и частота для parsed-кнопки; nil — неизвестный протокол.
    static func pattern(_ b: LibButton) -> (us: [Int], freq: Int)? {
        if let r = b.r, !r.isEmpty { return (r, b.f ?? 38000) }
        guard let proto = b.p?.uppercased() else { return nil }
        let a: [Int] = bytes(b.a)
        let c: [Int] = bytes(b.c)
        let a0: Int = byte(a, 0)
        let a1: Int = byte(a, 1)
        let c0: Int = byte(c, 0)
        switch proto {
        case "NEC":
            return (nec(header: [9000, 4500], bytes: [a0, a0 ^ 0xFF, c0, c0 ^ 0xFF]), 38000)
        case "NECEXT", "NEC_EXT", "NECEXTENDED":
            return (nec(header: [9000, 4500], bytes: [a0, a1, c0, c0 ^ 0xFF]), 38000)
        case "SAMSUNG32", "SAMSUNG":
            return (nec(header: [4500, 4500], bytes: [a0, a0, c0, c0 ^ 0xFF]), 38000)
        case "SIRC", "SIRC12":
            return (sirc(cmd: c0, addr: a0, addrBits: 5, ext: nil), 40000)
        case "SIRC15":
            return (sirc(cmd: c0, addr: a0, addrBits: 8, ext: nil), 40000)
        case "SIRC20":
            return (sirc(cmd: c0, addr: a0, addrBits: 5, ext: a1), 40000)
        default:
            return nil
        }
    }

    private static func nec(header: [Int], bytes: [Int]) -> [Int] {
        var out: [Int] = header
        for v in bytes { necBits(v, into: &out) }
        out.append(560)                                  // хвост
        return out
    }

    private static func sirc(cmd: Int, addr: Int, addrBits: Int, ext: Int?) -> [Int] {
        var out: [Int] = [2400, 600]
        func put(_ value: Int, _ n: Int) {
            for i in 0..<n {
                let bit: Int = (value >> i) & 1
                out.append(bit == 1 ? 1200 : 600)
                out.append(600)
            }
        }
        put(cmd, 7)
        put(addr, addrBits)
        if let e = ext { put(e, 8) }
        return out
    }
}

/// Загрузчик библиотеки: индекс и файлы брендов (кэш в памяти).
final class LibLoader: ObservableObject {
    @Published var index: [String: [String]] = [:]
    @Published var cats: [String] = []
    @Published var models: [LibModel] = []
    @Published var status: String = ""
    @Published var loadingIndex = false
    @Published var loadingBrand = false

    private var brandCache: [String: [LibModel]] = [:]

    func loadIndex() {
        if !index.isEmpty || loadingIndex { return }
        guard let url = URL(string: IrLib.base + "index.json") else { return }
        loadingIndex = true
        status = "загрузка списка…"
        URLSession.shared.dataTask(with: url) { [weak self] data, _, err in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.loadingIndex = false
                if let data = data, let idx = try? JSONDecoder().decode(LibIndex.self, from: data) {
                    self.index = idx.cats
                    self.cats = idx.cats.keys.sorted()
                    self.status = ""
                } else {
                    self.status = "не удалось загрузить список — нужен интернет"
                }
            }
        }.resume()
    }

    func brands(_ cat: String) -> [String] { (index[cat] ?? []).sorted() }

    func loadBrand(_ cat: String, _ brand: String) {
        let key: String = cat + "/" + brand
        if let cached = brandCache[key] {
            models = cached
            status = ""
            return
        }
        let allowed = CharacterSet.urlPathAllowed
        let catPart: String = cat.addingPercentEncoding(withAllowedCharacters: allowed) ?? cat
        let brandPart: String = brand.addingPercentEncoding(withAllowedCharacters: allowed) ?? brand
        guard let url = URL(string: IrLib.base + catPart + "/" + brandPart + ".json") else { return }
        models = []
        loadingBrand = true
        status = "загрузка «" + brand + "»…"
        URLSession.shared.dataTask(with: url) { [weak self] data, _, err in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.loadingBrand = false
                if let data = data, let list = try? JSONDecoder().decode([LibModel].self, from: data) {
                    self.brandCache[key] = list
                    self.models = list
                    self.status = ""
                } else {
                    self.status = "не удалось загрузить «" + brand + "»"
                }
            }
        }.resume()
    }
}

/// Вкладка «Библиотека»: категория → бренд → модель → кнопки.
struct LibraryPanel: View {
    @ObservedObject var model: RemoteModel
    @StateObject private var lib = LibLoader()
    @State private var cat: String?
    @State private var brand: String?
    @State private var modelIdx: Int?
    @State private var search = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            if !lib.status.isEmpty {
                Text(lib.status).font(Term.ru(12)).foregroundColor(Term.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            content
        }
        .onAppear { lib.loadIndex() }
    }

    private func label(_ s: String) -> String {
        if !(Term.prism || Term.blueprint) { return s }
        var t: String = s
        if t.hasPrefix("[") { t.removeFirst() }
        if t.hasSuffix("]") { t.removeLast() }
        return t.trimmingCharacters(in: .whitespaces)
    }

    private func rowButton(_ title: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            TabLabel(title: title, on: false, height: 44, ruSize: 14, leading: true, forceRu: true)
        }
    }

    // MARK: шапка и путь

    private var header: some View {
        HStack(spacing: 8) {
            if cat != nil {
                Button { back() } label: {
                    TabLabel(title: label("[ ← ]"), on: false, height: 36, ruSize: 14, forceRu: true)
                }
                .frame(width: 60)
            }
            Text(pathText).font(Term.ru(14)).foregroundColor(Term.fg)
                .lineLimit(1).minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var pathText: String {
        var parts: [String] = ["Библиотека"]
        if let c = cat { parts.append(c) }
        if let b = brand { parts.append(b) }
        if let i = modelIdx, i < lib.models.count { parts.append(lib.models[i].m) }
        return parts.joined(separator: " · ")
    }

    private func back() {
        if modelIdx != nil { modelIdx = nil }
        else if brand != nil { brand = nil; lib.models = []; search = "" }
        else if cat != nil { cat = nil; search = "" }
    }

    // MARK: уровни

    @ViewBuilder private var content: some View {
        if cat == nil {
            catList
        } else if brand == nil {
            brandList
        } else if modelIdx == nil {
            modelList
        } else {
            buttonGrid
        }
    }

    private var catList: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 6) {
                ForEach(lib.cats, id: \.self) { c in
                    rowButton(c) { cat = c }
                }
            }
            .padding(.bottom, 16)
        }
    }

    private var searchField: some View {
        TextField("поиск", text: $search)
            .font(Term.ru(15)).foregroundColor(Term.fg)
            .disableAutocorrection(true).autocapitalization(.none)
            .padding(8)
            .overlay(Rectangle().strokeBorder(Term.line, lineWidth: 1))
    }

    private func matches(_ s: String) -> Bool {
        if search.isEmpty { return true }
        return s.lowercased().contains(search.lowercased())
    }

    private var brandList: some View {
        let list: [String] = (cat.map { lib.brands($0) } ?? []).filter { b in matches(b) }
        return VStack(spacing: 6) {
            searchField
            ScrollView(showsIndicators: false) {
                VStack(spacing: 6) {
                    ForEach(list, id: \.self) { b in
                        rowButton(b) {
                            brand = b
                            if let c = cat { lib.loadBrand(c, b) }
                        }
                    }
                }
                .padding(.bottom, 16)
            }
        }
    }

    private var modelList: some View {
        let idxs: [Int] = lib.models.indices.filter { i in matches(lib.models[i].m) }
        return VStack(spacing: 6) {
            searchField
            ScrollView(showsIndicators: false) {
                VStack(spacing: 6) {
                    ForEach(idxs, id: \.self) { i in
                        rowButton(lib.models[i].m) { modelIdx = i }
                    }
                }
                .padding(.bottom, 16)
            }
        }
    }

    private var buttonGrid: some View {
        let buttons: [LibButton] = (modelIdx.flatMap { i in i < lib.models.count ? lib.models[i].b : nil }) ?? []
        return ScrollView(showsIndicators: false) {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8),
                                GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(0..<buttons.count, id: \.self) { i in keyButton(buttons[i]) }
            }
            .padding(.bottom, 16)
        }
    }

    private func keyButton(_ b: LibButton) -> some View {
        Button { tap(b) } label: {
            TabLabel(title: b.n, on: false, height: 52, ruSize: 12, forceRu: true)
        }
    }

    private func tap(_ b: LibButton) {
        guard let pat = IrLib.pattern(b) else {
            model.say("> неизвестный протокол кнопки", err: true)
            return
        }
        model.sendLibrary(pat.us, freq: pat.freq, status: "> tx " + b.n + " [sent]")
    }
}
