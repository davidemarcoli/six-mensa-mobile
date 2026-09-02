import SwiftUI
import UIKit
import WidgetKit

struct SettingsView: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(MenuStore.self) private var menuStore
    @Environment(HistoryStore.self) private var historyStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("settings.language")
                            Text("settings.language.sub").font(.caption).foregroundStyle(.secondary)
                        }
                        chipRow(
                            items: ContentLanguage.allCases.map { (label: $0.displayName, value: $0) },
                            selection: languageSelection
                        )
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("settings.restaurant")
                            Text("settings.restaurant.sub").font(.caption).foregroundStyle(.secondary)
                        }
                        chipRow(
                            items: Restaurant.allCases.map { (label: $0.displayName, value: $0) },
                            selection: restaurantSelection
                        )
                    }
                } header: {
                    Text("settings.content")
                }

                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("settings.theme")
                        chipRow(
                            items: ThemeMode.allCases.map { (label: $0.displayName, value: $0) },
                            selection: themeSelection
                        )
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("settings.accent")
                        HStack(spacing: 12) {
                            ForEach(SettingsStore.accentPresets, id: \.self) { hex in
                                Circle()
                                    .fill(Color(hexString: hex))
                                    .frame(width: 30, height: 30)
                                    .overlay {
                                        if hex == settings.accentColorHex {
                                            Image(systemName: "checkmark")
                                                .font(.caption.bold())
                                                .foregroundStyle(.white)
                                        }
                                    }
                                    .onTapGesture {
                                        settings.accentColorHex = hex
                                    }
                            }
                        }
                    }
                } header: {
                    Text("settings.appearance")
                }

                Section {
                    Toggle(isOn: notificationsEnabledBinding) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("settings.notification")
                            Text("settings.notification.sub").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    NavigationLink {
                        TimeSettingsPage(hour: settings.notificationHour, minute: settings.notificationMinute)
                    } label: {
                        HStack {
                            Text("settings.time")
                            Spacer()
                            Text(DayResolver.formatTime(hour: settings.notificationHour, minute: settings.notificationMinute))
                                .foregroundStyle(.secondary)
                        }
                    }
                    Button {
                        Task {
                            await NotificationManager.postTodayNow(
                                restaurant: settings.standardRestaurant,
                                language: settings.language
                            )
                        }
                    } label: {
                        Label(NSLocalizedString("settings.test", comment: ""), systemImage: "bell.badge")
                    }
                } header: {
                    Text("settings.notifications")
                }

                Section {
                    HStack {
                        Text("settings.version")
                        Spacer()
                        Text(appVersion).foregroundStyle(.secondary)
                    }
                    if let status = historyStore.status {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("settings.api_version")
                                Spacer()
                                Text(status.version).foregroundStyle(.secondary)
                            }
                            if let interval = status.features.updateInterval {
                                Text(String(format: NSLocalizedString("settings.refresh", comment: ""), interval))
                                    .font(.caption)
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                    Link(destination: URL(string: "https://mensa.davidemarcoli.dev")!) {
                        Text("settings.webapp")
                    }
                } header: {
                    Text("settings.about")
                }
            }
            .navigationTitle(Text("settings.title"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(NSLocalizedString("done", comment: "")) {
                        dismiss()
                    }
                }
            }
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    private var languageSelection: Binding<ContentLanguage> {
        Binding(
            get: { settings.language },
            set: { newValue in
                settings.language = newValue
                WidgetCenter.shared.reloadAllTimelines()
                Task { await menuStore.refresh(force: true) }
            }
        )
    }

    private var restaurantSelection: Binding<Restaurant> {
        Binding(
            get: { settings.standardRestaurant },
            set: { newValue in
                settings.standardRestaurant = newValue
                WidgetCenter.shared.reloadAllTimelines()
                Task { await menuStore.selectRestaurant(newValue) }
            }
        )
    }

    private var themeSelection: Binding<ThemeMode> {
        Binding(
            get: { settings.themeMode },
            set: { settings.themeMode = $0 }
        )
    }

    private var notificationsEnabledBinding: Binding<Bool> {
        Binding(
            get: { settings.notificationsEnabled },
            set: { newValue in
                if newValue {
                    Task {
                        let granted = await NotificationManager.requestAuthorization()
                        if granted {
                            settings.notificationsEnabled = true
                            await NotificationManager.scheduleDaily(
                                hour: settings.notificationHour,
                                minute: settings.notificationMinute,
                                restaurant: settings.standardRestaurant,
                                language: settings.language
                            )
                        }
                    }
                } else {
                    settings.notificationsEnabled = false
                    Task { await NotificationManager.cancel() }
                }
            }
        )
    }

    private func chipRow<V: Hashable>(items: [(label: String, value: V)], selection: Binding<V>) -> some View {
        HStack(spacing: 8) {
            ForEach(items.indices, id: \.self) { index in
                let item = items[index]
                let isSelected = selection.wrappedValue == item.value
                Button(item.label) {
                    selection.wrappedValue = item.value
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? settings.accentColor : Color(uiColor: .secondarySystemBackground))
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .clipShape(Capsule())
            }
        }
    }
}

private struct TimeSettingsPage: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(\.dismiss) private var dismiss
    @State private var time: Date

    init(hour: Int, minute: Int) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = DayResolver.zurich
        let initial = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
        _time = State(initialValue: initial)
    }

    var body: some View {
        Form {
            DatePicker("", selection: $time, displayedComponents: .hourAndMinute)
                .labelsHidden()
                .datePickerStyle(.wheel)
                .onChange(of: time) { _, newValue in
                    var calendar = Calendar(identifier: .gregorian)
                    calendar.timeZone = DayResolver.zurich
                    let components = calendar.dateComponents([.hour, .minute], from: newValue)
                    settings.notificationHour = components.hour ?? settings.notificationHour
                    settings.notificationMinute = components.minute ?? settings.notificationMinute
                    if settings.notificationsEnabled {
                        Task {
                            await NotificationManager.scheduleDaily(
                                hour: settings.notificationHour,
                                minute: settings.notificationMinute,
                                restaurant: settings.standardRestaurant,
                                language: settings.language
                            )
                        }
                    }
                }
        }
        .navigationTitle(Text("settings.time"))
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(NSLocalizedString("done", comment: "")) {
                    dismiss()
                }
            }
        }
    }
}

private extension Color {
    init(hexString: String) {
        var value: UInt64?
        guard Scanner(string: hexString).scanHexInt64(&value), let hex = value else {
            self = .brandRed
            return
        }
        self = Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
