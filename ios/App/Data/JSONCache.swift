import Foundation

actor JSONCache {
    static let appGroupID = "group.dev.davidemarcoli.mensa"

    private let directory: URL

    init() {
        directory = Self.cacheDirectory()
    }

    static func containerURL() -> URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)
    }

    private static func cacheDirectory() -> URL {
        if let container = containerURL() {
            return container
        }
        let fileManager = FileManager.default
        if let support = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            return support
        }
        return fileManager.temporaryDirectory
    }

    private func fileURL(for key: String) -> URL {
        directory.appendingPathComponent("\(key).json")
    }

    func read<T: Decodable>(_ key: String, ttl: TimeInterval) -> T? {
        readWithAge(key, ttl: ttl)?.value
    }

    func readWithAge<T: Decodable>(_ key: String, ttl: TimeInterval) -> (value: T, fetchedAt: Date)? {
        let url = fileURL(for: key)
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        guard let data = try? Data(contentsOf: url) else {
            try? fileManager.removeItem(at: url)
            return nil
        }
        do {
            let envelope = try JSONDecoder().decode(Envelope<T>.self, from: data)
            let fetchedAt = Date(timeIntervalSince1970: envelope.fetchedAt)
            guard Date().timeIntervalSince(fetchedAt) <= ttl else { return nil }
            return (envelope.payload, fetchedAt)
        } catch {
            try? fileManager.removeItem(at: url)
            return nil
        }
    }

    func write<T: Encodable>(_ key: String, _ value: T) throws {
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let envelope = Envelope(fetchedAt: Date().timeIntervalSince1970, payload: value)
        let data = try JSONEncoder().encode(envelope)
        let target = fileURL(for: key)
        let temporary = directory.appendingPathComponent("\(key).json.tmp")
        // Write-then-rename so a kill mid-write cannot leave a truncated file behind.
        try data.write(to: temporary, options: .atomic)
        try? fileManager.removeItem(at: target)
        try fileManager.moveItem(at: temporary, to: target)
    }

    struct Envelope<T> {
        var fetchedAt: Double
        var payload: T
    }

    static func weekKey(restaurant: Restaurant, language: ContentLanguage) -> String {
        "week_\(restaurant.rawValue)_\(language.rawValue)"
    }

    static func historyKey(restaurant: Restaurant? = nil, from: String? = nil, to: String? = nil) -> String {
        let fromValue = from?.isEmpty == false ? from : nil
        let toValue = to?.isEmpty == false ? to : nil
        var key = "history_\(restaurant?.rawValue ?? "all")"
        if fromValue != nil || toValue != nil {
            key += "_\(fromValue ?? "all")_\(toValue ?? "all")"
        }
        return key
    }

    static let pdfKey = "pdf_links"

    /// Deletes cached history files whose `to` month is older than the retention
    /// window, so the app group storage does not creep without bound.
    func pruneHistory(retentionMonths: Int = 13) throws {
        let fileManager = FileManager.default
        let files = try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        let historyFiles = files.filter {
            $0.lastPathComponent.hasPrefix("history_") && $0.pathExtension == "json"
        }
        var refer = Calendar(identifier: .gregorian)
        refer.timeZone = DayResolver.zurich
        let cutoff = refer.date(byAdding: .month, value: -retentionMonths, to: Date()) ?? Date()

        for file in historyFiles {
            let name = file.deletingPathExtension().lastPathComponent
            guard let toToken = Self.historyToToken(in: name),
                  let monthDate = Self.monthDate(from: toToken, calendar: refer),
                  monthDate < cutoff
            else { continue }
            try? fileManager.removeItem(at: file)
        }
    }

    private static func historyToToken(in name: String) -> String? {
        let parts = name.split(separator: "_")
        guard parts.count >= 4 else { return nil }
        return String(parts[3])
    }

    private static func monthDate(from token: String, calendar: Calendar) -> Date? {
        let comps = token.split(separator: "-")
        guard comps.count == 2, let month = Int(comps[1]), let year = Int(comps[0]) else { return nil }
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1
        return calendar.date(from: components)
    }
}

extension JSONCache.Envelope: Decodable where T: Decodable {}
extension JSONCache.Envelope: Encodable where T: Encodable {}

enum CacheTTL {
    static let week: TimeInterval = 6 * 3600
    static let history: TimeInterval = 12 * 3600
    static let pdfLinks: TimeInterval = 24 * 3600
}
