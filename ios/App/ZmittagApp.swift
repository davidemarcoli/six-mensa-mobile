import SwiftUI

enum AppTab: Hashable, CaseIterable {
    case menu, compare, stats
}

@main
struct ZmittagApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            TabView(selection: $model.selectedTab) {
                MenuView()
                    .tabItem { Label("tab_menu", systemImage: "fork.knife") }
                    .tag(AppTab.menu)
                CompareView()
                    .tabItem { Label("tab_compare", systemImage: "arrow.left.arrow.right") }
                    .tag(AppTab.compare)
                StatsView()
                    .tabItem { Label("tab_stats", systemImage: "chart.bar") }
                    .tag(AppTab.stats)
            }
            .environment(model)
            .environment(model.settings)
            .environment(model.menuStore)
            .environment(model.historyStore)
            .tint(model.settings.accentColor)
            .preferredColorScheme(model.settings.themeMode.colorScheme)
            .sheet(isPresented: $model.showSettings) {
                SettingsView()
            }
            .fullScreenCover(item: $model.pdfRestaurant) { restaurant in
                PDFScreenView(restaurant: restaurant)
            }
            .task {
                await model.onLaunch()
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    Task { await model.onForeground() }
                }
            }
        }
    }
}
