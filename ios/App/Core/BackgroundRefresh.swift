import Foundation
import BackgroundTasks
import WidgetKit

enum BackgroundRefresh {

    static let identifier = "dev.davidemarcoli.mensa.background-refresh"

    private static let maxInterval: TimeInterval = 6 * 3600

    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: identifier,
            using: nil
        ) { task in
            handle(task: task as? BGAppRefreshTask)
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: maxInterval)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
        }
    }

    @MainActor
    static func refreshNow() async {
        await refresh()
        schedule()
    }

    private static func handle(task: BGAppRefreshTask?) {
        let operation = Task { @MainActor in
            await refresh()
        }
        task?.expirationHandler = {
            AppLog.refresh.warning("Background refresh expired before completing.")
            operation.cancel()
        }
        Task {
            await operation.value
            schedule()
            task?.setTaskCompleted(success: true)
            AppLog.refresh.info("Background refresh completed.")
        }
    }

    @MainActor
    private static func refresh() async {
        let settings = SettingsStore()
        let menuStore = MenuStore(settings: settings)
        await menuStore.load()
        try? await JSONCache().pruneHistory()
        await NotificationManager.rescheduleIfStale(settings)
        WidgetCenter.shared.reloadAllTimelines()
    }
}