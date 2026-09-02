import Foundation

struct MenuAPI: Sendable {
    static let baseURL = URL(string: "https://six-mensa-api.homelab.davidemarcoli.dev/")!

    enum APIError: LocalizedError, Equatable {
        case invalidRestaurant
        case menuNotFound
        case http(Int, String)
        case network
        case malformed

        var errorDescription: String? {
            switch self {
            case .invalidRestaurant:
                return "Invalid restaurant"
            case .menuNotFound:
                return "Menu not found"
            case .http(let code, let body):
                return body.isEmpty ? "Server error (\(code))" : "Server error (\(code)): \(body)"
            case .network:
                return NSLocalizedString("error.generic", comment: "")
            case .malformed:
                return NSLocalizedString("error.generic", comment: "")
            }
        }
    }

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func week(restaurant: Restaurant, language: ContentLanguage) async throws -> [DayMenu] {
        let path = "\(restaurant.rawValue)/-1?language=\(language.rawValue)"
        guard let url = URL(string: "\(Self.baseURL.absoluteString)\(path)") else {
            throw APIError.network
        }
        return try await fetch(url)
    }

    func history(from: String? = nil, to: String? = nil) async throws -> [HistoryEntry] {
        // Empty strings are treated as "no bound"; the server returns [] for invalid values.
        let fromValue = from?.isEmpty == false ? from : nil
        let toValue = to?.isEmpty == false ? to : nil
        var query = ""
        if let fromValue, let toValue {
            query = "?from=\(fromValue)&to=\(toValue)"
        } else if let fromValue {
            query = "?from=\(fromValue)"
        } else if let toValue {
            query = "?to=\(toValue)"
        }
        guard let url = URL(string: "\(Self.baseURL.absoluteString)history\(query)") else {
            throw APIError.network
        }
        return try await fetch(url)
    }

    func pdfLinks() async throws -> [String: String] {
        guard let url = URL(string: "\(Self.baseURL.absoluteString)pdf-links") else {
            throw APIError.network
        }
        return try await fetch(url)
    }

    func pdfLink(for restaurant: Restaurant, in links: [String: String]) -> URL? {
        guard let value = links.first(where: { $0.key.caseInsensitiveCompare(restaurant.rawValue) == .orderedSame })?.value else {
            return nil
        }
        return URL(string: value)
    }

    func status() async throws -> APIStatus {
        guard let url = URL(string: "\(Self.baseURL.absoluteString)status") else {
            throw APIError.network
        }
        return try await fetch(url)
    }

    private func fetch<T: Decodable>(_ url: URL) async throws -> T {
        let (data, code) = try await requestData(url)
        guard (200..<300).contains(code) else {
            let body = String(data: data, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            switch body.lowercased() {
            case "invalid restaurant":
                throw APIError.invalidRestaurant
            case "menu not found":
                throw APIError.menuNotFound
            default:
                throw APIError.http(code, body)
            }
        }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIError.malformed
        }
    }

    private func requestData(_ url: URL) async throws -> (Data, Int) {
        do {
            let (data, response) = try await session.data(from: url)
            guard let http = response as? HTTPURLResponse else {
                throw APIError.network
            }
            return (data, http.statusCode)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.network
        }
    }
}
