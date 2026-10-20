# AI-response reporting — approved implementation candidate

Google's AI-generated-content policy says covered apps must support in-app user
reporting/flagging of offensive content to developers without exiting the app:
https://support.google.com/googleplay/android-developer/answer/13985936

Voxkin's general-purpose agent conversation experience presents generated content.
A bring-your-own-gateway design is not evidence of an exemption. The prior Play candidate lacked a report mechanism. Android 0.2.18 adds a local implementation candidate. Opening an email app would not satisfy the quoted in-app
requirement by itself. A local-only report or report sent only to the user's agent
is not a developer reporting channel either.

## Recommended minimum flow

- Add “Report response” to final assistant messages in Talk and History.
- Open a native sheet showing the exact selected reply, a reason and optional note.
  Let the user remove sensitive text or cancel. Do not attach prior messages,
  gateway addresses, prompts, credentials, raw audio or diagnostics automatically.
- Explain that explicit Submit sends the reviewed report to Builder of Things
  Studio, not just to their own gateway. No automatic reporting/telemetry.
- Submit to a small dedicated publisher-operated HTTPS report endpoint; no gateway
  token is sent. Bound payload size, rate-limit abuse and avoid body logs.
- Keep the sheet open on failure, show truthful delivery status, no silent retries
  or duplicate submissions. Provide a report reference only on server acceptance.
- Set and implement a bounded retention/deletion policy, with authorized publisher
  access and a real triage process. Proposed starting retention:30days; not an
  existing implemented promise. Update privacy/Data Safety/store wording accordingly.

Jason approved this optional manual data flow on October 20, 2026. Android UI,
strict dedicated client and isolated receiver are implemented locally. See
`ops/reporting/` for tests, retention, triage and deployment candidates.
No production endpoint activation or privacy deployment is authorized by that
product approval. The candidate cannot be submitted as functioning reporting
until the deployed path has passed an explicit synthetic receipt/triage/deletion
check. Existing 0.2.17 package receipts describe the earlier artifact only.
