# App Privacy and age-rating worksheet — October 15, 2026

Status: researched draft, NOT submitted/attested. A privacy manifest does not
answer the App Store questionnaire. Public website clarification deployed and
verified October 15; this does not constitute App Privacy attestation. Official
guidance checked October 15:
- https://developer.apple.com/app-store/app-privacy-details/
- https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions/

## Data flows and proposed answers

- Tracking: **No**. No advertising or third-party advertising purposes.
- Microphone audio: Apple on-device recognition; app does not upload raw microphone
  recordings. Do not claim Audio Data collection merely because voice input exists.
- Recognized text and reply text: sent to the user-selected gateway and its configured
  model/speech services. Gateway storage is not erased by clearing the phone view.
- Local credentials: device-only Keychain. Local preferences/in-memory timing do not
  by themselves constitute off-device developer collection.
- Normal independently operated BYO gateway: developer collection differs from that
  gateway operator's collection. Data Not Collected is only defensible after the
  developer/partner and review-environment scope is resolved, not as a blanket
  statement that no servers retain text.
- Publisher-operated review/test gateway: persistent access/device state, OpenClaw
  state and model relay exist. Candidate declarations if in scope: **Other User
  Content** (free-form prompts/replies), **Device ID**, **App Functionality**, linked
  to user/device, not tracking. Verify retained logs/diagnostics separately.
- Apple guidance treats generic free-form text as Other User Content; do not label
  every possible sensitive category merely because a user can type anything.
- Voluntary support contact/feedback: evaluate Apple's optional-disclosure criteria
  against actual support practice. Do not silently count support as analytics.
- Apple's own collection is distinct from publisher use of Apple-provided data.

### Facts still needed for final attestation

1. Resolved: hosted gateway strictly for App Store review; users bring their own.
2. Actual review-gateway transcript/access/log deletion policy and model-provider
   retention settings. The 14-day access window is NOT a deletion policy.
3. Resolved: publisher will use Apple crash reports and App Analytics.

Do not attest Data Not Collected or publish labels until these are settled. If
review/test processing remains in scope, disclose the applicable retained data
instead of pretending the app has no processing outside the user's gateway.

## Age-rating capabilities (candidate answers)

No advertising, parental controls, age assurance, social media, broad user-generated
content distribution, person-to-person messaging/chat, embedded unrestricted web
browser, gambling, simulated gambling, contests or loot boxes are implemented.
Gateway admin approval is not a parental-control system. Agent web tools do not
by themselves constitute an unrestricted in-app web browser.

Content-frequency descriptors need a separate honest assessment: arbitrary gateway
models can generate mature, medical, violent or other content. Neither all None nor
all Frequent is established by this review. Maintainer declined an adult-only override on October 15. Leave the override at
**NONE** and let Apple calculate the rating from truthful questionnaire answers.
Do not invent mature content merely because the client connects to AI. Do not claim
kid-safe filtering or automatic age verification.

## Submission state

No age answers or App Privacy labels have been submitted by this pass. Store copy
and screenshots can be saved as an unpublished draft independently. Review
submission and public release remain separately authorized actions.

## Owner clarification — October 15

Maintainer confirmed the publisher-hosted gateway is strictly for App Store review
testing, not ordinary customer use or a hosted service offering. Customers bring
their own gateways. Apply this scope when completing the privacy questionnaire;
review-environment retention and downstream processing still need truthful
documentation. This clarification alone does not attest the privacy labels.

Maintainer approved using both Apple crash reports and App Analytics to diagnose
issues and improve Voxkin. Reflect actual usage of both Apple services in the
final privacy assessment; this is not authorization to add a third-party
analytics/crash SDK. No Apple settings changed in this documentation pass.

Maintainer requested no age rating; clarified that Apple requires its questionnaire
and calculated rating. No optional age override is selected. Content-frequency
answers remain unresolved, not automatically None.

## Content assessment — October 15 follow-up

Maintainer authorized the assessment and accepts a 13+ minimum. This supersedes the
previous no-override preference; it does not force Apple's calculated rating down.

Source inspection and Apple's definitions support No for advertising, parental
controls, age assurance, person-to-person messaging, social media, broadly
redistributed user content, unrestricted in-app browser, gambling and loot boxes;
None for contests and simulated gambling. Private AI conversation is not itself
person-to-person chat or broad UGC distribution under those definitions.

No intentionally inappropriate bundled content was identified. However, Apple
explicitly requires assessment of AI-generated responses, not only bundled UI:
https://developer.apple.com/news/?id=ks775ehf
“you must consider how all app features, including AI assistants and chatbot
functionality, impact the frequency of sensitive content appearing within your app”.
Voxkin accepts user-configured gateways/models; the current evidence does not
establish a content-frequency profile or app-enforced filtering across them.
Therefore a blanket None attestation is not supported by the absence of bundled
content. General health/medical, mature themes, language and violence need assessment
of reachable responses; a 13+ override does not replace this step.

API results: saving the 13+ override alone returned500; readback remained NONE.
Saving only the established capability answers returned409 requiring the remaining
content fields; readback confirmed all questionnaire answers still null. Nothing
was partially attested. Next: representative response review for the supported
configuration and, if BYO variability remains unresolved, clarification from Apple
before attesting a frequency that cannot be established. Do not silently convert
unknown frequencies to either None or Frequent.

## Owner-completed questionnaire readback

Maintainer completed the form in App Store Connect. API verified saved No/None answers
and calculated FOUR_PLUS (4+), overrides NONE. No assistant changes were made to
those answers. This supersedes the blank-form status above; it does not establish
that arbitrary user-selected model output is filtered. Receipt:
`.tmp/age-owner-completed.json`.
