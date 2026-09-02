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
            isAvailable: true
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayMenuEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func timeline(for entry: TodayMenuEntry, in context: Context) async -> Timeline<TodayMenuEntry> {
        let settings = SettingsStore()
        let restaurant = settings.standardRestaurant
        let language = settings.language
        let days = await JSONCache().read(
            [DayMenu].self,
            JSONCache.weekKey(restaurant: restaurant, language: language),
            ttl: CacheTTL.week
        )
        let now = Date()

        if let days, !days.isEmpty {
            let todayIndex = DayResolver.indexForToday(in: days, language: language)
            if todayIndex >= 0 {
                let day = days[todayIndex]
                let dishes = day.menues.prefix(7).map { item in
                    WidgetDish(
                        title: item.title,
                        type: item.displayType.isEmpty ? nil : item.displayType,
                        price: item.price.flatMap { $0.hasAny ? ShareText.priceText($0, language: language) : nil },
                        isVegan: item.dietaryType.isVegan
                    )
                }
                let base = TodayMenuEntry(
                    date: now,
                    restaurantName: restaurant.displayName,
                    dateLabel: day.date,
                    dishes: dishes,
                    isAvailable: true
                )
                let secondDate = now.addingTimeInterval(CacheTTL.week)
                let second = TodayMenuEntry(
                    date: secondDate,
                    restaurantName: base.restaurantName,
                    dateLabel: base.dateLabel,
                    dishes: base.dishes,
                    isAvailable: true
                )
                return Timeline(entries: [base, second], policy: .after(secondDate))
            }
        }

        let retryDate = now.addingTimeInterval(3600)
        let base = TodayMenuEntry(
            date: now,
            restaurantName: restaurant.displayName,
            dateLabel: "",
            dishes: [],
            isAvailable: false
        )
        let retry = TodayMenuEntry(
            date: retryDate,
            restaurantName: base.restaurantName,
            dateLabel: "",
            dishes: [],
            isAvailable: false
        )
        return Timeline(entries: [base, retry], policy: .after(retryDate))
    }
}

struct TodayMenuWidget: Widget {
    let kind: String = "TodayMenuWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayMenuProvider()) { entry in
            TodayMenuWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("widget.name"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .containerBackgroundRemovable(true)
    }
}
