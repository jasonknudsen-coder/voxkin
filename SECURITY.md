# Security

Voxkin is a native iOS/Android client for user-administered OpenClaw gateways.
Store distribution does not make it a hosted service or hostile multi-tenant sandbox.
Do not file secrets or private conversations in an issue. Report vulnerabilities
privately to the repository owner using the repository's private reporting route
if available; otherwise request a private contact before sharing details.

Owner mode requests gateway operator read/write scopes. Choosing an agent is not
an authorization boundary. Use only gateways and agents you trust, with trusted
WSS endpoints. Never enter model-provider API keys into the pairing-token field.

Owner setup codes must be scanned or pasted inside Voxkin; reusable owner secrets
are never generated as externally dispatchable custom-scheme links. External
links are for guest invitations only; guest enrollment still requires owner approval.
Legacy owner links are accepted only via explicit in-app scan/paste.

iOS pairing credentials are endpoint-bound and stored in non-syncing,
AfterFirstUnlockThisDeviceOnly Keychain storage. They remain available while the
phone is locked after its first unlock following reboot, to support CarPlay
reconnection. Existing unlock-only items migrate in place after successful access;
an updated installation needs one unlocked connection before locked use.
Android uses Keystore-backed encrypted credential storage with backup/transfer
excluded. Forget removes local credentials; revoke a device separately on the gateway.
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

## Local speech and release provenance

The offline worker runs a configured Python executable with an argument array,
not a shell. Speech arrives on stdin. The worker uses Python isolated mode and
an OS-environment allowlist, excluding gateway/provider credentials and Python
or dynamic-loader injection variables. Runtime files and installed dependencies
remain trusted, owner-managed code. Setup verifies model SHA-256; the startup
size/mtime receipt is only a readiness check, not tamper-proof attestation.
Python pins are not yet platform-specific wheel hash locks. Native dependency
curation and external security assessment remain separate follow-up work.

Review source and exact signed/package artifacts separately. A source test pass
is not evidence that an already installed plugin or store binary has those fixes.
