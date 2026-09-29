import Foundation
import Observation
import SwiftUI

@Observable
final class SettingsStore {

    private enum Keys {
        static let language = "content_language"
        static let restaurant = "restaurant"
        static let themeMode = "theme_mode"
        static let highContrast = "high_contrast"
        static let seedColor = "seed_color"
        static let notificationsEnabled = "notifications_enabled"
        static let notificationHour = "notification_hour"
        static let notificationMinute = "notification_minute"
        static let scheduledAt = "notification_scheduled_at"
        static let preferredMenuType = "preferred_menu_type"
        static let reviewPromptCount = "review_prompt_count"
        static let reviewPrompted = "review_prompted"
        static let notificationsOnlyInOffice = "notifications_only_in_office"
        static let officeRadiusMeters = "office_radius_meters"
    }

    private let defaults: UserDefaults

    var language: ContentLanguage {
        didSet { defaults.set(language.rawValue, forKey: Keys.language) }
    }

    var standardRestaurant: Restaurant {
        didSet { defaults.set(standardRestaurant.rawValue, forKey: Keys.restaurant) }
    }

    var themeMode: ThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: Keys.themeMode) }
    }

    /// Accessibility switch that pushes secondary/tertiary text toward full
    /// contrast in both light and dark mode. Defaults to off.
    var highContrast: Bool {
        didSet { defaults.set(highContrast, forKey: Keys.highContrast) }
    }

    var accentColorHex: String {
        didSet { defaults.set(accentColorHex, forKey: Keys.seedColor) }
    }

    var notificationsEnabled: Bool {
        didSet { defaults.set(notificationsEnabled, forKey: Keys.notificationsEnabled) }
    }

    var notificationHour: Int {
        didSet { defaults.set(notificationHour, forKey: Keys.notificationHour) }
    }

    var notificationMinute: Int {
        didSet { defaults.set(notificationMinute, forKey: Keys.notificationMinute) }
    }

    var scheduledNotificationAt: Date? {
        didSet {
            if let scheduledNotificationAt {
                defaults.set(scheduledNotificationAt.timeIntervalSince1970, forKey: Keys.scheduledAt)
            } else {
                defaults.removeObject(forKey: Keys.scheduledAt)
            }
        }
    }

    var preferredMenuType: String? {
        didSet {
            if let preferredMenuType {
                defaults.set(preferredMenuType, forKey: Keys.preferredMenuType)
            } else {
                defaults.removeObject(forKey: Keys.preferredMenuType)
            }
        }
    }

    var notificationsOnlyInOffice: Bool {
        didSet { defaults.set(notificationsOnlyInOffice, forKey: Keys.notificationsOnlyInOffice) }
    }

    /// Radius of the office notification circle in meters. Both offices share it.
    var officeRadiusMeters: Double {
        didSet { defaults.set(officeRadiusMeters, forKey: Keys.officeRadiusMeters) }
    }

    init(suiteName: String? = JSONCache.appGroupID) {
        defaults = suiteName.flatMap { UserDefaults(suiteName: $0) } ?? .standard

        if let raw = defaults.string(forKey: Keys.language), let value = ContentLanguage(rawValue: raw) {
            language = value
        } else {
            language = ContentLanguage.fromDeviceLocale()
        }

        if let raw = defaults.string(forKey: Keys.restaurant), let value = Restaurant(rawValue: raw) {
            standardRestaurant = value
        } else {
            standardRestaurant = .htp
        }

        if let raw = defaults.string(forKey: Keys.themeMode), let value = ThemeMode(rawValue: raw) {
            themeMode = value
        } else {
            themeMode = .system
        }

        highContrast = defaults.object(forKey: Keys.highContrast) as? Bool ?? false

        accentColorHex = defaults.string(forKey: Keys.seedColor) ?? "DE3919"

        if let stored = defaults.object(forKey: Keys.notificationsEnabled) as? Bool {
            notificationsEnabled = stored
        } else {
            notificationsEnabled = false
        }

        notificationHour = defaults.object(forKey: Keys.notificationHour) as? Int ?? 10
        notificationMinute = defaults.object(forKey: Keys.notificationMinute) as? Int ?? 30

        if let stored = defaults.object(forKey: Keys.scheduledAt) as? Double, stored > 0 {
            scheduledNotificationAt = Date(timeIntervalSince1970: stored)
        } else {
            scheduledNotificationAt = nil
        }

        preferredMenuType = defaults.string(forKey: Keys.preferredMenuType)

        notificationsOnlyInOffice = defaults.object(forKey: Keys.notificationsOnlyInOffice) as? Bool ?? false
        officeRadiusMeters = defaults.object(forKey: Keys.officeRadiusMeters) as? Double ?? WorkLocations.defaultRadiusMeters
    }

    /// Tracks how often the app has been launched so we can ask for a review at a
    /// meaningful moment rather than the first run.
    var reviewPromptCount: Int {
        get { defaults.object(forKey: Keys.reviewPromptCount) as? Int ?? 0 }
        set { defaults.set(newValue, forKey: Keys.reviewPromptCount) }
    }

    var hasPromptedForReview: Bool {
        get { defaults.bool(forKey: Keys.reviewPrompted) }
        set { defaults.set(newValue, forKey: Keys.reviewPrompted) }
    }

    /// Returns true once when the launch threshold is crossed, so the caller can
    /// ask for a review a single time for this build.
    func shouldPromptForReview() -> Bool {
        guard !hasPromptedForReview else { return false }
        reviewPromptCount += 1
        guard reviewPromptCount >= 4 else { return false }
        hasPromptedForReview = true
        return true
    }

    var accentColor: Color {
        var value: UInt64 = 0
        guard Scanner(string: accentColorHex).scanHexInt64(&value) else {
            return .brandRed
        }
        return Color(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: 1
        )
    }

    var notificationTimeComponents: DateComponents {
        DateComponents(hour: notificationHour, minute: notificationMinute)
    }

    static let accentPresets: [String] = [
        "DE3919", "1B6EF3", "00897B", "43A047", "8E24AA", "F9A825", "D81B60", "5D4037",
    ]

    func resetAccent() {
        accentColorHex = "DE3919"
    }
}
