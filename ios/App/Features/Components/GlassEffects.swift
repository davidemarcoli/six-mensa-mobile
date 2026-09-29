import SwiftUI

/// A glass-styled capsule chip, falling back to the original capsule on iOS < 26.
struct GlassChip: View {
    let label: String
    let isSelected: Bool
    let accent: Color
    let action: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
        }
        .buttonStyle(.plain)
        .background {
            if #available(iOS 26.0, *), colorScheme == .light {
                Capsule()
                    .glassEffect(.regular, in: .capsule)
            } else {
                Capsule().fill(isSelected ? accent : Color(uiColor: .secondarySystemBackground))
            }
        }
        .foregroundStyle(isSelected ? Color.white : Color.primary)
        .clipShape(Capsule())
    }
}

/// A glass-styled card background container.
struct GlassCard<Content: View>: View {
    var cornerRadius: CGFloat = 14
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        content()
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                cardSurface(cornerRadius: cornerRadius, colorScheme: colorScheme)
            }
    }
}

/// Shared card surface: in light mode the Liquid Glass card gets a subtle
/// white wash so it reads lighter against the grouped background; other
/// configurations fall back to the plain system card colour.
@ViewBuilder
func cardSurface(cornerRadius: CGFloat, colorScheme: ColorScheme) -> some View {
    let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
    if #available(iOS 26.0, *), colorScheme == .light {
        shape
            .glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
            .overlay {
                shape.fill(Color.white.opacity(0.25))
            }
    } else {
        shape.fill(Color(uiColor: .secondarySystemGroupedBackground))
    }
}

// MARK: - View extension for conditional modification

extension View {
    /// Applies `transform` only on iOS 26+ (Liquid Glass support).
    @ViewBuilder
    func `ifIOS26`<Content: View>(@ViewBuilder transform: (Self) -> Content) -> some View {
        if #available(iOS 26.0, *) {
            transform(self)
        } else {
            self
        }
    }

    /// Fills the background with a glass material card that degrades to a plain
    /// system card in dark mode / pre-iOS 26. Used by item and stats cards.
    func glassCardBackground(cornerRadius: CGFloat = 14) -> some View {
        modifier(GlassCardBackground(cornerRadius: cornerRadius))
    }
}

private struct GlassCardBackground: ViewModifier {
    var cornerRadius: CGFloat

    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.background {
            cardSurface(cornerRadius: cornerRadius, colorScheme: colorScheme)
        }
    }
}
