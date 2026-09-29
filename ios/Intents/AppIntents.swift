import AppIntents
import SwiftUI

/// Exposes "today's menu" to Siri / Shortcuts (App Intelligence) using the
/// cached week, so it works without opening the app first.
struct TodayMenuIntent: AppIntent {

    static let title: LocalizedStringResource = "Today's menu"

    static let description = IntentDescription(
        "Reads today's lunch menu from the SIX canteens.",
        categoryName: "Food"
    )

    @Parameter(title: "Restaurant")
    var restaurant: RestaurantEntity?

    static var parameterSummary: some ParameterSummary {
        Summary("Show today's menu at \(\.$restaurant)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog & ShowsSnippetView {
        let settings = SettingsStore()
        let restaurant = restaurant?.restaurant ?? settings.standardRestaurant
        let language = settings.language
        let preferred = settings.preferredMenuType

        let days: [DayMenu]? = await JSONCache().read(
            JSONCache.weekKey(restaurant: restaurant, language: language),
            ttl: .infinity
        )
        guard let day = days?.first(where: { DayResolver.isToday($0, language: language) }) else {
            return .result(
                dialog: IntentDialog("No menu is available for today.")
            )
        }

        let shown = day.filtered(to: preferred).menues.prefix(5)
        return .result(
            dialog: IntentDialog(stringLiteral:
                ShareText.summaryLines(day: day.filtered(to: preferred), language: language)
                    .prefix(3)
                    .joined(separator: ", ")
            ),
            view: MenuSnippetView(dishes: shown.map {
                MenuSnippetItem(title: $0.title, type: $0.displayType)
            })
        )
    }
}

struct RestaurantEntity: AppEntity, Equatable {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Restaurant"

    let restaurant: Restaurant

    static var defaultQuery = RestaurantQuery()

    var id: String { restaurant.rawValue }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(restaurant.displayName)")
    }
}

struct RestaurantQuery: EntityQuery {
    func entities(for identifiers: [RestaurantEntity.ID]) async throws -> [RestaurantEntity] {
        Restaurant.allCases.filter { identifiers.contains($0.rawValue) }.map(RestaurantEntity.init)
    }

    func suggestedEntities() async throws -> [RestaurantEntity] {
        Restaurant.allCases.map(RestaurantEntity.init)
    }
}

private struct MenuSnippetItem: Codable, Identifiable {
    let title: String
    let type: String
    var id: String { "\(title)-\(type)" }
}

private struct MenuSnippetView: View {
    let dishes: [MenuSnippetItem]

    var body: some View {
        List(dishes) { dish in
            VStack(alignment: .leading, spacing: 2) {
                if !dish.type.isEmpty {
                    Text(dish.type).font(.caption).foregroundStyle(.secondary)
                }
                Text(dish.title)
            }
        }
    }
}

/// Registers the intent so iOS surfaces it automatically in Siri Suggestions,
/// the Shortcuts app, and the app icon's press-and-hold quick actions menu.
struct SIXMensaShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: TodayMenuIntent(),
            phrases: [
                "What's for lunch today in \(\.$restaurant)? ${applicationName}",
                "Show today's menu ${applicationName}",
                "What's for lunch? ${applicationName}",
            ],
            shortTitle: "Today's menu",
            systemImageName: "fork.knife"
        )
    }
}