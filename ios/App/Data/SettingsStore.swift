import Foundation
import Observation
import SwiftUI

@Observable
final class SettingsStore {

    private enum Keys {
        static let language = "content_language"
        static let restaurant = "restaurant"
        static let themeMode = "theme_mode"
        static let seedColor = "seed_color"
        static let notificationsEnabled = "notifications_enabled"
        static let notificationHour = "notification_hour"
        static let notificationMinute = "notification_minute"
        static let scheduledAt = "notification_scheduled_at"
    }

    private let defaults: UserDefaults

    init(suiteName: String? = JSONCache.appGroupID) {
        defaults = suiteName.flatMap { UserDefaults(suiteName: $0) } ?? .standard
    }

    var language: ContentLanguage {
        get {
            if let raw = defaults.string(forKey: Keys.language), let value = ContentLanguage(rawValue: raw) {
                return value
            }
            return ContentLanguage.fromDeviceLocale()
        }
        set { defaults.set(newValue.rawValue, forKey: Keys.language) }
    }

    var standardRestaurant: Restaurant {
        get {
            if let raw = defaults.string(forKey: Keys.restaurant), let value = Restaurant(rawValue: raw) {
                return value
            }
            return .htp
        }
        set { defaults.set(newValue.rawValue, forKey: Keys.restaurant) }
    }

    var themeMode: ThemeMode {
        get {
            if let raw = defaults.string(forKey: Keys.themeMode), let value = ThemeMode(rawValue: raw) {
                return value
            }
            return .system
        }
        set { defaults.set(newValue.rawValue, forKey: Keys.themeMode) }
    }

    var accentColorHex: String {
        get { defaults.string(forKey: Keys.seedColor) ?? "DE3919" }
        set { defaults.set(newValue, forKey: Keys.seedColor) }
    }

    var accentColor: Color {
        var value: UInt64?
        guard Scanner(string: accentColorHex).scanHexInt64(&value), let hex = value else {
            return .brandRed
        }
        return Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    var notificationsEnabled: Bool {
        get {
            // UserDefaults.bool defaults to false for missing keys; the app default is true.
            guard let stored = defaults.object(forKey: Keys.notificationsEnabled) as? Bool else {
                return true
            }
            return stored
        }
        set { defaults.set(newValue, forKey: Keys.notificationsEnabled) }
    }

    var notificationHour: Int {
        get {
            defaults.object(forKey: Keys.notificationHour) as? Int ?? 10
        }
        set { defaults.set(newValue, forKey: Keys.notificationHour) }
    }

    var notificationMinute: Int {
        get {
            defaults.object(forKey: Keys.notificationMinute) as? Int ?? 30
        }
        set { defaults.set(newValue, forKey: Keys.notificationMinute) }
    }

    var notificationTimeComponents: DateComponents {
        DateComponents(hour: notificationHour, minute: notificationMinute)
    }

    var scheduledNotificationAt: Date? {
        get {
            guard let stored = defaults.object(forKey: Keys.scheduledAt) as? Double, stored > 0 else {
                return nil
            }
            return Date(timeIntervalSince1970: stored)
        }
        set {
            if let newValue {
                defaults.set(newValue.timeIntervalSince1970, forKey: Keys.scheduledAt)
            } else {
                defaults.removeObject(forKey: Keys.scheduledAt)
            }
        }
    }

    static let accentPresets: [String] = [
        "DE3919", "1B6EF3", "00897B", "43A047", "8E24AA", "F9A825", "D81B60", "5D4037",
    ]

    func resetAccent() {
        accentColorHex = "DE3919"
    }
}
