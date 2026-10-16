import XCTest
final class VoxkinUITests: XCTestCase {
    func testHistoryOpenAndContinueWithoutAutoplay() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-history"]; app.launch()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.buttons["history-agent:synthetic:voxkin:11111111-2222-4333-8444-555555555555"].waitForExistence(timeout: 10))
        app.buttons["history-agent:synthetic:voxkin:11111111-2222-4333-8444-555555555555"].tap()
        XCTAssertTrue(app.staticTexts["Suggest a quiet weekend activity."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Take a short walk, then settle in with a good book."].exists)
        let attachment = XCTAttachment(screenshot:app.screenshot()); attachment.name="history-preview"; attachment.lifetime = .keepAlways; add(attachment)
        app.buttons["Continue in Talk"].tap()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout:5))
        XCTAssertTrue(app.staticTexts["Take a short walk, then settle in with a good book."].exists)
    }

    func testHistorySwipeRenameAndConfirmedDelete() {
        let app = XCUIApplication(); app.launchArguments=["-ui-testing-history"]; app.launch()
        app.tabBars.buttons["History"].tap()
        let row=app.buttons["history-agent:synthetic:voxkin:11111111-2222-4333-8444-555555555555"]
        XCTAssertTrue(row.waitForExistence(timeout:10))
        row.swipeLeft()
        XCTAssertTrue(app.buttons["Rename"].waitForExistence(timeout:3)); app.buttons["Rename"].tap()
        let field=app.alerts.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout:3))
        field.tap(); field.typeText(String(repeating:XCUIKeyboardKey.delete.rawValue,count:"A quiet weekend".count)+"Weekend ideas")
        app.alerts.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Weekend ideas"].waitForExistence(timeout:5))
        row.swipeLeft(); app.buttons["Delete"].tap()
        XCTAssertTrue(app.buttons["Delete conversation"].waitForExistence(timeout:3))
        let attachment=XCTAttachment(screenshot:app.screenshot()); attachment.name="history-delete-confirmation"; attachment.lifetime = .keepAlways; add(attachment)
        app.alerts.buttons["Cancel"].tap(); XCTAssertTrue(row.exists)
        row.swipeLeft(); app.buttons["Delete"].tap(); app.buttons["Delete conversation"].tap()
        XCTAssertTrue(app.staticTexts["No saved conversations yet."].waitForExistence(timeout:5))
    }
    func testStorefrontScreenshots() {
        let app = XCUIApplication()
        func capture(_ name: String) {
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
        }
        app.launchArguments = ["-store-screenshots"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["CONNECTED"].waitForExistence(timeout: 5))
        capture("01-talk")
        app.terminate()
        app.launchArguments = ["-store-screenshots", "-store-screenshot-reply"]; app.launch()
        XCTAssertTrue(app.staticTexts["reply"].waitForExistence(timeout: 10))
        capture("02-conversation")
        app.tabBars.buttons["Settings"].tap()
        let voice = app.buttons["localVoice"]
        for _ in 0..<6 { if voice.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(voice.isHittable)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.30))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)))
        capture("03-voice-settings")
    }

    func testPairingApprovalPanelExplainsHostActionAndManualRetry() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-pairing"]; app.launch()
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Waiting for gateway approval"].waitForExistence(timeout:5))
        XCTAssertTrue(app.staticTexts["openclaw devices list"].exists)
        XCTAssertTrue(app.staticTexts["openclaw devices approve 7a56aa63-090b-48bf-a317-51c05201d72d"].exists)
        XCTAssertTrue(app.buttons["Retry connection"].exists)
        let shot = XCTAttachment(screenshot:app.screenshot());shot.name="Gateway approval panel";shot.lifetime = .keepAlways;add(shot)
    }

    func testConnectedPermissionToggleExplainsHowToChangeIt() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-conversation"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout:10))
        app.tabBars.buttons["Settings"].tap()
        let toggle = app.switches["Request access-management permission"]
        for _ in 0..<3 { if toggle.exists { break }; app.swipeUp() }
        XCTAssertTrue(toggle.waitForExistence(timeout:5)); XCTAssertFalse(toggle.isEnabled)
        XCTAssertTrue(app.staticTexts["Disconnect to change this permission, then reconnect to request gateway approval."].exists)
        app.buttons["Disconnect"].tap()
        XCTAssertTrue(toggle.isEnabled)
    }

    func testSpokenAcknowledgmentSettingPersists() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-empty"]
        func setting() -> XCUIElement {
            app.tabBars.buttons["Settings"].tap()
            let toggle = app.switches["Conversational feedback"]
            for _ in 0..<6 { if toggle.isHittable { break }; app.swipeUp() }
            XCTAssertTrue(toggle.isHittable)
            return toggle
        }
        app.launch()
        let first = setting(), original = first.value as? String
        first.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        let changed = original == "1" ? "0" : "1"
        let changedExpectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", changed), object: first)
        XCTAssertEqual(XCTWaiter.wait(for: [changedExpectation], timeout: 3), .completed)
        app.terminate(); app.launch()
        let restored = setting()
        XCTAssertEqual(restored.value as? String, changed)
        restored.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
    }

    func testEnrollmentRequiresExplicitActionAndHidesAdminControls() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-empty"]; app.launch()
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Scan invitation QR"].waitForExistence(timeout:5))
        XCTAssertTrue(app.buttons["Enroll this phone"].exists)
        XCTAssertFalse(app.buttons["Enroll this phone"].isEnabled)
        XCTAssertFalse(app.buttons["Add phone"].exists)
        XCTAssertFalse(app.buttons["Share invite"].exists)
        let shot = XCTAttachment(screenshot:app.screenshot());shot.name="Restricted enrollment entry";shot.lifetime = .keepAlways;add(shot)
    }

    func testLongPressEntersConversationAndStopEndsIt() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-conversation"]; app.launch()
        let mic = app.buttons["talkButton"]
        XCTAssertTrue(mic.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["startConversation"].waitForExistence(timeout: 5))
        mic.press(forDuration: 0.8)
        XCTAssertTrue(app.buttons["endConversation"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["status"].label.contains("Listening"))
        XCTAssertFalse(app.staticTexts["reply"].exists)
        app.buttons["stopAudio"].tap()
        XCTAssertFalse(app.buttons["endConversation"].exists)
        XCTAssertTrue(app.buttons["startConversation"].waitForExistence(timeout: 3))
        app.buttons["startConversation"].tap()
        XCTAssertTrue(app.buttons["endConversation"].waitForExistence(timeout: 3))
        app.tabBars.buttons["Settings"].tap()
        app.tabBars.buttons["Talk"].tap()
        XCTAssertFalse(app.buttons["endConversation"].exists)
    }

    func testLocalVoiceSettingsDiscovery() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-voices"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout:10))
        app.tabBars.buttons["Settings"].tap()
        app.swipeUp()
        XCTAssertTrue(app.buttons["voiceLanguage"].waitForExistence(timeout:5))
        XCTAssertTrue(app.buttons["localVoice"].label.contains("Dora"))
        let screenshot = XCTAttachment(screenshot:app.screenshot()); screenshot.name="Local voice settings"; screenshot.lifetime = .keepAlways; add(screenshot)
        app.buttons["voiceLanguage"].tap(); app.buttons["English"].tap()
        XCTAssertTrue(app.buttons["localVoice"].label.contains("Heart"))
        XCTAssertTrue(app.buttons["Preview voice"].isEnabled)
    }
    func testTalkTabsKeyboardSendAndPinnedStop() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.switches["Use synthetic demo (no network)"].exists)
        app.buttons["typeInstead"].tap()
        let draft = app.textFields["draft"]
        XCTAssertTrue(draft.waitForExistence(timeout: 5))
        draft.tap(); draft.typeText("Hello Iris")
        app.buttons["send"].tap()
        let reply = app.staticTexts["reply"]
        XCTAssertTrue(reply.waitForExistence(timeout: 8))
        XCTAssertTrue(reply.label.contains("Synthetic reply"))
        XCTAssertEqual(app.keyboards.count, 0)
        XCTAssertTrue(app.buttons["stopAudio"].isHittable)
        app.buttons["stopAudio"].tap()
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Talk reply"; screenshot.lifetime = .keepAlways; add(screenshot)
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Disconnect"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["gatewayURL"].exists)
        app.buttons["Disconnect"].tap()
        XCTAssertTrue(app.buttons["Connect"].exists)
        app.tabBars.buttons["Talk"].tap()
        XCTAssertFalse(app.staticTexts["reply"].exists)
    }
    func testClearTypedDraftRestoresTalkWithoutSending() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout: 10))
        app.buttons["typeInstead"].tap()
        XCTAssertFalse(app.buttons["clearDraft"].exists)
        let draft = app.textFields["draft"]; draft.tap(); draft.typeText("Wrong words")
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Draft clear layout"; screenshot.lifetime = .keepAlways; add(screenshot)
        app.buttons["clearDraft"].tap()
        XCTAssertFalse(app.buttons["clearDraft"].exists)
        XCTAssertEqual(app.buttons["talkButton"].label, "Talk to agent")
        XCTAssertFalse(app.staticTexts["reply"].exists)
        XCTAssertFalse(app.buttons["send"].isEnabled)
    }
    func testBigButtonSendsExistingDraftInsteadOfStartingRecording() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout: 10))
        app.buttons["typeInstead"].tap()
        let draft = app.textFields["draft"]; draft.tap(); draft.typeText("Keep my final words")
        XCTAssertEqual(app.buttons["talkButton"].label, "Send draft to agent")
        app.buttons["talkButton"].tap()
        XCTAssertTrue(app.staticTexts["reply"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.staticTexts["Keep my final words"].exists)
        XCTAssertEqual(app.buttons["talkButton"].label, "Talk to agent")
    }
    func testFreshAppHasSettingsAndNoDemoOrAutomaticRecording() {
        let app = XCUIApplication(); app.launchArguments = ["-ui-testing-empty"]; app.launch()
        XCTAssertTrue(app.buttons["talkButton"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["talkButton"].label, "Connect to gateway")
        XCTAssertFalse(app.switches["Use synthetic demo (no network)"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Talk welcome"; screenshot.lifetime = .keepAlways; add(screenshot)
        app.buttons["talkButton"].tap()
        XCTAssertTrue(app.secureTextFields["pairingToken"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Connect"].exists)
        XCTAssertFalse(app.staticTexts["dictation"].exists)
    }
}
