import Observation
import SwiftUI
import StoreKit
import UserNotifications
import OSLog

enum AppLog {
    static let main = Logger(subsystem: "dev.davidemarcoli.mensa", category: "app")
    static let refresh = Logger(subsystem: "dev.davidemarcoli.mensa", category: "refresh")
}

@MainActor
@Observable
final class AppModel: NSObject, UNUserNotificationCenterDelegate {
    var selectedTab: AppTab = .menu
    var pdfRestaurant: Restaurant?
    var showSettings: Bool = false
    let settings: SettingsStore
    let menuStore: MenuStore
    let historyStore: HistoryStore

    private var hasLaunched = false

    override init() {
        settings = SettingsStore()
        menuStore = MenuStore(settings: settings)
        historyStore = HistoryStore()
        super.init()
    }

    func onLaunch() async {
        guard !hasLaunched else { return }
        hasLaunched = true
        BackgroundRefresh.register()
        BackgroundRefresh.schedule()
        UNUserNotificationCenter.current().delegate = self
        OfficeGeofence.shared.refreshMonitoring(settings)
        if settings.notificationsEnabled {
            await NotificationManager.rescheduleIfStale(settings)
        }
        Task { await menuStore.load() }
        Task { await historyStore.loadStatus() }
        if settings.shouldPromptForReview() {
            await MainActor.run { SKStoreReviewController.requestReview() }
        }
    }

    func onForeground() async {
        await NotificationManager.rescheduleIfStale(settings)
        OfficeGeofence.shared.refreshMonitoring(settings)
        if settings.notificationsEnabled, settings.notificationsOnlyInOffice {
            switch await WorkLocationService.isAtWork() {
            case true:
                await NotificationManager.armDaily()
            case false:
                await NotificationManager.disarm()
            case nil:
                break
            }
        }
        BackgroundRefresh.schedule()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
        Task { await NotificationManager.rescheduleIfStale(settings) }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if (response.notification.request.content.userInfo["destination"] as? String) == "menu" {
            selectedTab = .menu
        }
        Task { await NotificationManager.rescheduleIfStale(settings) }
        completionHandler()
    }
}
