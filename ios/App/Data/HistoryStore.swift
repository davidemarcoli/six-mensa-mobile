import Foundation
import Observation

@MainActor @Observable
final class HistoryStore {
    var entries: [HistoryEntry] = []
    var isLoading: Bool
    var errorMessage: String?
    var from: String
    var to: String
    var status: APIStatus?

    static let earliestMonth = "2025-04"

    private let cache = JSONCache()
    private let api = MenuAPI()

    init() {
        let range = Self.defaultRange()
        self.from = range.from
        self.to = range.to
        self.isLoading = false
    }

    static func defaultRange() -> (from: String, to: String) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = DayResolver.zurich
        let now = Date()
        let toComponents = calendar.dateComponents([.year, .month], from: now)
        let to = String(format: "%04d-%02d", toComponents.year ?? 2025, toComponents.month ?? 1)
        let fromDate = calendar.date(byAdding: .month, value: -2, to: now) ?? now
        let fromComponents = calendar.dateComponents([.year, .month], from: fromDate)
        var from = String(format: "%04d-%02d", fromComponents.year ?? 2025, fromComponents.month ?? 1)
        if from < earliestMonth {
            from = earliestMonth
        }
        return (from, to)
    }

    func load(force: Bool = false) async {
        let key = JSONCache.historyKey(from: from, to: to)
        let cached: [HistoryEntry]? = await cache.read(key, ttl: CacheTTL.history)
        if !force, let cached {
            entries = cached
            errorMessage = nil
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let result = try await api.history(from: from, to: to)
            entries = result
            errorMessage = nil
            try? await cache.write(key, result)
        } catch {
            let stale: [HistoryEntry]? = await cache.read(key, ttl: .infinity)
            if let stale {
                entries = stale
            }
            errorMessage = NSLocalizedString("error.generic", comment: "")
        }
    }

    func loadStatus() async {
        do {
            status = try await api.status()
        } catch {
            // Best effort: keep whatever status we had before.
        }
    }

    var yearMonthOptions: [String] {
        Array(Set(entries.map(\.yearMonth))).sorted(by: >)
    }
}
