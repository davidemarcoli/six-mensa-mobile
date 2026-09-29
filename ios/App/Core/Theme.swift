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

    func resolvedColorScheme(system: ColorScheme) -> ColorScheme {
        switch self {
        case .system:
            return system
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

/// Whether the user enabled increased text contrast in Settings.
private struct HighContrastKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var highContrast: Bool {
        get { self[HighContrastKey.self] }
        set { self[HighContrastKey.self] = newValue }
    }
}

/// Secondary/tertiary text levels that get pushed toward full contrast when
/// the accessibility "High contrast" switch is enabled. When disabled the
/// styles resolve exactly to the system defaults.
enum AppTextEmphasis {
    case secondary
    case tertiary

    func style(highContrast: Bool) -> AnyShapeStyle {
        switch self {
        case .secondary:
            return highContrast ? AnyShapeStyle(Color.primary) : AnyShapeStyle(HierarchicalShapeStyle.secondary)
        case .tertiary:
            return highContrast ? AnyShapeStyle(Color.primary.opacity(0.85)) : AnyShapeStyle(HierarchicalShapeStyle.tertiary)
        }
    }
}

private struct AppForegroundStyleModifier: ViewModifier {
    @Environment(\.highContrast) private var highContrast
    let emphasis: AppTextEmphasis

    func body(content: Content) -> some View {
        content.foregroundStyle(emphasis.style(highContrast: highContrast))
    }
}

extension View {
    func appForegroundStyle(_ emphasis: AppTextEmphasis) -> some View {
        modifier(AppForegroundStyleModifier(emphasis: emphasis))
    }
}
