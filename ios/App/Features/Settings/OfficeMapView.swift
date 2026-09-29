import MapKit
import SwiftUI
import CoreLocation

/// A map card showing the two SIX office circles in the accent color, with
/// `+` / `−` buttons that resize both circles together.
///
/// Uses a cached `MKMapSnapshotter` image (pre-rendered once) instead of a live
/// interactive `Map`, so opening Settings never stalls or warms up MapKit on the
/// main thread. Resizing with `+` / `−` only redraws the circle overlays.
struct OfficeMapCard: View {
    @Environment(SettingsStore.self) private var settings
    @State private var authStatus: CLAuthorizationStatus = CLLocationManager().authorizationStatus

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("settings.office.map")
                .font(.headline)

            OfficeSnapshotView(
                radiusMeters: settings.officeRadiusMeters
            )
            .frame(height: 240)
            .clipShape(RoundedRectangle(cornerRadius: 12))

            OfficeLocationStatus(status: authStatus)

            VStack(spacing: 2) {
                Text(String(format: NSLocalizedString("settings.office.radius", comment: ""), Int(settings.officeRadiusMeters)))
                    .font(.subheadline)
                Text("settings.office.radius.sub")
                    .font(.caption)
                    .appForegroundStyle(.secondary)
            }

            HStack {
                Spacer()
                Button {
                    adjustRadius(by: -50)
                } label: {
                    Image(systemName: "minus")
                        .frame(width: 44, height: 44)
                        .background(.thinMaterial)
                        .clipShape(Circle())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(NSLocalizedString("settings.office.smaller", comment: ""))
                .disabled(settings.officeRadiusMeters <= 100)

                Button {
                    adjustRadius(by: 50)
                } label: {
                    Image(systemName: "plus")
                        .frame(width: 44, height: 44)
                        .background(.thinMaterial)
                        .clipShape(Circle())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(NSLocalizedString("settings.office.bigger", comment: ""))
                .disabled(settings.officeRadiusMeters >= 2000)
                Spacer()
            }
        }
        .padding()
    }

    private func adjustRadius(by delta: Double) {
        settings.officeRadiusMeters = min(max(settings.officeRadiusMeters + delta, 100), 2000)
    }
}

/// A pre-rendered map image of the office area with the two accent-colored
/// office circles overlaid.
private struct OfficeSnapshotView: View {
    @Environment(SettingsStore.self) private var settings
    let radiusMeters: Double

    /// Loaded once and reused so the card never re-runs MapKit on the main thread.
    @State private var snapshot: UIImage?

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            Group {
                if let snapshot, snapshot.size == size {
                    Image(uiImage: snapshot)
                        .resizable()
                        .overlay(circleOverlay(in: size))
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .task(id: size) {
                guard size.width > 0, size.height > 0 else { return }
                await loadSnapshot(size: size)
            }
        }
        .background(Color(uiColor: .secondarySystemBackground))
    }

    private func loadSnapshot(size: CGSize) async {
        snapshot = await OfficeSnapshotLoader.shared.load(size: size)
    }

    /// Draws both circles at the correct pixel positions + scale for the radius.
    private func circleOverlay(in size: CGSize) -> some View {
        Canvas { context, canvasSize in
            let accent = settings.accentColor

            for site in WorkLocations.all {
                guard let center = OfficeSnapshotLoader.point(for: site, in: canvasSize) else { continue }

                let radiusPixels = radiusMeters * OfficeSnapshotLoader.pixelsPerMeter(in: canvasSize)

                let bounds = CGRect(
                    x: center.x - radiusPixels,
                    y: center.y - radiusPixels,
                    width: radiusPixels * 2,
                    height: radiusPixels * 2
                )

                context.fill(Path(ellipseIn: bounds), with: .color(accent.opacity(0.18)))
                context.stroke(Path(ellipseIn: bounds), with: .color(accent), lineWidth: 2)
            }
        }
        .allowsHitTesting(false)
        .overlay(markersOverlay(in: size))
    }

