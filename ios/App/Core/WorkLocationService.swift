import CoreLocation
import Foundation

/// One-shot location lookup used to decide whether to deliver a daily
/// notification when "notification only when in office" is enabled.
enum WorkLocationService {

    static func isAtWork(
        radiusMeters: Double = SettingsStore().officeRadiusMeters
    ) async -> Bool? {
        guard await requestAuthorizationIfNeeded() else {
            return nil
        }
        guard let coordinate = await currentCoordinate(timeout: 12) else {
            return nil
        }
        return WorkLocations.isAtWork(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            radiusMeters: radiusMeters
        )
    }

    private static func requestAuthorizationIfNeeded() async -> Bool {
        let manager = CLLocationManager()
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            return true
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
            return true
        default:
            return false
        }
    }

    /// `CLLocationUpdate.liveUpdates()` is fully async (no CLLocationManager
    /// delegate), so there is nothing to leak. We race it against a timeout so
    /// we never hang waiting for a fix with no GPS signal.
    private static func currentCoordinate(timeout: TimeInterval) async -> CLLocationCoordinate2D? {
        await withTaskGroup(of: CLLocationCoordinate2D?.self) { group in
            group.addTask {
                do {
                    for try await update in CLLocationUpdate.liveUpdates() {
                        guard let location = update.location,
                              location.horizontalAccuracy.isFinite,
                              location.horizontalAccuracy <= 200 else {
                            continue
                        }
                        return location.coordinate
                    }
                } catch {
                    return nil
                }
                return nil
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                return nil
            }

            var result: CLLocationCoordinate2D?
            for await value in group {
                result = value
                if value != nil { break }
            }
            group.cancelAll()
            return result
        }
    }
}