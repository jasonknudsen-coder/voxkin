# Contributing to Voxkin

These instructions apply to the standalone source distribution. Source availability
does not imply a published npm package or App Store build.

## Local development

Use macOS with full Xcode and an iOS 17+ SDK. The core Swift package requires
macOS 13+. No third-party Swift packages are required. Follow README build/test
commands, then the getting-started guide for a gateway you control. Set your own
signing team locally; never commit provisioning profiles or signing credentials.
For a separately signed personal fork, choose your own bundle identifier; changing
it creates a new app identity and does not migrate an existing user's Keychain.

For plugin development, use Node24.16+ within the24.x series (or another version
supported by the pinned OpenClaw host). Install the pinned developer dependencies:

```sh
npm ci --prefix plugin --ignore-scripts --legacy-peer-deps
npm test --prefix plugin
```

The committed lockfile covers plugin dependencies; `--legacy-peer-deps` avoids
implicitly installing the separately managed OpenClaw peer and its entire host
runtime. This does not relax the supported host version: integration requires
OpenClaw2026.9.7. Tests requiring a host/runtime may skip when it is not supplied;
report those skips rather than claiming full host qualification.

Run `swift test` using the documented DEVELOPER_DIR, the app/UI xcodebuild tests,
and `npm test --prefix plugin` for plugin changes. Keep simulator/mocked evidence
separate from physical microphone, audio-route, and live gateway checks.

## Changes

Keep changes focused and document user-visible behavior. Preserve explicit
Stop/Cancel semantics, on-device-only recognition, and no replay on reconnect.
Never submit real endpoints, credentials, device IDs, conversation transcripts,
private screenshots, or model assets. Use synthetic examples and example.com.

Legacy bundle, Keychain, RPC, provider and session identifiers are compatibility
contracts; see docs/voxkin-rename.md before changing them. Other backends are a
future direction, not currently supported adapters.

Project source uses Apache-2.0. Keep third-party license notices and review runtime
redistribution obligations separately. Contributions must be yours to contribute;
branding does not grant trademark rights beyond the license terms.
