import SwiftUI
import UIKit

struct MenuItemCard: View {
    let item: MenuItem
    let language: ContentLanguage
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(item.displayType)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(accent)
                if item.dietaryType.isVegan {
                    Image(systemName: "leaf.fill")
                        .foregroundStyle(.green)
                }
                Spacer()
                if let price = item.price, price.hasAny {
                    Text(ShareText.priceText(price, language: language))
                        .font(.subheadline)
                        .appForegroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
            Text(item.title)
                .font(.headline)
            if !item.description.isEmpty {
                Text(item.description)
                    .font(.subheadline)
                    .appForegroundStyle(.secondary)
            }
            if let origin = item.origin, !origin.isEmpty {
                Text("(\(origin))")
                    .font(.footnote)
                    .appForegroundStyle(.tertiary)
            }
            if !item.allergens.isEmpty {
                Text("\(NSLocalizedString("allergen.label", comment: "")): \(item.allergens.joined(separator: ", "))")
                    .font(.footnote)
                    .appForegroundStyle(.tertiary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCardBackground(cornerRadius: 14)
    }
}