    /// Small building markers at each office centre, tinted with the accent color.
    private func markersOverlay(in size: CGSize) -> some View {
        ZStack {
            ForEach(WorkLocations.all, id: \.name) { site in
                if let point = OfficeSnapshotLoader.point(for: site, in: size) {
                    Image(systemName: "building.2.fill")
                        .foregroundStyle(settings.accentColor)
                        .font(.title3)
                        .padding(6)
                        .background(.regularMaterial, in: Circle())
                        .position(point)
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// Loads (and caches) an `MKMapSnapshotter` image of the office region, and maps
/// coordinates to pixel positions on that image.
private enum OfficeSnapshotLoader {

    static let region: MKCoordinateRegion = {
        let sites = WorkLocations.all
        let minLat = sites.map(\.latitude).min()!
        let maxLat = sites.map(\.latitude).max()!
        let minLon = sites.map(\.longitude).min()!
        let maxLon = sites.map(\.longitude).max()!

        let latSpan = maxLat - minLat
        let lonSpan = maxLon - minLon

        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2),
            span: MKCoordinateSpan(latitudeDelta: max(latSpan * 2.1, 0.008), longitudeDelta: max(lonSpan * 2.1, 0.008))
        )
    }()

    /// Small cache so the card never re-runs MapKit across re-appearances.
    final class Loader {
        var cache: UIImage?

        func load(size: CGSize) async -> UIImage? {
            if let cache, cache.size == size { return cache }
            let options = MKMapSnapshotter.Options()
            options.region = OfficeSnapshotLoader.region
            options.size = size
            options.mapType = .standard
            let snapshotter = MKMapSnapshotter(options: options)
            guard let snapshot = try? await snapshotter.start() else { return nil }
            cache = snapshot.image
            return snapshot.image
        }
    }

    static let shared = Loader()

    /// Longitude → x and latitude → y, linear over the small region, scaled to
    /// the image size.
    static func point(for site: WorkLocations.Site, in size: CGSize) -> CGPoint? {
        guard size.width > 0, size.height > 0, region.span.latitudeDelta > 0 else { return nil }

        let minLat = region.center.latitude - region.span.latitudeDelta / 2
        let minLon = region.center.longitude - region.span.longitudeDelta / 2
        let maxLat = region.center.latitude + region.span.latitudeDelta / 2
        let maxLon = region.center.longitude + region.span.longitudeDelta / 2

        guard maxLon > minLon, maxLat > minLat else { return nil }

        let x = (site.longitude - minLon) / (maxLon - minLon) * size.width
        let y = (1 - (site.latitude - minLat) / (maxLat - minLat)) * size.height

        return CGPoint(x: x, y: y)
    }

    static func pixelsPerMeter(in size: CGSize) -> Double {
        let metersPerDegreeLat = 111_320.0
        let metersPerPixel = region.span.latitudeDelta * metersPerDegreeLat / size.height
        return 1.0 / metersPerPixel
    }
}

/// Shows the current location authorization and, when not "Always", guides the
/// user to enable it so background geofencing keeps working.
private struct OfficeLocationStatus: View {
    @Environment(SettingsStore.self) private var settings
    let status: CLAuthorizationStatus

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(settings.accentColor)
            Text(message)
                .font(.caption)
                .appForegroundStyle(.secondary)
            Spacer()
            if needsSettings {
                Button {
                    OfficeGeofence.shared.requestAuthorization()
                } label: {
                    Text("settings.office.enable")
                        .font(.caption)
                }
                Button {
                    openSettings()
                } label: {
                    Image(systemName: "arrow.up.right.square")
                        .font(.caption)
                }
            }
        }
        .padding(8)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var icon: String {
        switch status {
        case .authorizedAlways: return "checkmark.circle.fill"
        case .authorizedWhenInUse: return "location.fill"
        case .denied, .restricted: return "xmark.circle.fill"
        default: return "location"
        }
    }

    private var message: String {
        switch status {
        case .authorizedAlways: return NSLocalizedString("settings.office.status.always", comment: "")
        case .authorizedWhenInUse: return NSLocalizedString("settings.office.status.inuse", comment: "")
        case .denied, .restricted: return NSLocalizedString("settings.office.status.denied", comment: "")
        default: return NSLocalizedString("settings.office.status.none", comment: "")
        }
    }

    private var needsSettings: Bool {
        status != .authorizedAlways
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}