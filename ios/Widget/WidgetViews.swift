import SwiftUI
import WidgetKit

struct TodayMenuWidgetView: View {
    let entry: TodayMenuEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
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
        .containerBackground(for: .widget) {
            Color.brandRed
        }
    }

    @ViewBuilder
    private func header(nameFont: Font) -> some View {
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

    private var smallLayout: some View {
        VStack(alignment: .leading, spacing: 6) {
            header(nameFont: .footnote.weight(.bold))
            if let first = entry.dishes.first {
                Text(first.title)
                    .font(.title3.weight(.bold))
                    .lineLimit(3)
                if let price = first.price {
                    Text(price)
                        .font(.footnote)
                        .monospacedDigit()
                        .opacity(0.7)
                }
            } else {
                Text("widget.empty")
                    .font(.footnote)
                    .opacity(0.7)
            }
            Spacer(minLength: 0)
            Text("widget.today")
                .font(.caption2)
                .opacity(0.7)
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
            dishList(rows: 7, showType: true)
            Spacer(minLength: 0)
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

    @ViewBuilder
    private func dishList(rows: Int, showType: Bool) -> some View {
        let dishes = Array(entry.dishes.prefix(rows))
        if dishes.isEmpty {
            Text("widget.empty")
                .font(.footnote)
                .opacity(0.7)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(dishes.enumerated()), id: \.offset) { index, dish in
                    dishRow(dish: dish, showType: showType)
                    if index < dishes.count - 1 {
                        Rectangle()
                            .fill(.white.opacity(0.2))
                            .frame(height: 1)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func dishRow(dish: WidgetDish, showType: Bool) -> some View {
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
        .padding(.vertical, 4)
    }
}
