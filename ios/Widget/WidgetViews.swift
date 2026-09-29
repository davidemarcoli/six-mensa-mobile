import SwiftUI
import WidgetKit

private let appURL = URL(string: "mensa://")!

struct TodayMenuWidgetView: View {
    let entry: TodayMenuEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            switch family {
            case .accessoryCircular:
                accessoryCircularLayout
            case .accessoryRectangular:
                accessoryRectangularLayout
            case .accessoryInline:
                accessoryInlineLayout
            default:
                homeScreenLayout
            }
        }
        .containerBackground(for: .widget) {
            if isAccessory {
                // Keep the grey background only for the small circular lock-screen
                // widget. The larger rectangular one is left on the wallpaper.
                if family == .accessoryRectangular {
                    Color.clear
                } else {
                    AccessoryWidgetBackground()
                }
            } else {
                accentedColor
            }
        }
    }

    private var accentedColor: Color {
        SettingsStore().accentColor
    }

    private var isAccessory: Bool {
        family == .accessoryCircular || family == .accessoryRectangular || family == .accessoryInline
    }

    // MARK: - Home screen layouts

    @ViewBuilder
    private var homeScreenLayout: some View {
        Group {
            if entry.isAvailable {
                switch family {
                case .systemSmall:
                    smallLayout
                case .systemLarge:
                    largeLayout
                default:
                    mediumLayout
                }
            } else {
                unavailableLayout
            }
        }
    }

    @ViewBuilder
    private func header(nameFont: Font) -> some View {
        Link(destination: appURL) {
            HStack(spacing: 5) {
                Image(systemName: "utensils.circle.fill")
                    .font(.system(size: 18))
                Text(entry.restaurantName)
                    .font(nameFont)
                if !entry.dateLabel.isEmpty {
                    Text(entry.dateLabel)
                        .font(.caption)
                        .opacity(0.7)
                }
                Spacer(minLength: 0)
            }
            .lineLimit(1)
            .foregroundStyle(.white)
        }
    }

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 6) {
            header(nameFont: .footnote.weight(.bold))
            if let first = entry.preferredDishes.first ?? entry.dishes.first {
                Link(destination: appURL) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(first.title)
                            .font(.title3.weight(.bold))
                            .lineLimit(3)
                        if let type = first.type {
                            Text(type)
                                .font(.footnote)
                                .opacity(0.7)
                        }
                    }
                }
            } else {
                Text("widget.empty")
                    .font(.footnote)
                    .opacity(0.7)
            }
            Spacer(minLength: 0)
            Link(destination: appURL) {
                Text("widget.today")
                    .font(.caption2)
                    .opacity(0.7)
            }
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var mediumLayout: some View {
        VStack(alignment: .leading, spacing: 8) {
            header(nameFont: .subheadline.weight(.bold))
            dishList(rows: 5, showType: false)
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
    }

    private var largeLayout: some View {
        VStack(alignment: .leading, spacing: 10) {
            header(nameFont: .subheadline.weight(.bold))
            dishList(rows: 7, showType: true, fillHeight: true)
        }
        .foregroundStyle(.white)
    }

    private var unavailableLayout: some View {
        VStack(spacing: 8) {
            Image(systemName: "utensils.circle.fill")
                .font(.system(size: 36))
            Text("widget.unavailable")
                .font(.footnote)
                .multilineTextAlignment(.center)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Lock screen layouts

    private var accessoryCircularLayout: some View {
        ZStack {
            AccessoryWidgetBackground()
            if entry.isAvailable, let first = entry.preferredDishes.first ?? entry.dishes.first {
                VStack(spacing: 0) {
                    Image(systemName: first.isVegan ? "leaf.fill" : "fork.knife")
                        .font(.system(size: 22))
                    Text(first.type ?? entry.restaurantName)
                        .font(.system(size: 9, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
                .foregroundStyle(.white)
            } else {
                Image(systemName: "utensils")
                    .font(.system(size: 22))
                    .foregroundStyle(.white)
            }
        }
        .widgetURL(appURL)
    }

    private var accessoryRectangularLayout: some View {
        ZStack {
            if entry.isAvailable, let first = entry.preferredDishes.first ?? entry.dishes.first {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(entry.restaurantName)
                            .font(.system(size: 11, weight: .bold))
                        Spacer()
                        if let type = first.type {
                            Text(type)
                                .font(.system(size: 10, weight: .medium))
                                .opacity(0.8)
                        }
                    }
                    Text(first.title)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                }
                .foregroundStyle(.white)
            } else {
                Label("widget.unavailable", systemImage: "utensils")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white)
            }
        }
        .widgetURL(appURL)
    }

    private var accessoryInlineLayout: some View {
        if entry.isAvailable, let first = entry.preferredDishes.first ?? entry.dishes.first {
            Label {
                Text(first.title)
            } icon: {
                Image(systemName: first.isVegan ? "leaf.fill" : "fork.knife")
            }
            .widgetURL(appURL)
        } else {
            Label("widget.unavailable", systemImage: "utensils")
                .widgetURL(appURL)
        }
    }

    // MARK: - Shared helpers

    @ViewBuilder
    private func dishList(rows: Int, showType: Bool, fillHeight: Bool = false) -> some View {
        let dishes = Array(entry.dishes.prefix(rows))
        if dishes.isEmpty {
            Text("widget.empty")
                .font(.footnote)
                .opacity(0.7)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(dishes.enumerated()), id: \.offset) { index, dish in
                    Link(destination: appURL) {
                        dishRow(dish: dish, showType: showType, fillsHeight: fillHeight)
                    }
                    if index < dishes.count - 1 {
                        Rectangle()
                            .fill(.white.opacity(0.2))
                            .frame(height: 1)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: fillHeight ? .infinity : nil, alignment: .top)
        }
    }

    @ViewBuilder
    private func dishRow(dish: WidgetDish, showType: Bool, fillsHeight: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                if showType, let type = dish.type {
                    Text(type)
                        .font(.caption2)
                        .opacity(0.6)
                        .lineLimit(1)
                }
                Text(dish.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            if let price = dish.price {
                Text(price)
                    .font(.footnote)
                    .monospacedDigit()
                    .opacity(0.7)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: fillsHeight ? .infinity : nil, alignment: .leading)
        .padding(.vertical, fillsHeight ? 0 : 4)
    }
}
