import WidgetKit
import SwiftUI

struct WidgetDish: Hashable {
    var title: String
    var type: String?
    var price: String?
    var isVegan: Bool
}

struct TodayMenuEntry: TimelineEntry {
    var date: Date
    var restaurantName: String
    var dateLabel: String
    var dishes: [WidgetDish]
    var preferredDishes: [WidgetDish]
    var isAvailable: Bool
}

struct TodayMenuProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayMenuEntry {
        TodayMenuEntry(
            date: Date(),
            restaurantName: "HTP",
            dateLabel: "31. August",
            dishes: [
                WidgetDish(title: "Rindergulasch mit Spätzle", type: "Local", price: "12.90 / 17.90", isVegan: false),
                WidgetDish(title: "Tomaten-Basilikum-Suppe", type: "Climate", price: "5.50 / 7.90", isVegan: true),
                WidgetDish(title: "Penne all'Arrabbiata", type: "Pizza & Pasta", price: "9.90 / 14.90", isVegan: true),
                WidgetDish(title: "Hähnchenschnitzel mit Kartoffelsalat", type: "Local", price: "13.90 / 18.90", isVegan: false),
                WidgetDish(title: "Gemüsecurry mit Reis", type: "Global", price: "10.90 / 15.90", isVegan: true)
            ],
            preferredDishes: [
                WidgetDish(title: "Gemüsecurry mit Reis", type: "Global", price: "10.90 / 15.90", isVegan: true)
            ],
            isAvailable: true
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayMenuEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func timeline(for entry: TodayMenuEntry, in context: Context) async -> Timeline<TodayMenuEntry> {
        await makeTimeline()
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayMenuEntry>) -> Void) {
        Task {
            completion(await makeTimeline())
        }
    }

    private func makeTimeline() async -> Timeline<TodayMenuEntry> {
        let settings = SettingsStore()
        let restaurant = settings.standardRestaurant
        let language = settings.language
        let preferredType = settings.preferredMenuType
        let key = JSONCache.weekKey(restaurant: restaurant, language: language)
        let now = Date()

        // Prefer the week the app cached in the shared container. If it is
        // missing or expired (fresh install, app group hiccup, or the app has
        // not been opened for a while) fetch it directly and seed the cache so
        // the widget never gets stuck on "Menu unavailable".
        var days: [DayMenu]? = await JSONCache().read(key, ttl: CacheTTL.week)
        if days?.isEmpty != false {
            if let fetched = try? await MenuAPI().week(restaurant: restaurant, language: language),
               !fetched.isEmpty {
                try? await JSONCache().write(key, fetched)
                days = fetched
            }
        }

        guard let days, !days.isEmpty else {
            // Still nothing (e.g. offline): retry soon so the widget self-heals
            // once connectivity returns or the app writes the cache.
            let retryDate = now.addingTimeInterval(3600)
            let base = TodayMenuEntry(
                date: now,
                restaurantName: restaurant.displayName,
                dateLabel: "",
                dishes: [],
                preferredDishes: [],
                isAvailable: false
            )
            let retry = TodayMenuEntry(
                date: retryDate,
                restaurantName: base.restaurantName,
                dateLabel: "",
                dishes: base.dishes,
                preferredDishes: base.preferredDishes,
                isAvailable: false
            )
            return Timeline(entries: [base, retry], policy: .after(retryDate))
        }

        // Build one entry per cached day so the widget shows the correct menu for
        // each day and advances itself (via the timeline policy) without needing
        // the app to be opened.
        var entries: [TodayMenuEntry] = []
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = DayResolver.zurich
        for day in days {
            guard let dayDate = DayResolver.resolve(day, language: language, today: now),
                  dayDate >= calendar.startOfDay(for: now),
                  let startOfDay = calendar.date(bySettingHour: 0, minute: 0, second: 0, of: dayDate)
            else { continue }
            let dishes: [WidgetDish] = day.menues.prefix(7).map { Self.widgetDish(from: $0, language: language) }
            let preferredDishes: [WidgetDish] = day.filtered(to: preferredType).menues.prefix(7).map { Self.widgetDish(from: $0, language: language) }
            entries.append(
                TodayMenuEntry(
                    date: startOfDay,
                    restaurantName: restaurant.displayName,
                    dateLabel: day.date,
                    dishes: dishes,
                    preferredDishes: preferredDishes,
                    isAvailable: true
                )
            )
        }

        guard !entries.isEmpty else {
            let retryDate = now.addingTimeInterval(3600)
            let base = TodayMenuEntry(
                date: now,
                restaurantName: restaurant.displayName,
                dateLabel: "",
                dishes: [],
                preferredDishes: [],
                isAvailable: false
            )
            return Timeline(entries: [base], policy: .after(retryDate))
        }

        // Keep the system asking us for fresh timelines past the last entry.
        let refreshPolicyDate = (entries.last?.date ?? now).addingTimeInterval(3600 * 3)
        return Timeline(entries: entries, policy: .after(refreshPolicyDate))
    }

    private static func widgetDish(from item: MenuItem, language: ContentLanguage) -> WidgetDish {
        WidgetDish(
            title: item.title,
            type: item.displayType.isEmpty ? nil : item.displayType,
            price: item.price.flatMap { $0.hasAny ? ShareText.priceText($0, language: language) : nil },
            isVegan: item.dietaryType.isVegan
        )
    }
}

struct TodayMenuWidget: Widget {
    let kind: String = "TodayMenuWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayMenuProvider()) { entry in
            TodayMenuWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("widget.name"))
        .description(Text("widget.description"))
        .supportedFamilies([
            .systemSmall, .systemMedium, .systemLarge,
            .accessoryCircular, .accessoryRectangular, .accessoryInline
        ])
        .containerBackgroundRemovable(true)
    }
}
