# Using Voxkin and troubleshooting

For your first connection, start with [Getting started](getting-started.md).

## Pick language, voice, and reply length

1. After enabling/reloading the plugin, **Disconnect → Connect** in the app to
   refresh advertised capabilities.
2. Select **Settings → Speech source → Local voices**.
3. Choose **Language → English / Español**, then an advertised **Voice**.
4. Tap **Preview voice**. This sends a fixed sample to synthesis, not a message to
   the agent. Stop ends local playback and discards late audio.
5. Choose **Brief**, **Balanced**, or **Full** replies. These are preferences, not
   guaranteed word counts; explicit requests for detail still take priority.

Voice/language selection is saved per gateway on this phone; it does not alter the
shared gateway default. Choices are disabled while a turn/recording/playback is active.
Language guides dictation and the agent's reply. Speech uses the selected voice,
not automatic language detection. A user explicitly asking for another reply language
can override the text preference, but does not automatically switch the voice.

## Prepare Spanish dictation on the iPhone

Speech **input** is Apple's on-device recognition. Speech **output** is the gateway's
voice. Dora preview can work even when Spanish microphone input is not ready.

If Spanish dictation reports unavailable:

1. In **iPhone Settings → General → Keyboard**, ensure **Enable Dictation** is on.
2. Under **Keyboards → Add New Keyboard**, add a Spanish keyboard.
3. Open **Notes**, switch to that Spanish keyboard, and use the keyboard microphone
   to dictate a short Spanish sentence.
4. Return to Voxkin, keep **Español / Dora** selected, and retry the main mic.
   If necessary, close and reopen the app to retry recognition availability.

**Observed on one physical iPhone:** adding the keyboard alone did not resolve the
error. Spanish dictation in Notes worked; returning to Voxkin then allowed Spanish
input and a correct Spanish response. This is a verified troubleshooting sequence,
not proof of exactly when iOS downloaded/initialized an asset or a guarantee for all
phones. The app cannot force Apple's model download. It checks available recognizers
within the selected language, never substitutes English for Spanish, and never enables
cloud recognition as a fallback. If still unavailable, type Spanish and report the exact
error, iOS version, selected language, and whether Notes/Preview work—no tokens needed.

## Check the complete flow

- Preview Heart and Dora separately to verify synthesis/playback.
- In English, tap the main mic, speak, then tap again to finish and send.
- In Spanish, try “Hola, dime una frase sobre el bosque.” Check the transcribed words,
  Spanish reply, and Spanish voice. Confirm meaning as well as successful playback.
- If finalization needs review, inspect/edit the draft and tap Send. The clear X
  appears only when there is text and lets you discard a mistaken draft.
- Stop silences local audio; **Cancel request** separately asks the agent to stop.
  Completed actions cannot be undone by either button.
- Backgrounding/locking disconnects and clears the app conversation. Returning
  reconnects without replay. This does not erase gateway-side history.

Voxkin keeps the screen awake while the app is active in the foreground. Leaving
the app restores normal auto-lock behavior; the side button still locks manually.

## Hands-free conversation (experimental)

Hold the main microphone for about two-thirds of a second, then release. You can
also tap **Hold the mic for conversation**. Start with an empty draft and a connected
agent. Ordinary single-tap recording still works as before.

- Speak, then pause: roughly two seconds without new recognized words finishes
  transcription and sends the turn. This is a transcript-based pause detector, not
  acoustic voice-activity detection; hesitation and recognition delays need real-device tuning.
- The microphone stays off while the agent works and its reply plays. After the
  entire reply, listening resumes following a short cooldown. This is alternating
  conversation, not simultaneous listening/talking.
- Tap the mic during reply playback to interrupt and resume listening. **End** or
  **Stop** leaves conversation mode. **Cancel request** separately cancels a pending
  agent request; stopping local audio does not undo remote work.
- Settings, keyboard input, app inactivity/locking/backgrounding, disconnects,
  audio interruptions, and recognition errors end the mode. It never restarts
  automatically when the app returns.
- Recognition failures preserve any draft for review rather than retrying or
  sending it automatically. The existing 30-second utterance limit still applies.
- For first-time microphone/speech permissions, complete setup with a normal
  single turn first; a system permission dialog can end hands-free mode.

## Troubleshooting and migration

- **Switching gateways by invitation:** disconnect the current gateway first,
  then open the new invitation and verify the selected endpoint before testing.
- **Testing a public review endpoint:** Wi-Fi and cellular both use its public
  route; cellular is not required. Tailscale is only relevant to gateways whose
  network configuration requires it. Check actual playback and background/reopen
  separately from server-side audio generation.

- **No Local voices option:** reconnect; check plugin enabled/allowlisted, runtime
  setup, and whether both plugin methods are advertised by this gateway version.
- **Saved voice unavailable:** restore that plugin/voice or explicitly choose
  **Gateway default**. The app will not silently substitute another provider.
- **Preview works, microphone fails:** use the dictation checklist above; reinstalling
  Kokoro will not fix Apple's recognition availability.
- **Text works, audio fails:** verify Preview, speech acceptance, and gateway runtime;
  retain the exact error. Do not resubmit a potentially action-taking prompt just
  to test audio—use Preview.
- **Slow response:** Settings breaks down reply generation and speech preparation.
  Full final text is awaited before speech; shorter replies can help, but there is
  no fixed latency promise or forked gateway streaming implementation.
- **Rollback:** select Gateway default, then disable/reload the optional plugin
  through your gateway's normal workflow. Do not delete the old provider/adapter
  until you have checked whether other clients or channels still use it.

See [qualification and live evidence](local-voice-plugin.md) for tested scope.
Public release, clean-OS and other host-platform installation, multi-user isolation,
CarPlay and comprehensive Bluetooth/phone-call interruption testing remain outside
the current qualification. AirPods have a user-reported successful check, not universal qualification. A guided one-command setup is a future improvement, not a
feature this prototype already ships.

## Updating the local voice catalog

For a 0.1 → 0.2 plugin upgrade, follow the [receipt-refresh and allowlist steps](../plugin/README.md#upgrade-from-01-to-02). Updating code alone is not sufficient: old readiness receipts lack voice inventory, and existing two-voice allowlists intentionally stay restricted. Install the compatible app build, reconnect, then preview newly enabled voices. The app discovers names/languages from the plugin rather than maintaining its own Kokoro list.
