import XCTest
import AppIntentsTesting

@available(iOS 27.0, *)
final class TodayMenuIntentTests: XCTestCase {

    var app: XCUIApplication!
    var definitions: IntentDefinitions!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        definitions = IntentDefinitions(bundleIdentifier: "dev.davidemarcoli.mensa")
    }

    func testTodayMenuIntentIsRegistered() throws {
        let intentDef = definitions.intents["TodayMenuIntent"]
        XCTAssertEqual(intentDef.identifier, "TodayMenuIntent")
        XCTAssertEqual(intentDef.bundleIdentifier, "dev.davidemarcoli.mensa")
    }

    func testTodayMenuIntentRuns() async throws {
        let intentDef = definitions.intents["TodayMenuIntent"]
        let intent = intentDef.makeIntent()
        let result = try await intent.run()
        XCTAssertNotNil(result)
    }

    func testRestaurantEntityIsRegistered() throws {
        let entityDef = definitions.entities["RestaurantEntity"]
        XCTAssertEqual(entityDef.typeIdentifier, "RestaurantEntity")
    }
}