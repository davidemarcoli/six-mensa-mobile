import SwiftUI
import UIKit

struct MenuView: View {
    @Environment(MenuStore.self) private var store
    @Environment(SettingsStore.self) private var settings
    @Environment(AppModel.self) private var model

    private var safeSelectedDayIndex: Int {
        guard !store.week.isEmpty else { return 0 }
        return min(store.selectedDayIndex, store.week.count - 1)
    }

    private var selectedDayBinding: Binding<Int> {
        Binding(
            get: { safeSelectedDayIndex },
            set: { store.selectedDayIndex = min($0, max(store.week.count - 1, 0)) }
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                restaurantPicker
                if !store.week.isEmpty {
                    dayChips
                }
                if let updated = store.lastUpdated {
                    Text(String(format: NSLocalizedString("menu.updated", comment: ""), DayResolver.relativeUpdateLabel(since: updated)))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }
                if let err = store.errorMessage {
                    // MenuStore already formats stale failures into a full sentence; render as-is.
                    HStack(spacing: 8) {
                        Image(systemName: "wifi.exclamationmark")
                        Text(err)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .font(.footnote)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color(uiColor: .systemRed).opacity(0.12)))
                    .padding(.horizontal)
                    .padding(.top, 8)
                }
                menuContent
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .toolbar { toolbarContent }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            if let day = store.currentDay {
                ShareLink(item: store.shareText(for: day)) {
                    Label("share.title", systemImage: "square.and.arrow.up")
                }
            }
        }
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Button {
                    model.pdfRestaurant = .htp
                } label: {
                    Text(String(format: NSLocalizedString("pdf.title", comment: ""), Restaurant.htp.displayName))
                }
                Button {
                    model.pdfRestaurant = .ht201
                } label: {
                    Text(String(format: NSLocalizedString("pdf.title", comment: ""), Restaurant.ht201.displayName))
                }
            } label: {
                Image(systemName: "ellipsis")
            }
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button {
                model.showSettings = true
            } label: {
                Label("settings.title", systemImage: "gearshape")
            }
        }
    }

    private var restaurantPicker: some View {
        Picker(
            "",
            selection: Binding(
                get: { store.restaurant },
                set: { newValue in Task { await store.selectRestaurant(newValue) } }
            )
        ) {
            ForEach(Restaurant.allCases) { restaurant in
                Text(restaurant.displayName).tag(restaurant)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.top, 8)
    }

    private var dayChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(store.week.indices, id: \.self) { i in
                    let selected = i == safeSelectedDayIndex
                    VStack(spacing: 2) {
                        Text(store.week[i].day)
                            .font(.footnote.weight(selected ? .bold : .regular))
                        Text(store.week[i].date)
                            .font(.caption2)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(selected ? settings.accentColor : Color(uiColor: .secondarySystemBackground)))
                    .overlay {
                        if i == store.todayIndex && !selected {
                            Capsule().strokeBorder(settings.accentColor, lineWidth: 1)
                        }
                    }
                    .foregroundStyle(selected ? Color.white : Color.primary)
                    .contentShape(Capsule())
                    .onTapGesture { store.selectedDayIndex = i }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
        }
    }

    @ViewBuilder
    private var menuContent: some View {
        if store.week.isEmpty && store.state == .loading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if store.week.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "fork.knife")
                    .font(.largeTitle)
                    .foregroundStyle(.tertiary)
                Text("dish.none")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            TabView(selection: selectedDayBinding) {
                ForEach(store.week.indices, id: \.self) { i in
                    DayMenuPage(
                        day: store.week[i],
                        language: settings.language,
                        accent: settings.accentColor,
                        isToday: i == store.todayIndex
                    )
                    .refreshable { await store.refresh(force: true) }
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
        }
    }
}
