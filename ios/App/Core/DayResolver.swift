import Foundation

enum DayResolver {

    static let zurich: TimeZone = TimeZone(identifier: "Europe/Zurich")!

    // The history archive mixes de/en locales within a single record, so both locales are always indexed.
    // Names are resolved against a real anchor date (2025-01-06 is a Monday) — independent of symbol-array order.
    private static let anchorMonday: Date = {
        var components = DateComponents()
        components.year = 2025
        components.month = 1
        components.day = 6
        return makeCalendar(tz: zurich).date(from: components)!
    }()

    private static let dayNames: [String: Int] = {
        var map: [String: Int] = [:]
        for identifier in ["de-DE", "en-US"] {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: identifier)
            formatter.dateFormat = "EEEE"
            let calendar = makeCalendar(tz: zurich)
            for offset in 0..<7 {
                guard let date = calendar.date(byAdding: .day, value: offset, to: anchorMonday) else { continue }
                let key = formatter.string(from: date).trimmingCharacters(in: .whitespaces).lowercased()
                if !key.isEmpty {
                    map[key] = offset
                }
            }
        }
        return map
    }()

    private static let monthNames: [String: Int] = {
        var map: [String: Int] = [:]
        for identifier in ["de-DE", "en-US"] {
            let full = DateFormatter()
            full.locale = Locale(identifier: identifier)
            full.dateFormat = "MMMM"
            let short = DateFormatter()
            short.locale = Locale(identifier: identifier)
            short.dateFormat = "MMM"
            let calendar = makeCalendar(tz: zurich)
            for month in 1...12 {
                var components = DateComponents()
                components.year = 2025
                components.month = month
                components.day = 1
                guard let date = calendar.date(from: components) else { continue }
                for name in [full.string(from: date), short.string(from: date)] {
                    var key = name.trimmingCharacters(in: .whitespaces).lowercased()
                    if key.hasSuffix(".") {
                        key = String(key.dropLast())
                    }
                    if !key.isEmpty {
                        map[key] = month
                    }
                }
            }
        }
        return map
    }()

    static func today() -> Date {
        Date()
    }

    static func dayIndex(of date: Date, tz: TimeZone = zurich) -> Int? {
        let weekday = makeCalendar(tz: tz).component(.weekday, from: date)
        switch weekday {
        case 2...6:
            return weekday - 2
        default:
            return nil
        }
    }

    static func weekdayName(index: Int, language: ContentLanguage) -> String {
        guard (0...4).contains(index) else { return "" }
        let calendar = makeCalendar(tz: zurich)
        guard let date = calendar.date(byAdding: .day, value: index, to: anchorMonday) else { return "" }
        let formatter = DateFormatter()
        formatter.locale = language.locale
        formatter.dateFormat = "EEEE"
        return formatter.string(from: date)
    }

    static func parseDay(_ day: String, language: ContentLanguage) -> Int? {
        dayNames[day.trimmingCharacters(in: .whitespaces).lowercased()]
    }

    static func parseDate(_ date: String, month: String, language: ContentLanguage) -> Int? {
        var candidates = [month]
        candidates.append(contentsOf: date.matches(of: /\p{L}{3,}/).map { $0.output.0 })
        for candidate in candidates {
            if let value = monthNames[candidate.trimmingCharacters(in: .whitespaces).lowercased()] {
                return value
            }
        }
        return nil
    }

    private static func parseMonthDay(_ raw: String) -> (day: Int, month: Int)? {
        guard let dayMatch = raw.firstMatch(of: /\d{1,2}/), let day = Int(dayMatch.output.0) else {
            return nil
        }
        for word in raw.matches(of: /\p{L}{3,}/) {
            if let month = monthNames[word.output.0.lowercased()] {
                return (day, month)
            }
        }
        return nil
    }

    static func resolve(_ menu: DayMenu, language: ContentLanguage, today: Date = Date()) -> Date? {
        guard let monthDay = parseMonthDay(menu.date) else { return nil }
        let calendar = makeCalendar(tz: zurich)
        let todayYear = calendar.component(.year, from: today)
        var best: Date?
        var bestDistance: Int?
        for year in [todayYear - 1, todayYear, todayYear + 1] {
            var day = monthDay.day
            // Parity with java.time MonthDay.atYear: clamp Feb 29 to Feb 28 in non-leap years.
            if monthDay.month == 2, day == 29, !isLeapYear(year) {
                day = 28
            }
            var components = DateComponents()
            components.year = year
            components.month = monthDay.month
            components.day = day
            guard let candidate = calendar.date(from: components),
                  let delta = calendar.dateComponents([.day], from: candidate, to: today).day
            else {
                continue
            }
            let distance = abs(delta)
            if let bestDistance, distance >= bestDistance { continue }
            best = candidate
            bestDistance = distance
        }
        return best
    }

    static func isToday(_ menu: DayMenu, language: ContentLanguage, today: Date = Date()) -> Bool {
        if let resolved = resolve(menu, language: language, today: today) {
            return makeCalendar(tz: zurich).isDate(resolved, inSameDayAs: today)
        }
        guard let weekday = parseDay(menu.day, language: language), let todayWeekday = dayIndex(of: today) else {
            return false
        }
        return weekday == todayWeekday
    }

    static func indexForToday(in days: [DayMenu], language: ContentLanguage) -> Int {
        let today = Date()
        let calendar = makeCalendar(tz: zurich)
        for (index, menu) in days.enumerated() {
            if let resolved = resolve(menu, language: language, today: today),
               calendar.isDate(resolved, inSameDayAs: today) {
                return index
            }
        }
        if let todayWeekday = dayIndex(of: today) {
            for (index, menu) in days.enumerated() {
                if parseDay(menu.day, language: language) == todayWeekday {
                    return index
                }
            }
        }
        return -1
    }

    static func nextWeekdayOccurrence(after date: Date, hour: Int, minute: Int, tz: TimeZone = zurich) -> Date {
        let calendar = makeCalendar(tz: tz)
        var components = calendar.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = minute
        components.second = 0
        var candidate = calendar.date(from: components) ?? date
        if candidate <= date {
            candidate = calendar.date(byAdding: .day, value: 1, to: candidate) ?? candidate
        }
        while dayIndex(of: candidate, tz: tz) == nil {
            guard let next = calendar.date(byAdding: .day, value: 1, to: candidate) else { break }
            candidate = next
        }
        return candidate
    }

    static func formatTime(hour: Int, minute: Int) -> String {
        String(format: "%02d:%02d", hour, minute)
    }

    static func relativeUpdateLabel(since: Date, now: Date = Date()) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: since, relativeTo: now)
    }

    private static func makeCalendar(tz: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = tz
        return calendar
    }

    private static func isLeapYear(_ year: Int) -> Bool {
        (year.isMultiple(of: 4) && !year.isMultiple(of: 100)) || year.isMultiple(of: 400)
    }
}
