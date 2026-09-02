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
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
            Text(item.title)
                .font(.headline)
            if !item.description.isEmpty {
                Text(item.description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if let origin = item.origin, !origin.isEmpty {
                Text("(\(origin))")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
            if !item.allergens.isEmpty {
                Text("\(NSLocalizedString("allergen.label", comment: "")): \(item.allergens.joined(separator: ", "))")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
