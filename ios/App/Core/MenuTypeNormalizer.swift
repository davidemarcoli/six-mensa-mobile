import Foundation

enum MenuTypeNormalizer {

    static let knownTypes: [String] = ["Local", "Climate", "Global", "Pizza & Pasta"]

    // Order is significant: "Local" must be tested before the "Climate" group.
    private static let typeGroups: [(name: String, keywords: [String])] = [
        ("Local", ["local"]),
        ("Climate", ["climate", "klima", "vegetarian", "veggi", "veggie", "vegi"]),
        ("Global", ["global", "globetrotter"]),
        ("Pizza & Pasta", ["pizza"]),
    ]

    static func normalize(_ raw: String) -> String {
        let lowered = raw.lowercased()
        for group in typeGroups where group.keywords.contains(where: { lowered.contains($0) }) {
            return group.name
        }
        return raw.trimmingCharacters(in: .whitespaces)
    }
}
