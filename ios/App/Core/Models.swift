import Foundation

struct Price: Codable, Hashable, Sendable {
    var intern: Double?
    var extern: Double?

    var hasAny: Bool { intern != nil || extern != nil }
}

enum DietaryType: String, Codable, CaseIterable, Sendable {
    case meat, vegetarian, vegan, unknown

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw: String
        if let decoded = try? container.decode(String.self) {
            raw = decoded.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        } else {
            raw = ""
        }
        self = DietaryType(rawValue: raw) ?? .unknown
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    var isVegan: Bool { self == .vegan }
}

struct MenuItem: Codable, Hashable, Sendable, Identifiable {
    var title: String
    var description: String
    var type: String
    var dietaryType: DietaryType
    var price: Price?
    var origin: String?
    var allergens: [String]
    var imagePath: String?

    var id: String {
        let pricePart: String
        if let price {
            pricePart = "\(price.intern.map(String.init) ?? "-")|\(price.extern.map(String.init) ?? "-")"
        } else {
            pricePart = "-"
        }
        return "\(title)|\(pricePart)|\(description)"
    }

    var displayType: String { MenuTypeNormalizer.normalize(type) }

    enum CodingKeys: String, CodingKey {
        case title, description, type, dietaryType, price, origin, allergens, imagePath
    }

    init(
        title: String,
        description: String,
        type: String,
        dietaryType: DietaryType,
        price: Price?,
        origin: String?,
        allergens: [String],
        imagePath: String?
    ) {
        self.title = title
        self.description = description
        self.type = type
        self.dietaryType = dietaryType
        self.price = price
        self.origin = origin
        self.allergens = allergens
        self.imagePath = imagePath
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = (try container.decodeIfPresent(String.self, forKey: .title) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        description = (try container.decodeIfPresent(String.self, forKey: .description) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        type = try container.decodeIfPresent(String.self, forKey: .type) ?? ""
        dietaryType = try container.decodeIfPresent(DietaryType.self, forKey: .dietaryType) ?? .unknown
        price = try container.decodeIfPresent(Price.self, forKey: .price)?.takeIf { $0.hasAny }
        if let rawOrigin = try container.decodeIfPresent(String.self, forKey: .origin) {
            let trimmed = rawOrigin.trimmingCharacters(in: .whitespacesAndNewlines)
            origin = trimmed.isEmpty ? nil : trimmed
        } else {
            origin = nil
        }
        allergens = Self.sanitizeAllergens(try container.decodeIfPresent([String].self, forKey: .allergens) ?? [])
        imagePath = try container.decodeIfPresent(String.self, forKey: .imagePath)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(title, forKey: .title)
        try container.encode(description, forKey: .description)
        try container.encode(type, forKey: .type)
        try container.encode(dietaryType, forKey: .dietaryType)
        try container.encodeIfPresent(price, forKey: .price)
        try container.encodeIfPresent(origin, forKey: .origin)
        try container.encode(allergens, forKey: .allergens)
        try container.encodeIfPresent(imagePath, forKey: .imagePath)
    }

    // The archive leaks serializer junk ("imagePath", "image/<hex>") into some allergen lists.
    private static func sanitizeAllergens(_ raw: [String]) -> [String] {
        var seen: Set<String> = []
        var result: [String] = []
        for entry in raw {
            let value = entry.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { continue }
            if value.caseInsensitiveCompare("imagePath") == .orderedSame { continue }
            if value.firstMatch(of: /^image\/[0-9A-Fa-f]+$/) != nil { continue }
            guard seen.insert(value.lowercased()).inserted else { continue }
            result.append(value)
        }
        return result
    }
}

struct DayMenu: Codable, Hashable, Sendable, Identifiable {
    var date: String
    var day: String
    var menues: [MenuItem]

    var id: String { "\(day)|\(date)" }

    var dayIndex: Int? {
        DayResolver.parseDay(day, language: .de) ?? DayResolver.parseDay(day, language: .en)
    }

    var resolvedDate: Date? {
        DayResolver.resolve(self, language: .de) ?? DayResolver.resolve(self, language: .en)
    }

    var isToday: Bool {
        DayResolver.isToday(self, language: .de) || DayResolver.isToday(self, language: .en)
    }

    enum CodingKeys: String, CodingKey {
        case date, day, menues
    }
}

struct HistoryEntry: Codable, Hashable, Sendable, Identifiable {
    var restaurant: Restaurant
    var year: Int
    var month: Int
    var week: Int
    var data: [DayMenu]

    var yearMonth: String { String(format: "%04d-%02d", year, month) }

    var id: String { "\(restaurant.rawValue)-\(year)-\(month)-\(week)" }

    enum CodingKeys: String, CodingKey {
        case restaurant, year, month, week, data
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let raw = (try container.decodeIfPresent(String.self, forKey: .restaurant) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        guard let restaurant = Restaurant(rawValue: raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: .restaurant,
                in: container,
                debugDescription: "Unknown restaurant: \(raw)"
            )
        }
        self.restaurant = restaurant
        year = try container.decodeIfPresent(Int.self, forKey: .year) ?? 0
        month = try container.decodeIfPresent(Int.self, forKey: .month) ?? 0
        week = try container.decodeIfPresent(Int.self, forKey: .week) ?? 0
        data = try container.decodeIfPresent([DayMenu].self, forKey: .data) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(restaurant, forKey: .restaurant)
        try container.encode(year, forKey: .year)
        try container.encode(month, forKey: .month)
        try container.encode(week, forKey: .week)
        try container.encode(data, forKey: .data)
    }
}

struct APIStatus: Codable, Sendable {
    var version: String
    var features: Features
    var restaurants: [String]

    struct Features: Codable, Sendable {
        var imageGeneration: Bool?
        var menuTranslation: Bool?
        var autoUpdate: Bool?
        var pdfScraping: Bool?
        var updateInterval: String?
    }
}
