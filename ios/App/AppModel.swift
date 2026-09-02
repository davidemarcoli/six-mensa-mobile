import Observation
import UserNotifications

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
        UNUserNotificationCenter.current().delegate = self
        if settings.notificationsEnabled {
            await NotificationManager.rescheduleIfStale(settings)
        }
        Task { await menuStore.load() }
        Task { await historyStore.loadStatus() }
    }

    func onForeground() async {
        await NotificationManager.rescheduleIfStale(settings)
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
