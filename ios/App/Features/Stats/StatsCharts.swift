import SwiftUI
import Charts

struct PriceTrendChart: View {
    let months: [String]
    let series: [PriceTrendSeries]
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Chart {
                ForEach(series) { s in
                    ForEach(s.points) { p in
                        LineMark(
                            x: .value("Month", p.month),
                            y: .value("CHF", p.value),
                            series: .value("Series", s.id)
                        )
                    }
                    .foregroundStyle(s.isOverall ? accent : Color.gray.opacity(0.6))
                    .lineStyle(StrokeStyle(lineWidth: s.isOverall ? 2.5 : 1.5, dash: s.isOverall ? [] : [4, 3]))
                    .interpolationMethod(.catmullRom)
                }
            }
            .chartLegend(.hidden)
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 6))
            }
            .frame(height: 220)

            if series.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(series) { s in
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(s.isOverall ? accent : Color.gray.opacity(0.6))
                                    .frame(width: 8, height: 8)
                                Text(s.isOverall ? NSLocalizedString("stats.overall", comment: "") : s.type)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

struct DietaryDonut: View {
    let counts: [DietaryCount]
    let accent: Color

    private var total: Int {
        counts.reduce(0) { $0 + $1.count }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Chart(counts) { c in
                SectorMark(
                    angle: .value("Count", c.count),
                    innerRadius: .ratio(0.62),
                    angularInset: 1
                )
                .foregroundStyle(color(for: c.type))
                .cornerRadius(4)
            }
            .chartLegend(.hidden)
            .chartBackground { proxy in
                GeometryReader { geo in
                    if let anchor = proxy.plotFrame {
                        let frame = geo[anchor]
                        Text("\(total)")
                            .font(.title2.bold())
                            .monospacedDigit()
                            .position(x: frame.midX, y: frame.midY)
                    }
                }
            }
            .frame(height: 200)

            HStack(spacing: 12) {
                ForEach(counts) { c in
                    HStack(spacing: 4) {
                        Circle()
                            .fill(color(for: c.type))
                            .frame(width: 8, height: 8)
                        Text(name(for: c.type))
                            .font(.caption)
                        Text("\(c.count) (\(percent(c.count)))%")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func color(for type: DietaryType) -> Color {
        switch type {
        case .meat:
            return .red
        case .vegetarian:
            return .green
        case .vegan:
            return Color(red: 0.09, green: 0.64, blue: 0.29)
        case .unknown:
            return .gray
        }
    }

    private func name(for type: DietaryType) -> String {
        switch type {
        case .meat:
            return NSLocalizedString("stats.diet.meat", comment: "")
        case .vegetarian:
            return NSLocalizedString("stats.diet.vegetarian", comment: "")
        case .vegan:
            return NSLocalizedString("stats.diet.vegan", comment: "")
        case .unknown:
            return ""
        }
    }

    private func percent(_ count: Int) -> Int {
        total == 0 ? 0 : Int((Double(count) / Double(total) * 100).rounded())
    }
}

struct DishFrequencyCard: View {
    private enum Mode: String, CaseIterable, Identifiable {
        case chart, table
        var id: String { rawValue }
        var label: String {
            switch self {
            case .chart:
                return NSLocalizedString("stats.chart", comment: "")
            case .table:
                return NSLocalizedString("stats.table", comment: "")
            }
        }
    }

    let data: [Counted]
    let accent: Color
    @State private var mode: Mode = .chart

    init(data: [Counted], accent: Color) {
        self.data = data
        self.accent = accent
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("", selection: $mode) {
                ForEach(Mode.allCases) { m in
                    Text(m.label)
                        .tag(m)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            switch mode {
            case .chart:
                Chart(Array(data.prefix(20))) { d in
                    BarMark(
                        x: .value("Count", d.count),
                        y: .value("Dish", abbreviated(d.label))
                    )
                    .foregroundStyle(accent)
                    .cornerRadius(4)
                }
                .frame(height: 320)
            case .table:
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(Array(data.enumerated()), id: \.element.id) { index, item in
                            HStack(alignment: .top) {
                                Text(String(format: "%02d", index + 1))
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                                Text(item.label)
                                    .lineLimit(2)
                                Spacer()
                                Text("\(item.count)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 6)

                            if index < data.count - 1 {
                                Divider()
                            }
                        }
                    }
                }
                .frame(maxHeight: 380)
            }
        }
    }

    private func abbreviated(_ label: String) -> String {
        label.count > 26 ? String(label.prefix(25)) + "…" : label
    }
}

struct AllergenChart: View {
    let data: [Counted]
    let accent: Color

    var body: some View {
        Chart(data) { d in
            BarMark(
                x: .value("Count", d.count),
                y: .value("Allergen", d.label)
            )
            .foregroundStyle(.orange)
            .cornerRadius(4)
        }
        .frame(height: 320)
    }
}
