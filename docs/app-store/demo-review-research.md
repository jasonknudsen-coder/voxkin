# Demo-mode review research — October 14, 2026

## Finding

Apple does not prescribe OpenClaw as required review infrastructure. A demo mode
is expressly contemplated, but not an unconditional substitute for working access.
Guideline2.1(a) permits a built-in demo mode instead of a demo account when legal
or security obligations prevent providing an account, **with prior approval by
Apple**, and requires the app's full features and functionality to be exhibited.

The submission checklist allows an active demo account or fully-featured demo mode
and also says backend services should be live and accessible during review.
The complete-review help page specifically discusses sensitive user information
and demonstration data. These sources support asking Apple about an alternative;
they do not establish that a scripted chat/audio walkthrough meets the requirement.
Guideline2.2 and TestFlight overview apply App Review Guidelines to external beta
review; TestFlight is not a documented exemption from the access requirement.

## Implications for Voxkin

- Clearly label any sample-data/demo mode and disclose simulated components.
- A walkthrough with canned responses alone does not demonstrate actual remote
  agent conversation, device authentication, enrollment/approval or isolation.
- An interactive demo could use the actual dictation, playback, stop/cancel and
  language-selection UI with a local synthetic transport. Enrollment and network
  behavior would remain simulated and must be disclosed, not called proven.
- A video can support an explanation of a difficult-to-replicate environment;
  Apple does not state that a video automatically replaces functional access.
- Request confirmation of an acceptable review plan before treating an offline
  demo as a replacement for an isolated live service. Approval remains Apple's.
- No AWS resource, demo implementation, live review endpoint or Apple inquiry was
  created/submitted by this research.

## Proposed inquiry — draft, not sent

Subject: Voxkin — request for demo-mode review guidance under Guideline2.1(a)

Voxkin (bundle com.iris.private.voice, App Store Connect app6822553328) is a native
iPhone voice client for user-operated OpenClaw gateways. It has no central vendor
conversation backend. Real gateways can contain private conversations and
privileged agent access, which we cannot expose as reviewer accounts.

Before implementing an alternative review path, would you approve a clearly
labeled built-in demonstration mode using synthetic data under Guideline2.1(a),
for external TestFlight review and eventual App Store review? We propose using
real microphone/dictation, text input, playback, stop/cancel and English/Spanish
controls, with explicitly simulated agent responses and gateway enrollment.
We would disclose that this does not connect to a live gateway or prove server
access control, and could provide a physical-device video of real gateway use.

Please advise whether this scope is sufficient or which features require live
review access. We can consider an isolated demo service if required. We are not
claiming that the proposed demo already exists or that a recording replaces review.

## Recommended next steps

1. Ask Apple through App Review support or a pre-submission appointment; preserve
   its response. Do not claim prior approval merely from submitting an inquiry.
2. If accepted, build and test the agreed demo, then upload a new build and provide
   exact review steps with the approval reference.
3. If declined or insufficient, use a separate disposable review host isolated from unrelated apps and user data.
4. For immediate family device testing, Ad Hoc/development installation is a
   separate option; requires device registration/signing, not external beta review.
   This does not solve eventual App Store review or production plugin qualification.

## Official sources checked

- https://developer.apple.com/app-store/review/guidelines/#app-completeness
  Before You Submit;2.1(a);2.2;2.3.1(a).
- https://developer.apple.com/help/app-review/before-submitting-for-review/complete-review
  Full functionality with demonstration data; nonexpiring review credentials.
- https://developer.apple.com/distribute/app-review/
  Supporting demo video/hardware guidance; contact/appointment routes.
- https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/
  First external build review against App Review Guidelines.
- https://developer.apple.com/contact/request/app-review/support/
- https://developer.apple.com/help/app-review/before-submitting-for-review/app-review-appointment
