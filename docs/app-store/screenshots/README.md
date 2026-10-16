# Store screenshots — October 15, 2026

Two selected, unretouched 1320×2868 RGB screenshots of the actual SwiftUI app,
rendered on an iPhone 17 Pro Max simulator. No screenshot compositing, generated UI,
real conversations, credentials, invitations or private gateway addresses.

- `01-talk.png`: idle connected Talk interface; fictional Personal agent.
- `02-conversation.png`: fictional prompt/reply rendered in the real conversation UI.

Captured with simulator-only `-store-screenshots` and
`-store-screenshot-reply` launch flags in `VoxkinApp.makeModel`. These fixtures are
compiled out of physical-device/archive builds and perform no network calls.
`VoxkinUITests/testStorefrontScreenshots` captures attachments. Source uses the
existing mock transport, but content is illustrative, not live-model evidence.

Evidence: `.tmp/storefront-large.xcresult`, `.tmp/storefront-large.log`.
A settings-screen candidate was inspected and rejected because the scroll framing
was unsuitable; it was not uploaded. The selected files were visually inspected.

Apple upload: existing en-US version 1.0 draft, `APP_IPHONE_67` API display set
(Apple accepts this 1320×2868 large-Dynamic-Island size), both assets COMPLETE with
no errors. Receipt: `.tmp/storefront-screenshot-receipts.json`.
No public release or submission occurred. Do not call these physical-device QA.
