# Google Play reviewer access — persistent isolated grant

A dedicated reusable Play invitation is stored in protected operational storage
and supplied only in Play Console's private App access instructions. It is not the
Apple invitation. Never put it in the listing, screenshots, source control or chat.
The dedicated Play grant has no scheduled expiry; it remains independently
revocable. Existing Apple and transitional review devices retain their original
fixed window ending **October 28, 2026, 15:02:32 UTC**. No legacy device was promoted.

## Private reviewer instructions (insert invitation privately)

1. In Settings, disconnect any previous gateway. Paste the invitation into
   **Paste Voxkin setup or invitation**, tap **Use setup link**, then
   **Enroll this phone** and **Connect**. Approval is automatic in this isolated
   environment; ignore the generic owner-approval message. No account, payment,
   VPN or owner/admin token is needed. Reconnect with saved access on later visits.
2. In Talk, use **Type instead** to send a short question. Speech input requires
   microphone permission and a supported installed on-device language model.
   Typing remains available without a recognition model.
3. **Settings → Allow gateway speech** enables generated reply audio. Select an
   available voice and Preview voice. History supports reviewing conversations.
4. Final replies expose **Report response**. Inspect/edit the excerpt, choose a
   reason, optionally add a note, then explicitly submit. Reports go to the
   publisher only after confirmation, not to the review assistant.

Restricted review access intentionally excludes owner administration and agent
tools. There is no Android Auto experience. The compact Console version includes
setup and full private invitation in fewer than 500 characters.

## Verification — October 20, 2026

- 15 enrollment/lifecycle/transport/monitor tests passed. Persistent Play access
 survives a saved-state restart and a simulated year beyond Apple expiry; Apple
 devices and enrollment deny after their unchanged expiry. Individual revocation,
 grant rotation/removal, invalid configuration and existing caps are covered.
- Production gateway-only overlay preserves Apple bootstrap/expiry, existing
 device records and accounting. Relay/ingress unchanged. Exact prechange config,
 Compose and health-hook backups are retained privately for rollback.
- Dedicated permanent Play enrollment, restricted hello, voice catalog, synthetic
 model reply, 122,058-byte English WAV, history/delete and saved-device reconnect
 passed over public HTTPS. Original Apple invitation remains accepted. Health
 hook passed as the actual restricted monitoring account. The new saved Play
 credential also authenticated after a second graceful production gateway restart.
- Earlier transitional Play grant passed signed Android 0.2.18 release enrollment,
 typed model reply and background/reopen on an isolated API35 emulator. Release
  0.2.19 additionally passed install/launch and Settings privacy-link opening the
 current public policy. These Android runs are distinct from the new permanent
 grant's API qualification. Microphone and audible Android playback were not
 tested in this pass. Physical Pixel was untouched.

## Operational bounds and privacy facts

- Permanent credentials do not remove the existing 20-device capacity, 60/hour rate
 limit, 100-total-request allowance, single-flight limit or 1,024-token reply cap.
 At the current qualification checkpoint, 14/100 requests were used. Monitor and
 maintain capacity; do not silently reset accounting or imply unlimited service.
- Hash-bound Play labels persist with device records. Removing/rotating the
 permanent hash also denies associated devices. Legacy Apple labels remain
 expiry-bound. Health output distinguishes Apple expiry from Play availability
 without relaxing the 85-request operator-alert threshold.
- Review history/text persists in private OpenClaw SQLite state. A historical
 maintenance backup archive exists; do not describe the review environment as
 storing no data or having no backups. Expiry/revocation does not delete records.
- Docker diagnostics rotate by size (10MB×3), not a verified time limit. The
 dedicated Caddy source does not enable an explicit access log; provider/edge
 diagnostic retention is not independently established.
- The model relay uses OpenAI chat completions, `gpt-4.1-mini`, `store:false`, no
 tools and bounded requests. This does not establish zero provider retention/ZDR.
- Scheduled game-lab backups enumerate app `data` directories; this review service
 uses `state` and is excluded from that job. This is not a claim about hosting
 provider backups. Publisher privacy documentation discloses review storage and
 provider processing rather than claiming no collection.
