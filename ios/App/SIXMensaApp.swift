import SwiftUI
import UIKit

enum AppTab: Hashable, CaseIterable {
    case menu, compare, stats
}

@main
struct SIXMensaApp: App {
    @State private var model = AppModel()
    @State private var showLaunchScreen = true
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
            .ifIOS26 { view in
                if #available(iOS 26.0, *) {
                    view.tabBarMinimizeBehavior(.onScrollDown)
                } else {
                    view
                }
            }
            .environment(model)
            .environment(model.settings)
            .environment(model.menuStore)
            .environment(model.historyStore)
            .environment(\.highContrast, model.settings.highContrast)
            .tint(model.settings.accentColor)
            .onAppear {
                AppearanceManager.apply(model.settings.themeMode)
            }
            .onChange(of: model.settings.themeMode) { _, mode in
                AppearanceManager.apply(mode)
            }
            .sheet(isPresented: $model.showSettings) {
                SettingsView()
                    .environment(model.settings)
                    .environment(model.menuStore)
                    .environment(model.historyStore)
                    .environment(\.highContrast, model.settings.highContrast)
            }
            .fullScreenCover(item: $model.pdfRestaurant) { restaurant in
                PDFScreenView(restaurant: restaurant)
                    .environment(model.settings)
                    .environment(model.menuStore)
                    .environment(model.historyStore)
                    .environment(\.highContrast, model.settings.highContrast)
            }
            .onOpenURL { url in
                model.selectedTab = .menu
            }
            .task {
                await model.onLaunch()
            }
            .task {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                withAnimation(.easeOut(duration: 0.5)) {
                    showLaunchScreen = false
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    AppearanceManager.apply(model.settings.themeMode)
                    Task { await model.onForeground() }
                }
            }
            .overlay {
                if showLaunchScreen {
                    LaunchScreenView()
                        .transition(.opacity)
                        .zIndex(10)
                }
            }
        }
    }
}

private struct LaunchScreenView: View {
    @State private var logoAppeared = false
    @State private var creditAppeared = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.14, green: 0.06, blue: 0.03), Color(red: 0.05, green: 0.05, blue: 0.08)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                Image("Logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 132, height: 132)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .scaleEffect(logoAppeared ? 1 : 0.6)
                    .opacity(logoAppeared ? 1 : 0)

                Spacer()

                Text("Developed by Stefan Laux & Davide Marcoli")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.6))
                    .opacity(creditAppeared ? 1 : 0)
                    .padding(.bottom, 24)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                logoAppeared = true
            }
            withAnimation(.easeOut(duration: 0.5).delay(0.35)) {
                creditAppeared = true
            }
        }
    }
}

/// Applies the user's appearance selection to every window of the app.
/// Uses `UIWindow.overrideUserInterfaceStyle` because `preferredColorScheme`
/// on a sheet is buggy in SwiftUI (does not refresh a presented sheet when the
/// root appearance changes, and `.system` cannot be restored reliably).
/// `.unspecified` delegates back to the system, which handles "Follow System".
enum AppearanceManager {
    static func apply(_ mode: ThemeMode) {
        let style: UIUserInterfaceStyle
        switch mode {
        case .system: style = .unspecified
        case .light: style = .light
        case .dark: style = .dark
        }
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                window.overrideUserInterfaceStyle = style
            }
        }
    }
}
