# Apple review access template — Ubuntu

Operator template, not a working public demo. Replace `review.example.com`, the
private invitation and `[REVIEW_EXPIRY]` with your isolated deployment details
before sharing privately with Apple. No reviewer credentials ship with this source.

Voxkin connects to an OpenClaw gateway selected by the user. For Apple review, use this isolated, publicly reachable review assistant. It runs on our Ubuntu review service; it has no family data, owner/admin access or agent tools. No Tailscale, VPN, separate account, payment or owner pairing token is required. Internet access over Wi-Fi or cellular is sufficient.

SETUP
1. Open Voxkin and select Settings. If already connected to another gateway, tap Disconnect.
2. Under Set up this phone, paste the entire private invitation below into Paste Voxkin invitation. Alternatively, open the invitation URL on the iPhone to fill that field.
3. Tap Enroll this phone. This isolated review service approves enrollment automatically. The app's generic owner-approval message does not require any manual approval here; proceed directly to Connect.
4. Tap Connect. The saved gateway should be wss://review.example.com. If you already enrolled in this review gateway, simply reconnect using the saved access; do not enroll again or choose Forget gateway.

PRIVATE REVIEW INVITATION
[Private invitation: stored only in Apple review fields and protected operational storage.]

TESTING
In Talk, type a short question or use the microphone (allow microphone and speech-recognition access when prompted). In Settings > Voice, choose a local English or Spanish voice and use Preview voice, then try a question in that language. History supports reviewing and continuing conversations. Background the app, reopen it and try another question to test reconnect. English, Spanish and background/reopen reconnect were confirmed on a physical iPhone on October 15, 2026.

This is real model-backed conversation and generated speech, not a prerecorded demo. The restricted review connection intentionally excludes owner administration and agent tools. Personal gateways and owner-managed invitations follow their own approval process.

Review access currently expires at [REVIEW_EXPIRY]. Contact us through the supplied review contact if access needs renewal. Credentials are private to Apple review; do not publish them.

Debian review containers and images were retired on October 15, 2026. The hostname is unchanged; the Ubuntu image serves the existing review state and credentials. No new review submission or release is implied by these metadata updates.
