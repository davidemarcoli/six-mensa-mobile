import SwiftUI

struct CompareView: View {
    @Environment(SettingsStore.self) private var settings

    @State private var weeks: [Restaurant: [DayMenu]] = [:]
    @State private var dayLabels: [(name: String, date: String)] = []
    @State private var selectedDay = 0
    @State private var isLoading = true
    @State private var errorText: String?

    private var safeDay: Int {
        dayLabels.indices.contains(selectedDay) ? selectedDay : 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    dayHeader
                    ForEach([Restaurant.htp, Restaurant.ht201], id: \.self) { restaurant in
                        section(for: restaurant)
                            .padding()
                    }
                }
            }
            .refreshable { await load(force: true) }
            .navigationTitle("compare.title")
            .navigationBarTitleDisplayMode(.inline)
            .overlay {
                if isLoading && weeks.isEmpty {
                    ProgressView()
                } else if let errorText, weeks.isEmpty {
                    VStack(spacing: 16) {
                        Text(errorText)
                            .appForegroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                        Button(NSLocalizedString("retry", comment: "")) {
                            Task { await load(force: true) }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
        .task { await load(force: false) }
    }

    private var dayHeader: some View {
        HStack {
            Button {
                guard !dayLabels.isEmpty else { return }
                selectedDay = (safeDay - 1 + dayLabels.count) % dayLabels.count
            } label: {
                Image(systemName: "chevron.left")
            }
            .disabled(dayLabels.isEmpty)
            .accessibilityLabel(NSLocalizedString("compare.prev_day", comment: ""))
            Spacer()
            if !dayLabels.isEmpty {
                VStack(spacing: 2) {
                    Text(dayLabels[safeDay].name)
                        .font(.headline)
                    Text(dayLabels[safeDay].date)
                        .font(.caption)
                        .appForegroundStyle(.secondary)
                }
            }
            Spacer()
            Button {
                guard !dayLabels.isEmpty else { return }
                selectedDay = (safeDay + 1) % dayLabels.count
            } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(dayLabels.isEmpty)
            .accessibilityLabel(NSLocalizedString("compare.next_day", comment: ""))
        }
        .padding()
    }

    @ViewBuilder
    private func section(for restaurant: Restaurant) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(restaurant.displayName)
                .font(.title3.weight(.semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            if let day = weeks[restaurant], day.indices.contains(safeDay) {
                ForEach(day[safeDay].menues) { item in
                    MenuItemCard(item: item, language: settings.language, accent: settings.accentColor)
                }
            } else {
                Text(String(format: NSLocalizedString("compare.empty", comment: ""), restaurant.displayName))
                    .appForegroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func load(force: Bool) async {
        let language = settings.language
        for restaurant in Restaurant.allCases {
            let key = JSONCache.weekKey(restaurant: restaurant, language: language)
            if !force {
                if let cached: [DayMenu] = await JSONCache().read(key, ttl: CacheTTL.week) {
                    weeks[restaurant] = cached
                } else if let stale: [DayMenu] = await JSONCache().read(key, ttl: .infinity) {
                    weeks[restaurant] = stale
                }
            } else if let stale: [DayMenu] = await JSONCache().read(key, ttl: .infinity) {
                weeks[restaurant] = stale
            }
            do {
                let fresh = try await MenuAPI().week(restaurant: restaurant, language: language)
                weeks[restaurant] = fresh
                try? await JSONCache().write(key, fresh)
            } catch {
                if weeks[restaurant] == nil {
                    errorText = NSLocalizedString("error.generic", comment: "")
                }
            }
        }
        let days = weeks[.htp] ?? weeks[.ht201] ?? []
        dayLabels = days.map { ($0.day, $0.date) }
        let todayIndex = DayResolver.indexForToday(in: days, language: language)
        selectedDay = todayIndex >= 0 ? todayIndex : 0
        if !weeks.isEmpty {
            errorText = nil
        }
        isLoading = false
    }
}
