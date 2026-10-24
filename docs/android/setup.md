# Native Android preview

This is a Pixel-first development candidate, not a Google Play release. See
[qualification](qualification.md) for evidence and remaining acceptance checks.
The repository remains private. No Android Auto support is claimed.

## Build locally

Install Android Studio, Android SDK platform 36 and a compatible JDK (17 or newer).
Set `ANDROID_HOME` to your SDK directory, or use an untracked
`android/local.properties` containing `sdk.dir=...`.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID is `studio.voxkin.android`. This does not replace the iOS
application or modify the gateway plugin. A debug APK is intended for local
qualification; retain its signing identity for upgrades, and never commit signing
keys. Minimum Android version is 12 (API 31); on-device recognizer availability
still depends on the device and installed language models.

## Connect

1. Ensure the phone can reach your gateway. For a private tailnet address, install
   Tailscale, join the same authorized tailnet and turn its VPN connection on.
2. Open Voxkin Settings. Scan or paste an owner setup code or a restricted invitation
   supplied by the gateway owner, then stage it. Staging does not send credentials.
3. Verify the displayed gateway. For owner access, tap Connect and approve the exact
   native device request in the gateway. For guest access, tap Enroll, have the
   owner approve the displayed phone ID, then tap Connect.
4. Never share setup codes or invitation links in public issues, screenshots, logs or source files.
   A restricted guest grant is separate from an operator device grant.

Owner setup codes from the updated helper are non-URI text and require the updated
app. Scan them inside Voxkin, not the system camera. Legacy owner URLs still work
when deliberately scanned/pasted inside Voxkin, but both cold-start and already-open
external intents reject them (even explicit intents). Guest invitation links remain
supported. Existing paired devices need no new enrollment.

The client requires TLS and rejects redirect-based credential forwarding. Do not
bypass certificate validation to connect a local test server.

## Talk and history

Choose English or Spanish in Settings. On Talk, tap the microphone, wait for
“Listening · speak now”, speak, then tap the Stop square and review the transcript.
The primary control becomes a Send arrow only after transcription finishes.
Send is a separate explicit action; use the keyboard button to type instead. Missing
on-device speech support falls back to typing, not cloud transcription. If final
recognition fails or times out, the last useful recognized words remain in the
draft with an incomplete-transcription warning. Review them before sending;
Cancel still discards the current capture.

Speech source selects **Gateway default** (`tts.speak`) or **Local voices**
(`voxkin.speak`) when advertised by an owner gateway. Restricted access only permits
Local voices. Source choice is saved for this gateway/access mode; no alternate
source is silently substituted. “Local voices” means the gateway plugin's catalog,
not Android text-to-speech.

Android model checks/downloads are under **Troubleshooting → Show on-device
dictation details**. They concern speech input, not the spoken reply source.

Gateway speech is opt-in: reply text may be processed by the configured gateway
speech provider and fallback. Consent and voice selection are remembered for that
gateway after authentication. Preview voice uses the same consent and Stop audio
controls as replies. Brief / Balanced / Full applies only to new messages from
this phone; slash and bang commands are sent unchanged. Android does not replace the gateway's privacy
policy. Leaving the application stops capture/playback and disconnects. Returning
reconnects saved, previously approved access and restores the selected conversation.
No pending message, microphone capture, or speech is automatically replayed. Drafts
survive same-conversation backgrounding only in memory, not process termination.
Explicit Disconnect suppresses this reconnect for the current app process; a fresh
launch may reconnect saved access. Forget removes the saved reconnect permission.
If restoration fails, choose a conversation in History or explicitly start New;
sending remains blocked until a conversation is ready.

Replies can appear progressively under **Reply in progress** when the gateway
provides chat deltas. Only the completed reply is spoken. Speech uses a short
first clip and prepares one following clip during playback; Stop cancels both.
Restricted connections may remain final-only depending on their protocol.

