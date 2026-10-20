# Website candidate — October 14, 2026

- Static landing, privacy and support pages prepared for voxkin.builderofthings.studio.
- Contact: jason@builderofthings.studio, selected by owner; TestFlight feedback saved.
- Local HTTP checks: all three routes and stylesheet returned 200. Internal links
  resolve; no scripts/third-party fonts or former nonworking support address.
- On-device recognition statement checked against SpeechCapture.swift.
- Site include and the existing full review host Caddy configuration plus candidate
  both parse using the host's installed Caddy 2.6.2 (formatting warning only).
  Initial attempt using '-' failed; /dev/stdin succeeded. No config was installed.
- Browser policy blocked navigation to the local preview. Desktop/mobile visual
  inspection remains unverified; no alternative route was used to bypass the block.
- Read-only host inspection found Caddy active and no existing Voxkin site route
  or application directory. Host fingerprint matched the documented ED25519 pin.
- No DNS, deployed files, Caddy reload, unrelated project changes, live voice changes,
  reviewer credentials, or public Apple URLs were changed.

Deployment scope: only immutable static files, isolated Voxkin Caddy include,
DNS/TLS for the chosen hostname, and public reachability verification. This does
not authorize a review gateway, plugin promotion, repository publication or app
release. Production approval required under workspace AGENTS.md.

## Production verification

Owner supplied the exact production authorization phrase on October 14. Approved
static content from 9f348e7 is live at https://voxkin.builderofthings.studio/.
Cloudflare-proxied DNS points to review host; Caddy obtained a public TLS certificate.
Initial certificate request preceded DNS and failed; automatic retry succeeded.
Initial static requests returned403 because Caddy could not traverse protected
review host app directories. Public files were placed under /var/www/voxkin instead;
private application-directory permissions were not changed. Caddy root was updated.
Cache-Control no-transform prevents Cloudflare email rewriting from injecting a
script blocked by the site's CSP. Content of all three pages and CSS matches the
approved source via public HTTPS. Python urllib requests returned403; curl and
browser checks succeeded. No global Cloudflare security settings were changed.
Desktop and390px mobile landing screenshots visually reviewed after public launch.
Privacy/support content and HTTP checked; not separately visually reviewed.
review host edge health passes, Caddy active, zero failed systemd units.
ASC beta marketing/privacy URLs and draft store marketing/support URLs saved200.
No review submission, invitations, live plugin change or unrelated project change.

## Policy/support update — October 15, 2026

Owner supplied the production authorization phrase for the prepared website update.
Immutable snapshot `b002bc2a4acd181789262e171e857fe9e33ecb49` deployed via atomic
current symlink switch; previous release `9f348e766318e15d9f379ca818799408bbc411bc`
preserved for rollback. Public landing, CSS, privacy and support returned HTTPS 200
and matched source SHA256 hashes at 14:20 UTC. Landing/CSS unchanged. Caddy active,
no failed systemd units; privacy response security headers intact. Exact receipt:
`.tmp/site-policy-live-receipt.json`. Updated pages not visually browser-reviewed.
No DNS/config reload, gateway mutation, app submission or repository publication.

## October 15 platform-matrix candidate (not yet deployed)

Added `/platforms/` with tested gateway targets and a coverage matrix separating
macOS, full Ubuntu container gateway/phone checks, Debian component-only checks,
and native patch-free Windows. Lists tested versions, hook permission, user-report
versus automated evidence, and unqualified architectures/harnesses. Landing and
navigation link to it; removed stale pending-CarPlay-approval wording.

Fetched the current live landing page and confirmed it lacks this matrix.
Local Chromium checks at 1280x900 and 390x844 loaded the page without document
horizontal overflow; table scrolls inside its named region. Mobile screenshot
inspected; local links validated. Source screenshots remain under ignored `.tmp/`.
Publication requires a new production authorization for the committed candidate.

## Platform matrix production verification — October 15, 2026

Maintainer supplied the production confirmation for committed candidate
`b5a0c56f48fafcbcacfacb749fa81a7ff53ce434`. Deployed only its `site/` archive,
verified the archive SHA256 on the host, and atomically switched the current
symlink. Previous release `b002bc2a4acd181789262e171e857fe9e33ecb49` remains
available through the rollback pointer. No Caddy configuration/reload or DNS
change was needed; Caddy and the existing edge health check stayed healthy.

Public HTTPS landing, platforms, privacy, support and stylesheet all match the
approved Git source byte-for-byte. Platforms returns200 with CSP, nosniff and
no-transform headers intact. Browser checks verify all four matrix rows and no
viewport overflow at390px and1280px. Receipt and live captures are in ignored
`.tmp/site-platform-promotion/`. No gateway, TestFlight or public App Store change.

## CarPlay wording correction — October 15, 2026

Approved snapshot `be33132678e85f774b112bf48b2c7f2c5fa06303` deployed atomically;
previous platform-matrix snapshot preserved. Homepage now describes CarPlay voice
conversations with iOS26.4+ and setup-before-driving note. Platforms page distinguishes
verified release packaging from unclaimed universal vehicle/audio-route testing.
All five public routes match approved source bytes over HTTPS; Caddy remains active.
No DNS, Caddy configuration, gateway or App Store changes. Static text-only edit;
no new browser layout validation in this pass. Receipt: `.tmp/site-carplay-promotion/receipt.json`.

## October20 Android reporting/privacy activation

Jason supplied exact production authorization; clarified no Voxkin backup set.
Approved4234e9e static snapshot activated, previous pointer preserved. Isolated
report receiver enabled under DynamicUser and loopback-only8793; only Voxkin's
Caddy include changed, full config validates. Five public assets match approved
source; edge stays healthy. HTTPS201 new and200 duplicate receipts passed, invalid
allowlist payload rejected400. Synthetic report reviewed/deleted and aged-row
purge verified on host. No actual customer report read. App remains unsubmitted.
Live app backup script hashes to repository baseline and does not cover report
state path. Provider snapshots were not inspected; owner's no-backup confirmation
is the evidence, not a verified disabled provider setting. No disk-encryption
claim. Receipt in ignored `.tmp/reporting-deployment/live-receipt.json`.
