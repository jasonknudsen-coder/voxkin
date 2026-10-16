# Getting started with Voxkin

Voxkin is a private iPhone beta. Existing internal testers can install **0.3.0 (9)**
from TestFlight (October 14, 2026); there is no public App Store release yet.
Developers building from source: see [build instructions](../README.md#build-and-test).

Choose your path:
- [Connect your own phone](#connect-your-own-phone) — gateway owner.
- [Invite someone else](#invite-someone-else) — give a phone access to one agent.
- [Accept an invitation](#accept-an-invitation) — invited phone user.

## Connect your own phone

**Before you start:** have a working OpenClaw gateway, its trusted `wss://` address,
and host access to approve pairing. If your gateway uses Tailscale, connect it on
both the host and phone. Need host setup? See [gateway setup](gateway-setup.md),
including the [native Windows experimental notes](windows-setup.md).

1. Install Voxkin from your TestFlight invitation.
2. Generate an **owner setup QR** using the host's
   [onboarding helper](../plugin/README.md#phone-setup-qr-from-the-shell-next-voxkin-app-build).
   In Voxkin Settings, tap **Scan invitation QR**, check the address, then
   **Use owner setup**. Alternatively, enter the gateway address and bootstrap token
   manually. A model-provider API key or OpenClaw node setup code will not work.
3. If you want to invite other phones, turn on **Request access-management
   permission** before connecting. This requests admin access; it does not install
   the plugin or grant permission itself.
4. Tap **Connect**. If pairing is required, on the gateway host run:
   ```sh
   openclaw devices list
   openclaw devices approve REQUEST_ID
   ```
   Verify your phone and requested scopes before approving. Then tap **Connect**
   or **Retry connection** again. No reinstall or new QR is needed.
5. Select your agent. Review the speech-provider policy and enable **Allow gateway
   speech** if you want spoken replies. Tap the Talk microphone, grant iOS permissions,
   speak, then tap again to finish and send.

Keep the owner QR/token private: owner access is broad, not limited to the selected
agent. Use a restricted invitation for someone else's phone.

## Invite someone else

**Host prerequisite:** install a compatible Voxkin plugin and enable restricted
access. Add each intended agent to the plugin's **`access.allowedAgents`** list,
then reload/restart through your normal deployment procedure.
[Exact configuration](restricted-access.md#gateway-setup).

**Existing agents do not become eligible automatically.** The app's **Allowed agent**
menu shows only eligible agents from that list; it cannot edit the list. `main` is
never eligible. Restricted phones currently have agent tools disabled.

1. Connect your owner phone with approved access-management permission. If already
   connected without it, **Disconnect**, enable the permission request, reconnect,
   and approve the scope upgrade on the gateway.
2. Under **Settings → Manage access**, tap **Refresh phones and invitations**.
   Select the allowed agent, enter a phone label, then tap **Add phone**.
3. Show the QR or use **Share invite** to send it privately. Invitations expire
   after ten minutes. Arrange network access first; Tailscale access is separate
   from Voxkin approval.
4. After the recipient enrolls, refresh. Compare the last eight characters of their
   device ID through a trusted channel, then approve that pending phone.

No owner phone connected yet? Use the [shell invitation helper](../plugin/README.md#first-restricted-user-no-owner-phone-required).
See [restricted-access details](restricted-access.md) for cancellation and revocation.

## Accept an invitation

1. Install Voxkin from your TestFlight invitation. Connect Tailscale if the gateway
   owner says it is required.
2. Open the private invitation link, or use **Settings → Scan invitation QR** / paste
   the invitation. Disconnect first if already connected to another gateway.
3. Check the gateway address, then tap **Enroll this phone**.
4. Tell the owner the last eight characters of your device ID. After they approve
   it, tap **Connect**. Only your assigned agent is available.
5. Choose your voice/language, then try a short microphone turn. Grant the requested
   iOS microphone and speech permissions. You do not need Python, Kokoro, or an owner
   token on your phone.

## Need help?

- **Missing agent in invitations:** ask the host owner to update `access.allowedAgents`;
  refresh after the configuration is activated.
- **Admin approved, management API unavailable:** the host needs a compatible plugin
  with restricted access enabled. Reconnect after it is ready.
- **Pairing pending:** approve the matching device on the host, then retry connecting.
  For copy-and-paste commands to inspect owner pairing or restricted enrollments,
  see [Check pending requests from the shell](gateway-setup.md#check-pending-requests-from-the-shell).
- **Voices, Spanish dictation, or audio trouble:** [usage and troubleshooting](using-voxkin.md).
- **Network or plugin installation:** [gateway setup reference](gateway-setup.md).
- **Build status, test evidence, and release limits:** [release readiness](release-readiness.md).
