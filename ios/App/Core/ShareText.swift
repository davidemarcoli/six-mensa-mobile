import Foundation

enum ShareText {

    static func weekMenu(restaurant: Restaurant, day: DayMenu, language: ContentLanguage) -> String {
        var text = "SIX Mensa · \(restaurant.displayName)"
        if !day.day.isEmpty {
            text += " — \(day.day)"
            if !day.date.isEmpty {
                text += ", \(day.date)"
            }
        }
        for item in day.menues {
            var block = item.displayType
            if let intern = item.price?.intern {
                block += " · \(number(intern, language: language))"
            }
            if let extern = item.price?.extern {
                block += " / \(number(extern, language: language))"
            }
            var dish = item.title
            if !item.description.isEmpty {
                dish += " — \(item.description)"
            }
            if block.isEmpty {
                text += "\n\n\(dish)"
            } else {
                text += "\n\n\(block)\n\(dish)"
            }
        }
        return text
    }

    static func summaryLines(day: DayMenu, language: ContentLanguage) -> [String] {
        day.menues.map { item in
            guard let intern = item.price?.intern else { return item.title }
            return "\(item.title)  \(number(intern, language: language))"
        }
    }

    static func priceText(_ price: Price, language: ContentLanguage) -> String {
        var parts: [String] = []
        if let intern = price.intern {
            parts.append(number(intern, language: language))
        }
        if let extern = price.extern {
            parts.append(number(extern, language: language))
        }
        return parts.joined(separator: " / ")
    }

    private static func number(_ value: Double, language: ContentLanguage) -> String {
        let formatter = NumberFormatter()
        formatter.locale = language.locale
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.string(from: value as NSNumber) ?? String(format: "%.2f", value)
    }
}
