# Android Data Safety worksheet — not an attestation

## Source-backed app behavior

- No advertising, analytics or crash-reporting SDK in the Android client.
- Raw microphone audio goes to Android's on-device recognizer, with no app-enabled
  cloud fallback. Device/OS services have their own policies.
- Recognized/typed text and requested conversation history go to the chosen gateway
  over TLS. Its configured model/tools/speech providers may process that text.
- Owner/restricted identity and credentials are encrypted locally with Android
  Keystore-protected AES-GCM. Backup is disabled. Forget is local, not server deletion.
- Camera is used for locally decoding gateway setup/invitation QR codes; the app
  does not publish camera images. QR credentials are sensitive and excluded from art.
- Reply speech is temporarily cached in app-private files. Normal cleanup deletes
  those files; interrupted cleanup may leave cache behind. Do not promise zero disk use.
- Gateway conversation history/retention is independent of local phone state.

## Must resolve before Console answers

Play definitions of collection/sharing, optional use, ephemeral processing and
service-provider exceptions must be applied to the final data flows, not inferred
from “no vendor AI backend.” In particular, publisher-operated reviewer access is
not a third-party user's independent gateway. It processes/retains submitted text,
responses, access identifiers and technical logs; expiration is not deletion.
Confirm actual retention/deletion mechanism, downstream model processing and privacy
URL before selecting categories. User content/messages and identifiers are likely
relevant to that separate environment; this worksheet is not final categorization.

Any in-app reporting service adds an explicit optional publisher data flow and must
be reflected here, in the public policy, in-app consent and store declarations.
No reporting collection exists in the current candidate.

Support: jason@builderofthings.studio. Do not collect credentials, invitation tokens,
raw audio or whole conversation history in a report by default.

## Approved optional Android reporting addition

Candidate 0.2.18 adds explicit user-submitted selected/edited response text, reason,
note and random report reference to the publisher receiver. Treat as optional
collection for safety/developer review, not automatic telemetry; determine the
Console's applicable user-generated-content and other-data categories before
attesting. Submitted text can contain personal information chosen by the user.
No full history, gateway credentials, audio or device identifier is attached.
Receiver retention is 30 days with deletion tooling; verify deployed operations,
logging exclusions and backup exclusions before publishing those promises.
