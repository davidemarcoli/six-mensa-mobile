import Foundation

/// The two SIX buildings the canteens belong to, kept free of UI types so the
/// geometry is testable. Mirrors Android `WorkLocations`.
enum WorkLocations {
    struct Site {
        let name: String
        let latitude: Double
        let longitude: Double
    }

    /// 47°23'34.8"N 8°30'33.5"E
    static let hardturmstrasse201 = Site(name: "Hardturmstrasse 201", latitude: 47.393000, longitude: 8.509306)

    /// 47°23'29.9"N 8°30'25.5"E
    static let pfingstweidstrasse110 = Site(name: "Pfingstweidstrasse 110", latitude: 47.391639, longitude: 8.507083)

    static let all = [hardturmstrasse201, pfingstweidstrasse110]

    /// The two buildings are about 225 m apart, so this comfortably covers both
    /// plus the walk between them, while still excluding home or the commute.
    static let defaultRadiusMeters: Double = 200

    private static let earthRadiusMeters: Double = 6_371_008.8

    /// Haversine great-circle distance.
    static func distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ) -> Double {
        func radians(_ degrees: Double) -> Double { degrees * .pi / 180 }

        let dLat = radians(latitude2 - latitude1)
        let dLon = radians(longitude2 - longitude1)
        let lat1 = radians(latitude1)
        let lat2 = radians(latitude2)

        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadiusMeters * asin(sqrt(a))
    }

    static func nearestSite(latitude: Double, longitude: Double) -> (site: Site, distance: Double) {
        all
            .map { ($0, distanceMeters(latitude1: latitude, longitude1: longitude, latitude2: $0.latitude, longitude2: $0.longitude)) }
            .min { $0.1 < $1.1 }!
    }

    /// Is the given location within `radiusMeters` of either building?
    static func isAtWork(latitude: Double, longitude: Double, radiusMeters: Double = defaultRadiusMeters) -> Bool {
        nearestSite(latitude: latitude, longitude: longitude).distance <= radiusMeters
    }
}