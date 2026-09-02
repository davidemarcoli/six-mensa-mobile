import SwiftUI

struct DayMenuPage: View {
    let day: DayMenu
    let language: ContentLanguage
    let accent: Color
    let isToday: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("\(day.day) · \(day.date)")
                    .font(.title3.weight(isToday ? .bold : .regular))
                    .foregroundStyle(isToday ? accent : Color.primary)
                if day.menues.isEmpty {
                    EmptyStateView(systemImage: "fork.knife", titleKey: "dish.none")
                        .frame(maxHeight: 240)
                } else {
                    VStack(spacing: 10) {
                        ForEach(day.menues) { item in
                            MenuItemCard(item: item, language: language, accent: accent)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
    }
}
