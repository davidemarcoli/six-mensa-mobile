import SwiftUI
import UIKit

struct StatsView: View {
    @Environment(HistoryStore.self) private var history
    @Environment(SettingsStore.self) private var settings

    @State private var search = ""
    @State private var restaurant: Restaurant? = nil
    @State private var menuType: String? = nil
    @State private var dietary: DietaryType? = nil

    var body: some View {
        NavigationStack {
            Group {
                if history.isLoading && history.entries.isEmpty {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    content
                }
            }
            .navigationTitle(Text("stats.title"))
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { await history.load(force: true) }
        }
    }

    private var flat: [FlatMenuItem] { Stats.flatten(history.entries) }

    private var filters: StatsFilters {
        StatsFilters(
            search: search,
            restaurant: restaurant,
            menuType: menuType,
            dietary: dietary,
            from: history.from.isEmpty ? nil : history.from,
            to: history.to.isEmpty ? nil : history.to
        )
    }

    private var filtered: [FlatMenuItem] { Stats.applyFilters(flat, filters) }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(String(format: NSLocalizedString("stats.filtered", comment: ""), "\(filtered.count)", "\(flat.count)"))
                    .font(.subheadline)
                    .appForegroundStyle(.secondary)

                if let error = history.errorMessage {
                    HStack(spacing: 8) {
                        Image(systemName: "wifi.exclamationmark")
                        Text(error)
                    }
                    .font(.footnote)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color(uiColor: .systemRed).opacity(0.12)))
                }

                filterCard

                if filtered.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.largeTitle)
                            .appForegroundStyle(.tertiary)
                        Text("stats.search.empty")
                            .appForegroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 48)
                } else {
                    chartCard(NSLocalizedString("stats.price_trend", comment: "")) {
                        let trend = Stats.priceTrend(filtered)
                        PriceTrendChart(months: trend.months, series: trend.series, accent: settings.accentColor)
                    }
                    chartCard(NSLocalizedString("stats.dietary", comment: "")) {
                        DietaryDonut(counts: Stats.dietaryDistribution(filtered), accent: settings.accentColor)
                    }
                    chartCard(NSLocalizedString("stats.dishes", comment: "")) {
                        DishFrequencyCard(data: Stats.dishFrequency(filtered, limit: 50), accent: settings.accentColor)
                    }
                    chartCard(NSLocalizedString("stats.allergens", comment: "")) {
                        AllergenChart(data: Stats.allergenFrequency(filtered, limit: 20), accent: settings.accentColor)
                    }
                }
            }
            .padding()
        }
    }

    private var filterCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("stats.search", text: $search)
                .textFieldStyle(.roundedBorder)

            chipRow("stats.restaurant", items: [(NSLocalizedString("stats.restaurant.all", comment: ""), nil)] + Restaurant.allCases.map { ($0.displayName, $0) }, selection: $restaurant)

            chipRow("stats.diet", items: [(NSLocalizedString("stats.diet.all", comment: ""), nil)] + [DietaryType.meat, .vegetarian, .vegan].map { (dietLabel($0), $0) }, selection: $dietary)

            chipRow("stats.type", items: [(NSLocalizedString("stats.type.all", comment: ""), nil)] + Stats.availableMenuTypes(flat).map { ($0, $0) }, selection: $menuType)

            rangeRow
        }
        .padding(12)
        .glassCardBackground(cornerRadius: 14)
    }

    private var rangeRow: some View {
        HStack(spacing: 8) {
            Text("stats.range")
                .font(.footnote.weight(.semibold))
                .frame(width: 90, alignment: .leading)

            Menu {
                Button(NSLocalizedString("stats.range.all", comment: "")) { setRange(from: "", to: "") }
                    .disabled(history.from.isEmpty && history.to.isEmpty)
                ForEach(history.yearMonthOptions, id: \.self) { month in
                    Button(shortMonth(month)) { setRange(from: month, to: history.to) }
                }
            } label: {
                Text(history.from.isEmpty ? NSLocalizedString("stats.range.all", comment: "") : shortMonth(history.from))
                    .lineLimit(1)
            }

            Menu {
                Button(NSLocalizedString("stats.range.all", comment: "")) { setRange(from: history.from, to: "") }
                    .disabled(history.from.isEmpty && history.to.isEmpty)
                ForEach(history.yearMonthOptions, id: \.self) { month in
                    Button(shortMonth(month)) { setRange(from: history.from, to: month) }
                }
            } label: {
                Text(history.to.isEmpty ? NSLocalizedString("stats.range.all", comment: "") : shortMonth(history.to))
                    .lineLimit(1)
            }

            Spacer()
        }
    }

    private func setRange(from: String, to: String) {
        guard history.from != from || history.to != to else { return }
        if let (min, max) = bothNonEmpty(from, to) {
            if min > max { history.to = min; history.from = max } else { history.from = min; history.to = max }
        } else {
            history.from = from
            history.to = to
        }
        Task { await history.load(force: true) }
    }

    private func shortMonth(_ yearMonth: String) -> String {
        String(yearMonth.suffix(5))
    }

    private func dietLabel(_ type: DietaryType) -> String {
        switch type {
        case .meat:
            return NSLocalizedString("stats.diet.meat", comment: "")
        case .vegetarian:
            return NSLocalizedString("stats.diet.vegetarian", comment: "")
        case .vegan:
            return NSLocalizedString("stats.diet.vegan", comment: "")
        case .unknown:
            return ""
        }
    }

    private func chipRow<T: Hashable>(
        _ title: String,
        items: [(label: String, value: T?)],
        selection: Binding<T?>
    ) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(LocalizedStringKey(title))
                .font(.footnote.weight(.semibold))
                .frame(width: 90, alignment: .leading)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        let isSelected = item.value == selection.wrappedValue
                        Button {
                            selection.wrappedValue = item.value
                        } label: {
                            Text(item.label)
                                .font(.footnote)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Capsule().fill(isSelected ? settings.accentColor : Color(uiColor: .tertiarySystemFill)))
                                .foregroundStyle(isSelected ? Color.white : Color.primary)
                                .overlay {
                                    if isSelected {
                                        Capsule().strokeBorder(settings.accentColor.opacity(0.4), lineWidth: 1)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                    }
                }
                .sensoryFeedback(.selection, trigger: selection.wrappedValue)
            }
        }
    }

    private func chartCard<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
            content()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCardBackground(cornerRadius: 14)
    }
}

private func bothNonEmpty(_ a: String, _ b: String) -> (String, String)? {
    a.isEmpty || b.isEmpty ? nil : (a, b)
}
