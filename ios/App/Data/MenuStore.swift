import Foundation
import Observation
import WidgetKit

@MainActor @Observable
final class MenuStore {
    enum LoadState: Equatable {
        case loading, ready, failed
    }

    var restaurant: Restaurant
    var week: [DayMenu] = []
    var lastUpdated: Date?
    var isRefreshing: Bool
    var errorMessage: String?
    var state: LoadState
    var selectedDayIndex: Int

    private let settings: SettingsStore
    private let cache = JSONCache()
    private let api = MenuAPI()
    private var lastFetch: [String: Date] = [:]

    init(settings: SettingsStore) {
        self.settings = settings
        self.restaurant = settings.standardRestaurant
        self.isRefreshing = false
        self.state = .loading
        self.selectedDayIndex = 0
    }

    var todayIndex: Int {
        DayResolver.indexForToday(in: week, language: settings.language)
    }

    var currentDay: DayMenu? {
        week.indices.contains(selectedDayIndex) ? week[selectedDayIndex] : nil
    }

    func load() async {
        let key = JSONCache.weekKey(restaurant: restaurant, language: settings.language)
        let entry: (value: [DayMenu], fetchedAt: Date)? = await cache.readWithAge(key, ttl: CacheTTL.week)
        if let entry {
            week = entry.value
            lastUpdated = entry.fetchedAt
            state = .ready
            errorMessage = nil
        } else if week.isEmpty {
            state = .loading
        }
        await refresh(force: false)
        selectToday()
    }

    func refresh(force: Bool = false) async {
        let key = JSONCache.weekKey(restaurant: restaurant, language: settings.language)
        let now = Date()
        if !force, let last = lastFetch[key], now.timeIntervalSince(last) < Self.minRefreshInterval {
            return
        }
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let days = try await api.week(restaurant: restaurant, language: settings.language)
            lastFetch[key] = now
            week = days
            lastUpdated = now
            state = .ready
            errorMessage = nil
            if !week.indices.contains(selectedDayIndex) {
                selectToday()
            }
            try? await cache.write(key, days)
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            if !week.isEmpty {
                state = .ready
                let when = lastUpdated ?? now
                let label = DayResolver.relativeUpdateLabel(since: when)
                errorMessage = String(format: NSLocalizedString("error.stale", comment: ""), label)
            } else {
                state = .failed
                errorMessage = NSLocalizedString("error.generic", comment: "")
            }
        }
    }

    func selectRestaurant(_ newRestaurant: Restaurant) async {
        restaurant = newRestaurant
        // Keep the persisted standard restaurant in sync so the widget, Siri
        // intent and notifications read the same cache key we are about to write.
        if settings.standardRestaurant != newRestaurant {
            settings.standardRestaurant = newRestaurant
        }
        week = []
        lastUpdated = nil
        errorMessage = nil
        state = .loading
        selectedDayIndex = 0
        await load()
        WidgetCenter.shared.reloadAllTimelines()
    }

    func shareText(for day: DayMenu) -> String {
        ShareText.weekMenu(restaurant: restaurant, day: day, language: settings.language)
    }

    private func selectToday() {
        let today = DayResolver.indexForToday(in: week, language: settings.language)
        selectedDayIndex = today >= 0 ? today : 0
    }

    private static let minRefreshInterval: TimeInterval = 30
}
