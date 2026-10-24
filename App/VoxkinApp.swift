import SwiftUI
import UIKit
import VoiceCore
import CoreImage.CIFilterBuiltins
import AVFoundation

@main struct VoxkinApp: App {
    @StateObject private var model: VoiceModel
    init() {
        // CarPlay can launch without creating the phone WindowGroup first.
        let model = Self.makeModel()
        _model = StateObject(wrappedValue: model)
    }
    @Environment(\.scenePhase) private var phase
    private static func makeModel() -> VoiceModel {
        #if targetEnvironment(simulator)
        // Store artwork uses the real UI with fictional, offline sample content.
        // This launch path is compiled out of physical-device/archive builds.
        if ProcessInfo.processInfo.arguments.contains("-store-screenshots") {
            let prefs = UserDefaults(suiteName: "voxkin-store-screenshots")!
            prefs.set("af_heart", forKey: "test-local-voice")
            let model = VoiceModel(restoreConnection: false, preferences: prefs,
                mockMethods: ["voxkin.catalog", "voxkin.speak"], mockFactory: { MockGateway() })
            Task { @MainActor in
                await model.connect()
                model.agents = ["personal"]; model.selectedAgent = "personal"
                if ProcessInfo.processInfo.arguments.contains("-store-screenshot-reply") {
                    model.sentText = "Suggest a simple way to unwind after a busy day."
                    model.reply = "Take a short walk, put your phone aside, and make something warm to drink. Pick one small thing you enjoy—music, a few pages of a book, or a quiet chat—and let that be enough for tonight."
                }
            }
            return model
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-history") {
            return VoiceModel(restoreConnection: false, preferences: UserDefaults(suiteName:"voxkin-history-ui")!, mockMethods:["chat.history", "sessions.preview", "sessions.patch", "sessions.delete"], mockFactory:{MockGateway()})
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-conversation") {
            return VoiceModel(restoreConnection: false, capture: ConversationTestCapture(), mockFactory: { MockGateway() })
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-voices") {
            let prefs = UserDefaults(suiteName: "voxkin-ui-local")!
            prefs.set("ef_dora", forKey: "test-local-voice")
            return VoiceModel(restoreConnection: false, preferences: prefs, mockMethods: ["voxkin.catalog", "voxkin.speak"], mockFactory: { MockGateway() })
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing") {
            return VoiceModel(restoreConnection: false, mockFactory: { MockGateway() })
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-pairing") {
            let model = VoiceModel(restoreConnection:false)
            model.pairingApproval = GatewayPairingRequired(requestID:"7a56aa63-090b-48bf-a317-51c05201d72d")
            return model
        }
        if ProcessInfo.processInfo.arguments.contains("-ui-testing-empty") {
            return VoiceModel(restoreConnection: false)
        }
        #endif
        let model = VoiceModel()
        CarPlayCoordinator.shared.attach(model:model)
        return model
    }
    var body: some Scene {
        WindowGroup {
            VoiceView(model: model)
                .preferredColorScheme(.dark)
                .task {
                    UIApplication.shared.isIdleTimerDisabled = phase == .active
                    await model.foreground()
                }
                .onChange(of: phase) { _, value in
                    UIApplication.shared.isIdleTimerDisabled = value == .active
                    if value == .inactive && !CarPlayCoordinator.shared.ownsVoiceSession { model.endConversation() }
                    if value == .background {
                        if !CarPlayCoordinator.shared.isConnected { model.background() }
                        else if !CarPlayCoordinator.shared.ownsVoiceSession { model.interrupt() }
                    }
                    if value == .active { Task { await model.foreground() } }
                }
        }
    }
}

