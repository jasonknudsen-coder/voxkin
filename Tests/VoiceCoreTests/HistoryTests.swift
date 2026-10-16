import XCTest
@testable import VoiceCore
final class HistoryTests: XCTestCase {
    func testAutomaticTitlesIgnoreTestTurnsAndIncompleteWrappers() {
        let messages = [ConversationMessage(id:"1",role:"user",text:"Test 1234"),
                        ConversationMessage(id:"2",role:"assistant",text:"Ignore me"),
                        ConversationMessage(id:"3",role:"user",text:"[Voxkin reply preference for this turn only: brief.]\n\nWeather in Highlands Ranch?")]
        XCTAssertEqual(VoxkinHistory.automaticTitle(messages),"Weather in Highlands Ranch?")
        XCTAssertNil(VoxkinHistory.automaticTitle([.init(id:"x",role:"user",text:"[Voxkin reply preference truncated")]))
        XCTAssertEqual(VoxkinHistory.automaticTitle([.init(id:"x",role:"user",text:String(repeating:"🫶",count:100))])?.count,80)
        XCTAssertEqual(VoxkinHistory.cleanTitle("  A   quiet\nweekend  "),"A quiet weekend")
    }
    func testNamespaceIsExactAndAgentScoped() {
        let id = UUID().uuidString.lowercased()
        XCTAssertTrue(VoxkinHistory.owns(VoxkinHistory.session(agent: "main", id: id), agent: "main"))
        XCTAssertFalse(VoxkinHistory.owns("agent:other:voxkin:" + id, agent: "main"))
        XCTAssertFalse(VoxkinHistory.owns("agent:main:discord:" + id, agent: "main"))
        XCTAssertFalse(VoxkinHistory.owns("agent:main:voxkin:restricted:direct:phone", agent: "main"))
        XCTAssertFalse(VoxkinHistory.owns("agent:main:voxkin:" + id + ":extra", agent: "main"))
    }
    func testHistoryFiltersToolsReasoningAndStripsOnlyOwnPreference() {
        let rows: [[String: Any]] = [
            ["id":"u","role":"user","content":"[Voxkin reply preference for this turn only: Be brief.]\n\nHello"],
            ["id":"a","role":"assistant","content":[["type":"text","text":"Hi"]]],
            ["id":"t","role":"toolResult","content":"private tool data"],
            ["id":"r","role":"assistant","channel":"analysis","content":"private reasoning"],
            ["id":"m","role":"assistant","content":[["type":"text","text":"working"],["type":"toolCall","name":"secret"]]],
        ]
        let messages = VoxkinHistory.messages(rows)
        XCTAssertEqual(messages.map(\.id), ["u","a"])
        XCTAssertEqual(messages.map(\.text), ["Hello","Hi"])
        XCTAssertEqual(VoxkinHistory.displayText("[My own note]\n\nHello"), "[My own note]\n\nHello")
    }
}
