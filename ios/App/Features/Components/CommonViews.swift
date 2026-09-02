import SwiftUI
import UIKit

struct SectionHeader: View {
    private let text: String
    @Environment(SettingsStore.self) private var settings

    init(_ key: String) {
        text = NSLocalizedString(key, comment: "")
    }

    var body: some View {
        Text(text)
            .font(.footnote.weight(.semibold))
            .textCase(.uppercase)
            .kerning(0.5)
            .foregroundStyle(settings.accentColor)
    }
}

// Usage: ChoiceChips(items: [("All", nil), ("HTP", .htp)], selection: $filter.restaurant) — selection type must match the item values.
struct ChoiceChips<T: Hashable>: View {
    let items: [(label: String, value: T)]
    @Binding var selection: T
    @Environment(SettingsStore.self) private var settings

    var body: some View {
        HStack(spacing: 8) {
            ForEach(items.indices, id: \.self) { index in
                let item = items[index]
                let isSelected = item.value == selection
                Button {
                    selection = item.value
                } label: {
                    Text(item.label)
                        .font(.subheadline.weight(isSelected ? .semibold : .regular))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Capsule().fill(isSelected ? settings.accentColor : Color(uiColor: .secondarySystemFill)))
                        .foregroundStyle(isSelected ? Color.white : .primary)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct LoadingView: View {
    var body: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
            Text(message)
                .font(.subheadline)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(.red)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.red.opacity(0.12)))
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let titleKey: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.largeTitle)
                .foregroundStyle(.tertiary)
            Text(NSLocalizedString(titleKey, comment: ""))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