struct VoiceView: View {
    static func formattedReply(_ text: String) -> AttributedString {
        (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
    }
    @ObservedObject var model: VoiceModel
    @State private var tab = 0
    @State private var typing = false
    @State private var renameTarget: ConversationSummary?
    @State private var deleteTarget: ConversationSummary?
    @State private var conversationName = ""
    @State private var showRename = false
    @State private var showDelete = false
    @State private var confirmForget = false
    @State private var scanningInvite = false
    @FocusState private var draftFocused: Bool
    private let accent = VoxkinBrand.cyan
    private var idle: Bool { model.gate.state == .ready || model.gate.state == .interrupted }
    private var hasDraft: Bool { !model.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var recording: Bool { model.gate.state == .recording }
    private var working: Bool { model.gate.state == .waiting || model.gate.state == .speaking }
    var body: some View {
        TabView(selection: $tab) {
            talk.tabItem { Label("Talk", systemImage: "waveform") }.tag(0)
            history.tabItem { Label("History", systemImage: "clock.arrow.circlepath") }.tag(2)
            settings.tabItem { Label("Settings", systemImage: "slider.horizontal.3") }.tag(1)
        }
        .alert("Rename conversation", isPresented: $showRename) {
            TextField("Conversation name", text: $conversationName)
            Button("Save") { if let target = renameTarget { Task { await model.renameConversation(target, to: conversationName) } } }
                .disabled(VoxkinHistory.cleanTitle(conversationName).isEmpty || conversationName.unicodeScalars.count > 80)
            Button("Cancel", role:.cancel) { renameTarget = nil }
        } message: { Text("Choose a short name, up to 80 characters. It is saved on your gateway.") }
        .alert("Delete conversation?", isPresented: $showDelete) {
            Button("Delete conversation", role:.destructive) { if let target = deleteTarget { Task { await model.deleteConversation(target) } } }
            Button("Cancel", role:.cancel) { deleteTarget = nil }
        } message: { Text("Remove this conversation from History? You cannot undo this in Voxkin. The gateway may retain agent transcripts or backups.") }
        .onChange(of: model.historyUIContext) { _, _ in showRename = false; showDelete = false; renameTarget = nil; deleteTarget = nil }
        .onOpenURL { url in
            if model.handleExternalSetupURL(url) { tab = 1 }
        }
        .sheet(isPresented: $scanningInvite) {
            InvitationScanner { value in model.invitationText = value; scanningInvite = false }
        }
        .tint(accent)
        .toolbarBackground(VoxkinBrand.navy, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .onChange(of: tab) { _, value in draftFocused = false; if value != 0 { model.endConversation() } }
        .onChange(of: model.gate.state) { _, state in
            if (state == .ready || state == .interrupted) && !model.draft.isEmpty { typing = true }
        }
    }
    private var history: some View {
        NavigationStack {
            List {
                if !model.connected {
                    Text("Connect to your gateway to view history.")
                } else if !model.historyAvailable {
                    Text("History requires a gateway with history support. Restricted phones also need the updated Voxkin plugin.")
                } else {
                    Section("Conversations · \(model.selectedAgent)") {
                        Button("New conversation", systemImage: "square.and.pencil") {
                            Task { await model.newConversation(); if model.historyError.isEmpty { tab = 0 } }
                        }.disabled(model.historyLoading || model.activeRun != nil)
                        ForEach(model.conversations) { item in
                            Button {
                                Task { await model.openConversation(item) }
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(item.title.isEmpty ? "Conversation" : item.title).lineLimit(2)
                                        if model.historySelection == item.id { Image(systemName: "checkmark.circle.fill") }
                                    }
                                    if !item.preview.isEmpty { Text(item.preview).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                                    Text(item.updatedAt.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                                }
                            }.accessibilityIdentifier("history-" + item.id).disabled(model.historyLoading || model.activeRun != nil)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) { deleteTarget = item; showDelete = true } label: { Label("Delete", systemImage:"trash") }
                                    .disabled(!model.canEditConversation(item))
                                Button { renameTarget = item; conversationName = item.title; showRename = true } label: { Label("Rename", systemImage:"pencil") }
                                    .tint(.indigo).disabled(!model.canEditConversation(item))
                            }
                            .contextMenu {
                                Button("Rename", systemImage:"pencil") { renameTarget = item; conversationName = item.title; showRename = true }
                                    .disabled(!model.canEditConversation(item))
                                Button("Delete", systemImage:"trash", role:.destructive) { deleteTarget = item; showDelete = true }
                                    .disabled(!model.canEditConversation(item))
                            }
                        }
                        if model.conversations.isEmpty && !model.historyLoading { Text("No saved conversations yet.").foregroundStyle(.secondary) }
                        if model.historyHasMore { Button("Load more conversations") { Task { await model.loadConversations(more: true) } }.disabled(model.historyLoading) }
                    }
                    if !model.historySelection.isEmpty {
                        Section("Selected conversation") {
                            Button("Continue in Talk", systemImage: "waveform") { tab = 0 }
                                .disabled(model.historyLoading || !model.historyReady)
                            Button("Refresh messages") { Task { await model.loadHistoryMessages() } }.disabled(model.historyLoading)
                            if model.messagesHaveMore { Button("Load older messages") { Task { await model.loadHistoryMessages(more: true) } }.disabled(model.historyLoading) }
                            ForEach(model.historyMessages) { message in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(message.role == "user" ? "You" : "Agent").font(.caption.bold()).foregroundStyle(accent)
                                    Text(Self.formattedReply(message.text)).textSelection(.enabled)
                                }.padding(.vertical, 4)
                            }
                        }
                    }
                    Section { Text("Stored on your gateway. Only Voxkin conversations are shown. Opening history never plays audio. Gateway retention policies apply.").font(.caption).foregroundStyle(.secondary) }
                }
                if model.historyLoading { ProgressView("Loading history…") }
                if !model.historyError.isEmpty { Text(model.historyError).foregroundStyle(.orange) }
            }
            .navigationTitle("History")
            .toolbar { Button("Refresh", systemImage: "arrow.clockwise") { Task { await model.loadConversations() } }.disabled(model.historyLoading || !model.connected) }
            .task { await model.loadConversations() }
            .refreshable { await model.loadConversations() }
        }
    }
    private var talk: some View {
        NavigationStack {
            ZStack {
                VoxkinBrand.background.ignoresSafeArea()
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 24) {
                            HStack {
                                Label(model.connected ? "CONNECTED" : (model.busy ? "CONNECTING" : "OFFLINE"), systemImage: model.connected ? "circle.fill" : "circle")
                                    .font(.caption2.weight(.semibold)).tracking(2).foregroundStyle(model.connected ? accent : .secondary)
                                Spacer()
                                if !model.selectedAgent.isEmpty { Text(model.selectedAgent.capitalized).font(.caption).foregroundStyle(.secondary) }
                            }
                            if model.sentText.isEmpty && model.reply.isEmpty {
                                VStack(spacing: 20) {
                                    VoxkinMark().frame(width: 184, height: 116).accessibilityHidden(true)
                                    VStack(spacing: 10) {
                                        Text("Talk to your agent.")
                                            .font(.system(.largeTitle, design: .rounded, weight: .bold))
                                        Text(model.endpoint.isEmpty && !model.connected ? "Your voice. Your agent.\nAdd your gateway in Settings to get started." : "A little less typing. A little more talking.\nTap to record. Stop to review. Then send.")
                                            .font(.body).foregroundStyle(.secondary)
                                    }
                                }.multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.vertical, 20)
                            }
                            if !model.sentText.isEmpty {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("YOU").font(.caption2.weight(.bold)).tracking(2).foregroundStyle(accent)
                                    Text(model.sentText).font(.body)
                                }.padding(18).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 22))
                            }
                            if !model.reply.isEmpty || !model.interim.isEmpty {
                                VStack(alignment: .leading, spacing: 12) {
                                    Label("AGENT", systemImage: "waveform").font(.caption2.weight(.bold)).tracking(2).foregroundStyle(VoxkinBrand.magenta)
                                    if !model.interim.isEmpty { Text(model.interim).foregroundStyle(.secondary).accessibilityIdentifier("interim") }
                                    if !model.reply.isEmpty { Text(Self.formattedReply(model.reply)).textSelection(.enabled).accessibilityIdentifier("reply") }
                                }.font(.body).lineSpacing(5).padding(20).frame(maxWidth: .infinity, alignment: .leading)
                                    .background(VoxkinBrand.purple.opacity(0.12), in: RoundedRectangle(cornerRadius: 24))
                                    .overlay(RoundedRectangle(cornerRadius: 24).stroke(VoxkinBrand.purple.opacity(0.25), lineWidth: 1))
                            }
                            Color.clear.frame(height: 1).id("latest")
                        }.padding(24)
                    }
                    .clipped()
                    .scrollDismissesKeyboard(.interactively)
                    .onChange(of: model.reply) { _, _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                    .onChange(of: model.interim) { _, _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                    .onChange(of: model.sentText) { _, _ in withAnimation { proxy.scrollTo("latest", anchor: .bottom) } }
                }
            }
            .navigationTitle("Voxkin")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .principal) { VoxkinWordmark() } }
            .toolbarBackground(VoxkinBrand.navy, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .safeAreaInset(edge: .bottom, spacing: 0) { controls }
        }
    }
    private var controls: some View {
        VStack(spacing: 12) {
            if model.conversationMode {
                HStack {
                    Label(recording ? "CONVERSATION · LISTENING" : "CONVERSATION ON", systemImage: recording ? "mic.fill" : "waveform")
                        .font(.caption.weight(.semibold)).foregroundStyle(accent)
                    Spacer()
                    Button("End") { model.endConversation() }.accessibilityIdentifier("endConversation")
                }
            }
            HStack(spacing: 8) {
                if model.busy || working { ProgressView().tint(accent) }
                Text(model.status).font(.caption).foregroundStyle(.secondary).lineLimit(3).accessibilityIdentifier("status")
                if working, !model.previewing, let start = model.turnStartedAt { Text(start, style: .timer).font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
            }.frame(maxWidth: .infinity)
            if recording && !model.finishingSpeech {
                HStack(spacing: 8) {
                    if model.microphoneReady {
                        Image(systemName: "waveform")
                            .symbolEffect(.pulse, options: .repeating)
                            .foregroundStyle(accent)
                        Text(hasDraft ? "Listening · words detected" : "Listening · speak now")
                    } else {
                        ProgressView()
                        Text("Getting microphone ready…")
                    }
                }
                .font(.caption).accessibilityElement(children: .combine)
                .accessibilityIdentifier("recordingIndicator")
            }
            if typing || recording || model.finishingSpeech || hasDraft {
                HStack(alignment: .center, spacing: 8) {
                    HStack(spacing: 0) {
                    TextField("Message your agent", text: $model.draft, axis: .vertical)
                        .lineLimit(1...4).focused($draftFocused).accessibilityIdentifier("draft")
                        .disabled(!idle || !model.connected || model.activeRun != nil)
                    if !model.draft.isEmpty {
                        Button { draftFocused = false; model.clearDraft() } label: {
                            Image(systemName: "xmark.circle.fill").font(.system(size: 18)).foregroundStyle(.secondary).frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Clear draft").accessibilityIdentifier("clearDraft")
                        .disabled(model.activeRun != nil || model.busy || working)
                    }
                    }.padding(.leading, 14).padding(.vertical, 6)
                        .frame(minHeight: 56)
                        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18))
                    if !recording && !model.finishingSpeech {
                    Button { draftFocused = false; model.send() } label: { Image(systemName: "arrow.up.circle.fill").font(.title).frame(width: 44, height: 44) }
                        .accessibilityLabel("Send message").accessibilityIdentifier("send")
                        .disabled(!idle || !model.connected || model.activeRun != nil || model.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !model.acceptedFallback)
                    }
                }
            }
            HStack(spacing: 28) {
                Button { model.endConversation(); typing.toggle(); draftFocused = typing } label: { Image(systemName: typing ? "keyboard.chevron.compact.down" : "keyboard").font(.title2).frame(width: 48, height: 48) }
                    .accessibilityLabel("Type instead").accessibilityIdentifier("typeInstead")
                Button(action: primaryAction) {
                    Image(systemName: recording ? (model.conversationMode ? "arrow.up" : "stop.fill") : ((model.connected && hasDraft) ? "arrow.up" : (model.connected ? "mic.fill" : "bolt.horizontal.fill")))
                        .font(.system(size: 36, weight: .medium)).foregroundStyle(.white)
                        .frame(width: 108, height: 108)
                        .background(LinearGradient(colors: recording ? [.orange, .pink] : [VoxkinBrand.cyan, VoxkinBrand.blue, VoxkinBrand.purple], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                        .overlay(Circle().stroke(.white.opacity(0.2), lineWidth: 1))
                        .shadow(color: VoxkinBrand.blue.opacity(0.28), radius: 20, y: 6)
                }
                .highPriorityGesture(LongPressGesture(minimumDuration: 0.65).exclusively(before: TapGesture()).onEnded { gesture in
                    switch gesture {
                    case .first(true):
                        draftFocused = false
                        if model.conversationMode { model.endConversation() }
                        else { Task { await model.startConversation() } }
                    case .second: primaryAction()
                    default: break
                    }
                })
                .accessibilityAction(named: "Start hands-free conversation") { Task { await model.startConversation() } }
                .disabled((model.busy || working || model.finishingSpeech || (recording && !model.microphoneReady) || (model.activeRun != nil && !recording)) && !model.canInterruptConversationReply)
                .opacity((model.busy || working) && !model.canInterruptConversationReply ? 0.5 : 1)
                .accessibilityLabel(model.canInterruptConversationReply ? "Interrupt reply and listen" : recording ? (model.conversationMode ? "Finish and send" : "Stop recording and review") : (model.connected ? (hasDraft ? "Send draft to agent" : "Talk to agent") : "Connect to gateway"))
                .accessibilityIdentifier("talkButton")
                Button {
                    draftFocused = false
                    if (recording || model.finishingSpeech) && !model.conversationMode { model.clearDraft() }
                    else { model.stopPlayback() }
                } label: {
                    Image(systemName: (recording || model.finishingSpeech) && !model.conversationMode ? "xmark" : "stop.fill").font(.title2).frame(width: 48, height: 48)
                }.accessibilityLabel((recording || model.finishingSpeech) && !model.conversationMode ? "Cancel recording" : "Stop audio").accessibilityIdentifier("stopAudio")
                    .disabled(!model.connected)
            }
            if !model.conversationMode && model.canStartConversation {
                Button("Hold the mic for conversation", systemImage: "waveform") {
                    draftFocused = false; Task { await model.startConversation() }
                }.font(.caption).accessibilityIdentifier("startConversation")
            }
            if model.canInterruptConversationReply {
                Text("Tap the mic to interrupt and listen").font(.caption).foregroundStyle(accent)
            }
            HStack {
                Text(model.finishingSpeech ? "Finishing…" : (recording ? (model.microphoneReady ? (model.conversationMode ? "Pause to send · tap to finish" : "Listening · tap Stop to review") : "Opening microphone…") : (model.connected && !model.acceptedFallback ? "Enable speech in Settings" : (model.connected ? (hasDraft ? "Tap to send your draft" : "Tap to talk") : (model.endpoint.isEmpty ? "Set up your gateway" : "Tap to connect"))))).font(.caption).foregroundStyle(.secondary)
                if model.activeRun != nil {
                    Spacer()
                    Button("Cancel request") { Task { await model.cancelAgent() } }.font(.caption).accessibilityIdentifier("cancelRequest")
                }
            }
        }.padding(.horizontal, 24).padding(.top, 14).padding(.bottom, 12).background(VoxkinBrand.navy)
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            .overlay(alignment: .top) { Rectangle().fill(VoxkinBrand.purple.opacity(0.2)).frame(height: 1) }
    }
    private func primaryAction() {
        draftFocused = false
        if model.canInterruptConversationReply { Task { await model.interruptConversationReply() } }
        else if recording {
            if model.conversationMode { model.finishAndSend() }
            else { model.finishRecording() }
        }
        else if !model.connected {
            if model.endpoint.isEmpty { tab = 1 }
            else { Task { await model.connect() } }
        } else if !model.acceptedFallback { tab = 1 }
        else if hasDraft { model.send() }
        else { Task { await model.record() } }
    }
    private func timingRow(_ title: String, _ seconds: TimeInterval) -> some View {
        LabeledContent(title, value: String(format: "%.1f s", seconds))
    }
    private var settings: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 16) {
                        VoxkinMark().frame(width: 68, height: 44).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 4) {
                            VoxkinWordmark()
                            Text("Talk to your agent.").font(.subheadline).foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 6)
                }.listRowBackground(Color.clear)
                if let approval = model.pairingApproval {
                    Section("Gateway approval required") {
                        Label("Waiting for gateway approval", systemImage:"person.badge.key.fill").font(.headline)
                        Text("Your phone reached the gateway. On the gateway host, list pending devices, verify this phone and its requested permissions, then approve its request.")
                        Text("openclaw devices list").font(.system(.caption, design:.monospaced)).textSelection(.enabled)
                        if let command = approval.approvalCommand {
                            Text(command).font(.system(.caption, design:.monospaced)).textSelection(.enabled)
                        } else {
                            Text("Run openclaw devices approve REQUEST_ID using the ID from that list.").font(.caption)
                        }
                        Text("Approval is required even without management permission. A pending request may retain permissions requested earlier; verify the list before approving. Your saved credential is kept.").font(.caption)
                        Button("Retry connection") { Task { await model.connect() } }.disabled(model.busy)
                    }.listRowBackground(Color.orange.opacity(0.12))
                }
                if !model.connected {
                    Section("Set up this phone") {
                        SecureField("Paste Voxkin invitation", text:$model.invitationText).textInputAutocapitalization(.never).autocorrectionDisabled()
                        if let invite = try? VoxkinInvitation(model.invitationText) {
                            Text("Gateway: " + (invite.gateway.host ?? "")).font(.caption)
                        }
                        Button("Scan invitation QR") { scanningInvite = true }.disabled(model.busy)
                        if let setup = try? VoxkinOwnerSetup(model.invitationText) {
                            Text("Owner gateway: " + setup.gateway.absoluteString).font(.caption)
                            Text("Owner setup grants broader gateway access, not restricted phone access. Only import your own trusted gateway. This code contains a credential; keep it private.").font(.caption)
                            Button("Use owner setup") { model.importOwnerSetup() }.disabled(model.busy)
                            Text("Import only fills the connection fields. Review them, then tap Connect; gateway pairing approval may be required.").font(.caption)
                        } else {
                            Button("Enroll this phone") { Task { await model.joinInvitation() } }.disabled(model.busy || (try? VoxkinInvitation(model.invitationText)) == nil)
                            Text("Only enroll with an owner you trust. Enrollment sends this invitation to the displayed gateway. Owner approval is required before access activates.").font(.caption)
                        }
                    }
                }
                Section(model.restrictedMode ? "Restricted gateway" : "Owner gateway") {
                    if model.restrictedMode && !model.enrolledDeviceID.isEmpty { Text("Phone ID: " + String(model.enrolledDeviceID.suffix(8)) + ". If approval is pending, compare this with the owner.").font(.caption) }
                    TextField("wss://your-gateway", text: $model.endpoint).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        .disabled(model.connected || model.busy).accessibilityIdentifier("gatewayURL")
                    if !model.restrictedMode {
                    SecureField(model.hasSavedCredential ? "Saved in Keychain" : "Pairing token", text: $model.bootstrap)
                        .disabled(model.connected || model.busy).accessibilityIdentifier("pairingToken")
                    Toggle("Request access-management permission", isOn:$model.requestAdmin).disabled(model.connected || model.busy)
                    if model.connected {
                        Text(model.adminPermissionGranted ? "Admin permission: approved for this connection." : "Admin permission: not granted. Disconnect, enable the request, then reconnect for gateway approval.").font(.caption)
                        Text(model.supportsAccessManagement ? "Phone-management API: available." : "Phone-management API: unavailable. The gateway owner must install or enable a compatible Voxkin access-management plugin. Reconnect after it is ready.").font(.caption)
                        Text("Disconnect to change the permission request. This toggle does not install or enable the gateway plugin.").font(.caption)
                    } else if model.busy {
                        Text("Wait for the connection to finish, or cancel it, to change this permission.").font(.caption)
                    }
                    Text("Requests permission to manage phone invitations and approvals. Requires gateway approval (operator.admin); a shared token alone does not grant it.").font(.caption)
                    }
                    if model.connected { Button("Disconnect") { model.disconnect() } }
                    else if model.busy { Button("Cancel connection") { model.disconnect() } }
                    else { Button("Connect") { Task { await model.connect() } } }
                    Text("Connects to your saved gateway when this app opens. Microphone and messages always wait for you. Tailscale must be connected separately.").font(.caption).foregroundStyle(.secondary)
                    Text(model.status).font(.caption).accessibilityIdentifier("settingsStatus")
                }
                if model.canManageAccess {
                    Section("Manage access") {
                        Button("Refresh phones and invitations") { Task { await model.refreshAccess() } }
                        Picker("Allowed agent", selection:$model.inviteAgent) { ForEach(model.accessAgents,id: \.self) { Text($0).tag($0) } }
                        TextField("Phone label",text:$model.inviteLabel)
                        Button("Add phone") { Task { await model.manageAccess("invite") } }.disabled(model.inviteAgent.isEmpty || model.inviteLabel.isEmpty)
                        if !model.shareInvitation.isEmpty {
                            ShareLink(item:model.shareInvitation) { Label("Share invite",systemImage:"square.and.arrow.up") }
                            if let qr = invitationQR(model.shareInvitation) { Image(uiImage:qr).interpolation(.none).resizable().scaledToFit().frame(width:220,height:220).accessibilityLabel("Single-use enrollment invitation QR") }
                            Text("Expires in 10 minutes. Share privately; approve the matching phone after enrollment.").font(.caption)
                        }
                        ForEach(model.accessDevices,id: \.self) { device in
                            VStack(alignment:.leading) {
                                Text((device["label"] ?? "Phone") + " · " + (device["status"] ?? ""))
                                Text(String((device["id"] ?? "").suffix(8)) + " · " + (device["agentId"] ?? "")).font(.caption)
                                if device["status"] == "pending" { Button("Approve phone") { Task { await model.manageAccess("approve",id:device["id"] ?? "") } } }
                                Button("Revoke phone",role:.destructive) { Task { await model.manageAccess("revoke",id:device["id"] ?? "") } }
                            }
                        }
                        ForEach(model.accessInvites,id: \.self) { invite in
                            Button("Cancel invite: " + (invite["label"] ?? "Phone"),role:.destructive) { Task { await model.manageAccess("cancel",id:invite["id"] ?? "") } }
                        }
                    }.task { await model.refreshAccess() }
                }
                if model.connected {
                    Section("Agent") {
                        Picker("Talk to", selection: $model.selectedAgent) { ForEach(model.agents, id: \.self) { Text($0.capitalized).tag($0) } }
                            .disabled(!idle || model.activeRun != nil)
                            .onChange(of: model.selectedAgent) { old, new in if !old.isEmpty && old != new { model.changeAgent() } }
                    }
                }
                Section("Voice") {
                    Toggle("Allow gateway speech", isOn: $model.acceptedFallback).disabled(working || recording || model.busy || model.endpoint.isEmpty)
                    Text("Uses the gateway’s configured voice and fallback providers. Reply text may be sent to those providers. Your acceptance is remembered for this gateway.").font(.caption).foregroundStyle(.secondary)
                    Toggle("Conversational feedback", isOn: $model.spokenAcknowledgment)
                    Text("Plays a subtle local sound when your spoken request is accepted. For longer work, may speak one real agent update using your selected voice; quick answers skip the update. No canned spoken acknowledgment. Restricted connections currently support the sound and final answer only.").font(.caption).foregroundStyle(.secondary)
                    if !model.provider.isEmpty { LabeledContent("Audio provider", value: model.provider) }
                    Picker("Speech source", selection: Binding(get: { model.selectedVoiceID.isEmpty ? "gateway" : "local" }, set: { source in
                        if source == "gateway" { model.selectVoice("") }
                        else if let first = model.localVoices.first { model.selectVoice(first.id) }
                    })) {
                        if !model.restrictedMode { Text("Gateway default").tag("gateway") }
                        if !model.localVoices.isEmpty || !model.selectedVoiceID.isEmpty { Text("Local voices").tag("local") }
                    }.disabled(!model.canSelectVoice)
                    if !model.selectedVoiceID.isEmpty {
                        if let selected = model.selectedVoice {
                            Picker("Language", selection: Binding(get: { selected.language }, set: { language in
                                if let voice = model.localVoices.first(where: { $0.language == language }) { model.selectVoice(voice.id) }
                            })) {
                                ForEach(Array(Set(model.localVoices.map(\.language))).sorted(), id: \.self) { language in
                                    Text(language == "es" ? "Español" : "English").tag(language)
                                }
                            }.disabled(!model.canSelectVoice).accessibilityIdentifier("voiceLanguage")
                            Picker("Voice", selection: Binding(get: { model.selectedVoiceID }, set: { model.selectVoice($0) })) {
                                ForEach(model.localVoices.filter { $0.language == selected.language }) { voice in Text(voice.name).tag(voice.id) }
                            }.disabled(!model.canSelectVoice).accessibilityIdentifier("localVoice")
                            Button("Preview voice", systemImage: "play.circle") { model.previewVoice() }
                                .disabled(!model.canSelectVoice || !model.acceptedFallback)
                            Text("Language sets dictation and the reply preference. Voice selection applies to this phone, not other gateway users.").font(.caption).foregroundStyle(.secondary)
                        } else { Text("Your saved local voice is unavailable. Select Gateway default or restore the plugin; no automatic fallback is used.").font(.caption).foregroundStyle(.orange) }
                    }
                    if model.previewing { Button("Stop preview", systemImage: "stop.fill") { model.stopPlayback() } }
                    Text(model.voiceCatalogStatus).font(.caption).foregroundStyle(.secondary)
                }
                Section("Replies") {
                    Picker("Reply length", selection: $model.replyLength) {
                        ForEach(ReplyLength.allCases) { length in Text(length.title).tag(length) }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("replyLength")
                    Text(model.replyLength.detail).font(.caption).foregroundStyle(.secondary)
                    Text("You can always ask for more or less detail. Applies to new messages from this app; saved on this phone.").font(.caption).foregroundStyle(.secondary)
                }
                Section("Last response timing") {
                    if model.timing.firstAudio == nil && model.timing.reply == nil {
                        Text("Send a message to see where the wait happens. Durations stay in memory on this phone.").font(.caption).foregroundStyle(.secondary)
                    }
                    if let value = model.timing.endOfTurn { timingRow("Last word update to Send", value) }
                    if let value = model.timing.acknowledgment { timingRow("Send to receipt cue", value) }
                    if let value = model.timing.transcription { timingRow("Finishing dictation", value) }
                    if let value = model.timing.firstUpdate { timingRow("First text update", value) }
                    if let value = model.timing.reply { timingRow("Complete reply", value) }
                    if let value = model.timing.speech { timingRow("Speech preparation", value) }
                    if let value = model.timing.speechRequest { timingRow("Gateway audio request", value) }
                    if let value = model.timing.playbackSetup { timingRow("Playback setup", value) }
                    if let value = model.timing.firstAudio { timingRow("Send to answer audio", value) }
                    Text("Reply/update times start at Send. Speech preparation is split into the gateway audio request (including transfer) and local playback setup. No conversation text or recordings are logged.").font(.caption).foregroundStyle(.secondary)
                }
                Section("Privacy & connection") {
                    Text("Address and credentials stay in this iPhone’s Keychain, without iCloud sync. Dictation is on-device. No local conversation history or audio files are saved. Gateway history follows its own settings.").font(.caption)
                    Text("Locking stops phone-only audio. An active CarPlay conversation can continue; use Stop voice on the car screen. Returning reconnects without replay. Stop audio does not cancel agent actions; Cancel request asks the gateway to stop remaining work.").font(.caption)
                    Button("Forget this gateway", role: .destructive) { confirmForget = true }.disabled(model.busy || working || recording)
                }
            }.scrollContentBackground(.hidden)
                .background(VoxkinBrand.background)
                .navigationTitle("Settings")
                .toolbarBackground(VoxkinBrand.navy, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .confirmationDialog("Forget this gateway on this iPhone?", isPresented: $confirmForget, titleVisibility: .visible) {
                    Button("Forget gateway", role: .destructive) { model.forget() }
                } message: { Text("Removes the saved address, credentials and speech acceptance. It does not revoke the device on the gateway.") }
        }
    }
}

