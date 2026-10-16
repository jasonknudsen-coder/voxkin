# Restricted phones on a shared gateway (experimental)

Voxkin can use two distinct connection modes. **Owner mode** uses OpenClaw's
operator WebSocket API. **Restricted mode** uses the existing Voxkin plugin's
`POST /voxkin/v1` channel endpoint on the same gateway origin and port. Restricted
credentials cannot be substituted for gateway/operator credentials. There is no
Voxkin cloud service, new public listener, phone-number lookup, or SMS dependency.

This is an opt-in implementation, disabled by default. It is not yet a claim of
production qualification: real two-phone enrollment/share-sheet/QR, approval,
revocation and simultaneous voice operation must be verified before rollout.

## Security boundary and current limitations

The plugin authenticates a phone and determines its assigned agent server-side.
The phone cannot supply an agent ID, session key, tool policy, or operator method.
Agent `main` is excluded from enrollment. Use a separate agent with a separate,
non-sensitive workspace, bootstrap files and memory configuration. The initial
restricted channel disables **all agent tools and text commands**, regardless of
that agent's normal tool configuration. It supports immediate text replies and
local speech; it does not support autonomous actions, attachments, proactive or
deferred replies, or transcript browsing. Adding tool access needs a separate
reviewed policy; do not remove the no-tools gate casually.

This protects the restricted interface, not users from the server owner or host
administrator. Shared native plugins, hooks, model providers and bootstrap content
are trusted infrastructure. Tool disabling does not sanitize those sources. A
shared host is not a hostile-tenant sandbox. Use independent gateway/OS boundaries
when that is your requirement. Never enroll a private administrative workspace.
Existing WhatsApp or other channel bindings do not automatically grant Voxkin access.

## Gateway setup

1. Install the plugin as described in [plugin setup](../plugin/README.md), retaining
   its stable ID `voxkin-local`. Configure and verify local voices if speech is
   desired. No global TTS override is required.
2. Create a separate agent using OpenClaw's documented agent setup. Confirm its
   workspace contains only information intended for that user. Do not alias its
   workspace to the owner's. Review host hooks and model-side shared context.
3. Add this object to the plugin's existing configuration (example, not a complete
   OpenClaw configuration):

   ```json
   {
     "access": {
       "enabled": true,
       "stateDir": "/absolute/private/path/voxkin-access",
       "allowedAgents": ["family-member"]
     }
   }
   ```

   Add every intended agent ID to `allowedAgents`; existing gateway agents and
   other channel bindings are not added automatically. The owner app can select
   eligible agents but cannot edit this list.

   The agent must already exist in the gateway configuration. `main` is never
   eligible. `stateDir` must be dedicated, owner-only (0700); files are 0600. Use
   a local filesystem and one gateway process per access state directory. Keep
   this directory out of repositories, web roots and shared/cloud-synced folders.
4. Reload/restart through your normal deployment procedure. SDK incompatibility,
   missing eligible agents or unsafe state cause startup failure, not fallback to
   owner access. `/voxkin/v1` returns 503 until initialization completes.
5. Serve the existing gateway via trusted HTTPS/WSS. Restricted mode currently
   requires an origin URL (no path prefix, userinfo, query or fragment). Network
   access, including Tailscale if used, must be configured separately on each phone.

The adapter is feature-gated on supported channel context, dispatch, routing and
`runDetachedWebhookWork` SDK APIs. Host contract tests use the installed OpenClaw
2026.9.7 build; a matching version string alone is not a substitute for tests.

## First phone from the host shell

You do not need an already-connected owner phone to issue a restricted invitation.
The packaged [shell onboarding helper](../plugin/README.md) calls the same admin-only
invite/list/approve RPCs through the host's authenticated OpenClaw CLI. It renders
the QR locally; no QR website receives the secret. Restricted access must already
be enabled with an eligible assigned agent. Creating an invitation does not approve
the phone: compare its pending device ID, then approve it explicitly.

