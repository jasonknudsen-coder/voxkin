# Draft beta metadata

## Beta description

Voxkin is a native voice client for your own OpenClaw gateway. Speak to your
assistant, hear its answers, or type when you prefer. Your gateway controls the
agent and speech providers; Voxkin has no vendor-operated conversation backend.

A compatible, reachable OpenClaw gateway is required. Owners can optionally
configure the Voxkin plugin for local English/Spanish voices and restricted
phone invitations. Restricted mode currently supports conversation and voice,
not agent tools. This beta is not an emergency or driving-safety service.

## What to test

- Owner connection and explicit gateway device approval.
- Short-lived invite sharing, QR/paste enrollment, pending owner confirmation.
- Assigned-agent access; no access to another person's runs.
- English/Spanish dictation and local voice preview/replies.
- Stop playback versus Cancel request; lock/background behavior.
- Revocation, reconnect, loss of network and two simultaneous phones.

CarPlay is not enabled in the initial ordinary TestFlight build. It requires
separate Apple entitlement approval and a capability-enabled build.

## Review information to supply before submission

Provide a non-personal review gateway with synthetic data and least-privilege
credentials through Apple's private review fields. Supply actual contact name,
email and telephone. Do not put private access credentials in public metadata.
The review path must be tested outside the owner's private network.

Privacy/support URLs and feedback email must be completed with real reachable
owner-approved destinations; do not submit placeholder URLs.