History belongs to the gateway, not a vendor-hosted conversation service. Rows show
timestamps/previews when supplied, and mark the current conversation. Use Load more
conversations and Load older messages to browse additional pages (up to500 per
view). Refresh conversation returns to the latest messages without playing audio.
For unnamed owner conversations, History can derive a title from the first useful
user messages and fill a missing preview. Manual titles and saved automatic titles
are preserved. Once there are two visible user messages and a meaningful topic,
Voxkin saves the automatic title to that exact gateway session when supported.
This uses local text rules and bounded gateway previews, not another language model.
After deleting a conversation, refresh History before continuing through pages.
Delete
requires confirmation and affects the selected gateway conversation. Forgetting
local access is not server-side revocation; revoke a device at the gateway when
needed.

## Owner phone management

While disconnected in owner mode, enable **Request access-management permission**
and reconnect. The gateway must approve operator.admin for this phone and advertise
all five Voxkin access-management methods. The toggle is off on a fresh app process;
ordinary connections remain read/write only. A shared token or the toggle alone does
not grant admin access or install the gateway plugin.

Once permitted, use **Refresh phones and invitations**, select an allowed dedicated
agent and phone label, then **Add phone**. Review and confirm to create an invitation.
The QR and Share action are temporary, expire at the gateway-provided time, and clear
on background/disconnect. The displayed QR blocks screen capture; use the deliberate
Share action privately. No invite is copied to the clipboard or sent automatically.

After enrollment, compare the phone's last eight ID characters with the owner list,
then confirm **Approve phone**. **Revoke phone** and **Cancel invitation** also require
confirmation naming their target. Failed/uncertain mutations require Refresh before
trying again; there is no automatic replay. Existing restricted phones can recover
their ID from the authenticated gateway hello when supported; new enrollments save it
encrypted alongside endpoint-scoped access. Forget removes that local ID, not server access.

## QR setup

In Settings, disconnect first if already connected, then choose **Scan setup QR**.
Allow camera access when Android asks, or cancel and use paste instead. The camera
scanner decodes QR codes on the phone; barcode-image saving is disabled. No separate
scanner app, cloud decoding, clipboard copy or credential logging is used.

A valid owner setup or restricted invitation opens a review showing only the gateway
and access type. **Discard** leaves existing fields unchanged. **Use scanned setup**
fills the fields; you must still explicitly **Connect** or **Enroll this phone**.
Ordinary web links, insecure endpoints and malformed invitations are rejected.
Scanned text is never opened as a browser URL.

Camera cancellation or invalid scans keep the previous fields. Setup suppresses
saved-gateway automatic reconnect, including Activity state restoration; returning
from the scanner does not enroll or connect. An interrupted review is cleared rather
than persisting its credential. Paste/deep-link setup remains available without a camera.

## Hands-free conversation (opt-in)

On Talk, choose **Hands-free conversation**, read the explanation, then confirm
**Start hands-free** and allow microphone access. A ready conversation, speech
consent, available speech source and an empty draft are required. This mode is
never remembered across app exits or reconnects.

Successful final dictation sends automatically. A two-second unchanged-partial
pause requests finalization, but Android's recognizer can finalize earlier.
After the spoken reply finishes, listening resumes after a short gap. Tap the mic
during speech to interrupt the reply and listen; tap Stop to end the mode.
Leaving Talk or leaving the app also ends it. Stop does not cancel a request
already sent: use **Cancel request** for that.

Recognition errors, missing final words, timeouts, oversized drafts and failed
replies end the loop. Recovered partial words remain for review; they never
send automatically. Ordinary recording still uses **record → review → Send**.
Hands-free real EN/ES conversational and Bluetooth/headset acceptance is pending.

## Preview gaps

Camera QR scanning and paste/deep-link setup are supported. Android Auto is not implemented. The Android preview is not yet a claim of full
iOS feature parity; several response/history experience features still differ. Physical microphone/language, routing, lifecycle and live gateway
checks must be recorded before calling it qualified.

See [iPhone presentation parity](iphone-parity.md) for the shared design and
explicit remaining feature differences.


## Conversational feedback

Settings → Voice → **Conversational feedback** is enabled by default, but only
works with speech consent and a finalized, unedited spoken draft. A subtle local
tone confirms the exact request was accepted. For longer owner requests, the first
eligible real public progress update may be spoken once; restricted connections
use only the tone and final answer. Typed/edited drafts never trigger this feedback.
The final answer interrupts feedback. You can turn feedback or speech consent off
while waiting; re-enabling does not revive feedback for the current turn.