private func invitationQR(_ value: String) -> UIImage? {
    let filter = CIFilter.qrCodeGenerator(); filter.message = Data(value.utf8)
    guard let output = filter.outputImage?.transformed(by:CGAffineTransform(scaleX:8,y:8)), let cg = CIContext().createCGImage(output,from:output.extent) else { return nil }
    return UIImage(cgImage:cg)
}
private struct InvitationScanner: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    func makeUIViewController(context:Context) -> ScannerController { ScannerController(onScan:onScan) }
    func updateUIViewController(_ controller:ScannerController,context:Context) {}
}
private final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let capture = AVCaptureSession()
    private let onScan:(String)->Void
    private var delivered=false
    init(onScan:@escaping(String)->Void) { self.onScan=onScan;super.init(nibName:nil,bundle:nil) }
    required init?(coder:NSCoder) { fatalError("Not supported") }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        AVCaptureDevice.requestAccess(for:.video) { [weak self] allowed in
            DispatchQueue.main.async { guard let self else { return }; if allowed { self.configure() } else { self.showError() } }
        }
    }
    private func showError() {
        let label=UILabel(frame:view.bounds);label.text="Camera unavailable. Close and paste an invitation instead.";label.textColor = .white;label.numberOfLines=0;label.textAlignment = .center;view.addSubview(label)
    }
    private func configure() {
        guard let device=AVCaptureDevice.default(for:.video),let input=try? AVCaptureDeviceInput(device:device),capture.canAddInput(input) else { showError();return }
        capture.addInput(input)
        let output=AVCaptureMetadataOutput();guard capture.canAddOutput(output) else { showError();return };capture.addOutput(output)
        output.setMetadataObjectsDelegate(self,queue:.main);output.metadataObjectTypes=[.qr]
        let preview=AVCaptureVideoPreviewLayer(session:capture);preview.frame=view.bounds;preview.videoGravity = .resizeAspectFill;view.layer.addSublayer(preview)
        DispatchQueue.global(qos:.userInitiated).async { [capture] in capture.startRunning() }
    }
    override func viewDidDisappear(_ animated:Bool) { super.viewDidDisappear(animated);DispatchQueue.global(qos:.userInitiated).async { [capture] in capture.stopRunning() } }
    func metadataOutput(_ output:AVCaptureMetadataOutput,didOutput metadataObjects:[AVMetadataObject],from connection:AVCaptureConnection) {
        guard !delivered,let value=(metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue, VoxkinSetupCode.accepts(value) else { return }
        delivered=true;onScan(value)
    }
}
