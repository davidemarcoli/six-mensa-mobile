import CoreLocation
import Foundation

/// Monitors a geofence around each SIX office. While the user is inside a circle
/// the daily menu notification stays armed (fires at the chosen time); leaving the
/// office disarms it. Together this means the notification only fires at the user's
/// chosen time while they are actually in the office.
final class OfficeGeofence: NSObject, CLLocationManagerDelegate {

    static let shared = OfficeGeofence()

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.activityType = .other
    }

    /// Requests Always authorization, needed for region monitoring to keep
    /// running while the app is backgrounded. Also called on "while using" so
    /// iOS can offer the upgrade to Always on a subsequent visit.
    func requestAuthorization() {
        switch manager.authorizationStatus {
        case .notDetermined, .authorizedWhenInUse:
            manager.requestAlwaysAuthorization()
        default:
            break
        }
    }

    /// Starts or stops monitoring the office circles based on current settings.
    func refreshMonitoring(_ settings: SettingsStore) {
        guard settings.notificationsEnabled, settings.notificationsOnlyInOffice else {
            stopMonitoring()
            return
        }
        requestAuthorization()
        guard manager.authorizationStatus == .authorizedAlways
            || manager.authorizationStatus == .authorizedWhenInUse else {
            return
        }
        startMonitoring(radiusMeters: settings.officeRadiusMeters)
    }

    private func startMonitoring(radiusMeters: Double) {
        let currentIDs = Set(manager.monitoredRegions.compactMap { $0.identifier })

        for site in WorkLocations.all {
            let id = site.name
            guard !currentIDs.contains(id) else { continue }
            let region = CLCircularRegion(
                center: CLLocationCoordinate2D(latitude: site.latitude, longitude: site.longitude),
                radius: radiusMeters,
                identifier: id
            )
            region.notifyOnEntry = true
            region.notifyOnExit = true
            manager.startMonitoring(for: region)
        }
    }

    private func stopMonitoring() {
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        Task { @MainActor in
            switch state {
            case .inside:
                await NotificationManager.armDaily()
            case .outside:
                await NotificationManager.disarm()
            default:
                break
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        Task { @MainActor in await NotificationManager.armDaily() }
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        Task { @MainActor in await NotificationManager.disarm() }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        AppLog.main.error("Geofence error: \(error.localizedDescription)")
    }
}