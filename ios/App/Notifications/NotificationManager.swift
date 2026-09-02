import Foundation
import UserNotifications

enum NotificationManager {
    static let notificationID = 1001

    private static let identifier = String(notificationID)

    static func requestAuthorization() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    static func authorizationStatus() async -> UNAuthorizationStatus {
        (try? await UNUserNotificationCenter.current().notificationSettings())?.authorizationStatus ?? .notDetermined
    }

    static func scheduleDaily(hour: Int, minute: Int, restaurant: Restaurant, language: ContentLanguage) async -> Date {
        let fireDate = DayResolver.nextWeekdayOccurrence(after: Date(), hour: hour, minute: minute)

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = DayResolver.zurich
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate),
            repeats: false
        )

        let body = await summaryBody(restaurant: restaurant, language: language)
        let content = makeContent(title: restaurant.displayName, restaurant: restaurant, body: body)
        await add(content, with: trigger)

        SettingsStore().scheduledNotificationAt = fireDate
        return fireDate
    }

    static func cancel() async {
        try? await UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [identifier])
    }

    static func rescheduleIfStale(_ settings: SettingsStore) async {
        guard settings.notificationsEnabled,
              let scheduled = settings.scheduledNotificationAt,
              scheduled <= Date()
        else { return }
        _ = await scheduleDaily(
            hour: settings.notificationHour,
            minute: settings.notificationMinute,
            restaurant: settings.standardRestaurant,
            language: settings.language
        )
    }

    static func postTodayNow(restaurant: Restaurant, language: ContentLanguage) async {
        let body = await summaryBody(restaurant: restaurant, language: language)
        let content = makeContent(title: restaurant.displayName, restaurant: restaurant, body: body)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        await add(content, with: trigger)
    }

    private static func makeContent(title: String, restaurant: Restaurant, body: String) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = String(format: NSLocalizedString("notification.title", comment: ""), title)
        content.body = body
        content.sound = .default
        content.userInfo = ["restaurant": restaurant.rawValue, "destination": "menu"]
        return content
    }

    private static func add(_ content: UNMutableNotificationContent, with trigger: NotificationTrigger) async {
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
        let center = UNUserNotificationCenter.current()
        try? await center.removePendingNotificationRequests(withIdentifiers: [identifier])
        try? await center.add(request)
    }

    private static func summaryBody(restaurant: Restaurant, language: ContentLanguage) async -> String {
        guard let day = await fetchToday(restaurant: restaurant, language: language) else {
            return ""
        }
        return ShareText.summaryLines(day: day, language: language).joined(separator: "\n")
    }

    private static func fetchToday(restaurant: Restaurant, language: ContentLanguage) async -> DayMenu? {
        var days: [DayMenu]?
        if let fetched = try? await MenuAPI().week(restaurant: restaurant, language: language) {
            days = fetched
        }
        if days == nil {
            days = await JSONCache().read(
                JSONCache.weekKey(restaurant: restaurant, language: language),
                ttl: .infinity
            )
        }
        guard let days else { return nil }
        return days.first { DayResolver.isToday($0, language: language) }
    }
}
