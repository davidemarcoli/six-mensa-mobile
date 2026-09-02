import Foundation

struct StatsFilters: Equatable {
    var search: String = ""
    var restaurant: Restaurant? = nil
    var menuType: String? = nil
    var dietary: DietaryType? = nil
    var from: String? = nil
    var to: String? = nil
}

struct FlatMenuItem: Identifiable {
    var id: Int
    var restaurant: Restaurant
    var year: Int
    var month: Int
    var week: Int
    var rawDay: String
    var rawDate: String
    var item: MenuItem
    var yearMonth: String
}

struct PricePoint: Identifiable {
    var month: String
    var value: Double
    var id: String { month }
}

struct PriceTrendSeries: Identifiable {
    let type: String
    let isOverall: Bool
    let points: [PricePoint]
    var id: String { isOverall ? "overall" : type }
}

struct DietaryCount: Identifiable {
    let type: DietaryType
    let count: Int
    var id: String { type.rawValue }
}

struct Counted: Identifiable {
    let label: String
    let count: Int
    var id: String { label }
}

enum Stats {
    static func flatten(_ entries: [HistoryEntry]) -> [FlatMenuItem] {
        var result: [FlatMenuItem] = []
        var index = 0
        for entry in entries {
            for day in entry.data {
                for item in day.menues {
                    result.append(FlatMenuItem(
                        id: index,
                        restaurant: entry.restaurant,
                        year: entry.year,
                        month: entry.month,
                        week: entry.week,
                        rawDay: day.day,
                        rawDate: day.date,
                        item: item,
                        yearMonth: entry.yearMonth
                    ))
                    index += 1
                }
            }
        }
        return result
    }

    static func applyFilters(_ flat: [FlatMenuItem], _ f: StatsFilters) -> [FlatMenuItem] {
        let needle = f.search.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return flat.filter { entry in
            let item = entry.item
            if let restaurant = f.restaurant, entry.restaurant != restaurant {
                return false
            }
            if let menuType = f.menuType, item.displayType != menuType {
                return false
            }
            if let dietary = f.dietary, item.dietaryType != dietary {
                return false
            }
            if !needle.isEmpty {
                let haystack = "\(item.title) \(item.description) \(item.origin ?? "")".lowercased()
                if !haystack.contains(needle) {
                    return false
                }
            }
            if let from = f.from, entry.yearMonth < from {
                return false
            }
            if let to = f.to, entry.yearMonth > to {
                return false
            }
            return true
        }
    }

    static func availableMenuTypes(_ flat: [FlatMenuItem]) -> [String] {
        Array(Set(flat.map { $0.item.displayType })).filter { !$0.isEmpty }.sorted()
    }

    static func priceTrend(_ flat: [FlatMenuItem]) -> (months: [String], series: [PriceTrendSeries]) {
        let withPrice = flat.filter { ($0.item.price?.intern ?? 0) > 0 }
        guard !withPrice.isEmpty else {
            return ([], [])
        }
        let yearMonths = Array(Set(withPrice.map(\.yearMonth))).sorted()

        func points(for items: [FlatMenuItem]) -> [PricePoint] {
            let byMonth = Dictionary(grouping: items, by: \.yearMonth)
            return yearMonths.compactMap { yearMonth -> PricePoint? in
                guard let monthItems = byMonth[yearMonth] else { return nil }
                let values = monthItems.compactMap { $0.item.price?.intern }
                guard !values.isEmpty else { return nil }
                // "2025-04" → "25-04"
                return PricePoint(
                    month: String(yearMonth.dropFirst(2)),
                    value: values.reduce(0, +) / Double(values.count)
                )
            }
        }

        var series = [PriceTrendSeries(type: "", isOverall: true, points: points(for: withPrice))]
        let types = Array(Set(withPrice.map(\.item.displayType))).filter { !$0.isEmpty }.sorted()
        for type in types {
            series.append(PriceTrendSeries(
                type: type,
                isOverall: false,
                points: points(for: withPrice.filter { $0.item.displayType == type })
            ))
        }
        return (yearMonths.map { String($0.dropFirst(2)) }, series)
    }

    static func dietaryDistribution(_ flat: [FlatMenuItem]) -> [DietaryCount] {
        [DietaryType.meat, .vegetarian, .vegan].compactMap { type in
            let count = flat.count { $0.item.dietaryType == type }
            return count > 0 ? DietaryCount(type: type, count: count) : nil
        }
    }

    static func dishFrequency(_ flat: [FlatMenuItem], limit: Int = 20) -> [Counted] {
        var counts: [String: Int] = [:]
        for entry in flat {
            let label = entry.item.title.trimmingCharacters(in: .whitespaces)
            guard !label.isEmpty else { continue }
            counts[label, default: 0] += 1
        }
        return ranked(counts, limit: limit)
    }

    static func allergenFrequency(_ flat: [FlatMenuItem], limit: Int = 20) -> [Counted] {
        var counts: [String: Int] = [:]
        for entry in flat {
            for allergen in entry.item.allergens {
                counts[allergen, default: 0] += 1
            }
        }
        return ranked(counts, limit: limit)
    }

    private static func ranked(_ counts: [String: Int], limit: Int) -> [Counted] {
        Array(
            counts
                .map { Counted(label: $0.key, count: $0.value) }
                .sorted { lhs, rhs in
                    if lhs.count != rhs.count {
                        return lhs.count > rhs.count
                    }
                    return lhs.label < rhs.label
                }
                .prefix(limit)
        )
    }
}