For the first **owner** phone, the helper's separate owner setup code fills gateway
and bootstrap-token fields, followed by explicit Connect and normal pairing. That
is broader access, not a restricted invitation, and requires build 5 or later for in-app owner QR scanning.
See the [quick setup and Tailscale guide](getting-started.md#connect-your-own-phone).

## Owner: enroll and manage phones

1. Connect using owner mode. If already connected, Disconnect first. Enable **Request access-management permission** before
   connecting. Have the gateway administrator explicitly approve the device's
   `operator.admin` scope using OpenClaw's pairing workflow. Voxkin cannot approve
   its own elevation. Read/write pairing alone is insufficient. This is a broad
   OpenClaw admin permission, not a new narrowly scoped privilege.
2. With admin scope and compatible plugin methods, Settings shows **Manage access**.
3. Refresh, select an eligible agent, provide a phone label, and tap **Add phone**.
4. Show the QR or tap **Share invite** to open the native iOS share sheet. Send it
   privately via your preferred messenger. Invitations expire after ten minutes.
   Copy/paste works when a messenger does not open custom application links.
5. After the recipient enrolls, refresh. Compare the last eight characters of the
   displayed device ID with the recipient through a trusted channel, then approve
   the pending phone. Labels are owner-assigned, not proof of the recipient's identity.
6. Cancel unused invites or revoke enrolled phones from the same section.

An invite is a bearer capability to request enrollment. Forwarding or interception
can consume it, which is why owner confirmation is required. Link opening and
previews do not redeem it: redemption is an explicit POST after the user taps
**Enroll this phone**. Revoking a phone rejects subsequent requests and clears
its retained response; active work receives cancellation. Cancellation cannot
undo already completed external effects (this release disables tools).

## Recipient: join, approve, then talk

Install Voxkin first. Open the shared `voxkin://join#…` invitation or use
Settings → **Scan invitation QR** / paste invitation. Check the displayed gateway
host, then tap **Enroll this phone**. The app exchanges the one-time invitation
for a pending credential over HTTPS and stores it in device-only Keychain. Tell
its device ID suffix to the owner for approval, then tap **Connect**.

Only the assigned agent is returned. There is no silent operator fallback. Choose
Local voices → Español and a Spanish voice for Spanish dictation/replies/audio.
On-device Spanish recognition must be available on the phone. Voice preferences
remain phone-local. Restricted mode never calls general gateway TTS methods;
local voice failure does not trigger a cloud fallback.

Forgetting local credentials does not revoke them on the server. Reinstallation,
lost enrollment responses, or lost credentials may require a new invitation and
revocation of the old record. The app does not automatically replay enrollment
or message mutations after uncertain outcomes.

## Protocol and operations

- `POST /voxkin/v1`: JSON operations `redeem`, `hello`, `catalog`, `speak`, `send`,
  `poll`, `abort`. All except redemption require `Authorization: Bearer …`.
- Owner RPC: `voxkin.access.list/invite/approve/revoke/cancel`, all requiring
  `operator.admin` and normal host profile admission.
- Random 256-bit invite/device secrets, separate invite/device lookup tables, only hashes persisted.
- Atomic serialized state writes; pending approval expires after one hour.
- Server derives a per-device agent session. Reconnecting resumes that device's
  server conversation; the app displays only the current exchange, not its history.
- One unsettled agent run per phone, 128 retained runs total, 120-second cancellation
  deadline; completed results retained for five minutes. Results do not survive
  plugin/gateway restart. No replay after restart. Idempotency is bounded by that
  retention window and binds the exact text, not just the request ID.
- Polling at 750ms replaces broadcast subscriptions; no global gateway event feed.
  The first version exposes working/final/error status, not detailed tool progress.
- HTTP input/body and concurrency limits plus a global request budget provide basic
  abuse protection, not an Internet-scale multi-tenant quota service. Local speech
  still shares the existing bounded worker capacity.
- Redirects are refused; credentials and invitation secrets are never put in HTTP
  query strings. Invitations use a URL fragment but remain sensitive in messengers
  and clipboard history. Do not log invite strings, bearer headers or response bodies.
- Shutdown aborts and drains work; a drain timeout reports failure. A crashed writer
  leaves an intentional fail-closed lock: see [state recovery](../plugin/access/README.md).
  Never delete a lock before verifying its writer is stopped.

## Contributor checks

```sh
(cd plugin && npm test)
swift test
# Optional: actual installed SDK contract, isolated temporary profile, no model call:
OPENCLAW_SDK_DIR=/absolute/path/to/openclaw/dist/plugin-sdk \
  node --test plugin/restricted/host-contract.test.js
# Optional real isolated gateway load (no model calls):
VOXKIN_HOST_LOAD_TEST=1 node --test plugin/restricted/host-load.test.js
```

Run iOS simulator app/UI tests with Xcode. Security regressions must cover changed
agent/session fields, guessed run IDs, unapproved/revoked credentials, reused and
expired invitations, preview GET requests, cancellation races, concurrent users,
unsafe state files and SDK failure. The installed SDK contract replaces only the
model reply resolver; it does not prove production pairing or physical networking.

Before public release, verify real owner scope upgrade, QR and messenger sharing,
recipient approval and Spanish audio, cross-user rejection, revoke during response,
restart recovery and two simultaneous phones. Never present mock/contract tests as
those device checks. Keep the repository private until separately authorized.
