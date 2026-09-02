import SwiftUI

enum ThemeMode: String, CaseIterable, Codable, Sendable, Identifiable {
    case system = "SYSTEM", light = "LIGHT", dark = "DARK"

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }

    var displayName: String {
        switch self {
        case .system:
            return String(localized: "theme.system")
        case .light:
            return String(localized: "theme.light")
        case .dark:
            return String(localized: "theme.dark")
        }
    }
}

extension Color {
    static let brandRed: Color = Color(.sRGB, red: Double(0xDE) / 255, green: Double(0x39) / 255, blue: Double(0x19) / 255, opacity: 1)
}
