import SwiftUI

enum Restaurant: String, CaseIterable, Codable, Sendable, Identifiable, Hashable {
    case htp, ht201

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .htp:
            return "HTP"
        case .ht201:
            return "HT 201"
        }
    }
}

enum ContentLanguage: String, CaseIterable, Codable, Sendable, Identifiable {
    case de = "de", en = "en"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .de:
            return String(localized: "language.german")
        case .en:
            return String(localized: "language.english")
        }
    }

    var locale: Locale {
        switch self {
        case .de:
            return Locale(identifier: "de-DE")
        case .en:
            return Locale(identifier: "en-US")
        }
    }

    static func fromDeviceLocale() -> ContentLanguage {
        Locale.current.language.languageCode?.identifier == "de" ? .de : .en
    }
}
