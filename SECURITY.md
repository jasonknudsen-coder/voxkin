# Security

Voxkin is a personal-testing prototype, not yet a supported public release.
Do not file secrets or private conversations in an issue. Report vulnerabilities
privately to the repository owner using the repository's private reporting route
if available; otherwise request a private contact before sharing details.

The client requests gateway operator read/write scopes. Choosing an agent is not
an authorization boundary. Use only gateways and agents you trust, with trusted
WSS endpoints. Never enter model-provider API keys into the pairing-token field.

Pairing credentials are endpoint-bound and stored in non-syncing,
AfterFirstUnlockThisDeviceOnly Keychain storage. They remain available while the
phone is locked after its first unlock following reboot, to support CarPlay
reconnection. Existing unlock-only items migrate in place after successful access;
an updated installation needs one unlocked connection before locked use.
Forget removes local credentials; revoke a device separately on the gateway.
Stop stops local capture/playback; Cancel requests remote cancellation and cannot
undo side effects. The app does not expose a new unauthenticated proxy service.

Before release, review source AND history for credentials, signing artifacts,
private infrastructure, transcripts, and screenshots. No scan is a guarantee.
Do not rotate/revoke live credentials or rewrite shared history without owner
coordination. Never publish a suspect value in an audit report.

## Restricted access

Review [restricted-access.md](docs/restricted-access.md) for the opt-in phone
credential boundary, no-tools policy, trusted-host assumptions and qualification
limits. Report bypasses of agent, credential, run ownership or admin checks privately.
