# Prompt for Claude in Chrome — release Gesture Volume 1.4.0 to internal testing

The extension cannot pick files from the Mac, so it stops and waits for you to upload the bundle:
`app/build/outputs/bundle/release/app-release.aab` (1.4.0, versionCode 35, signed, ads on).

````
Release my app "Gesture Volume" (package com.newagedevs.gesturevolume) to INTERNAL TESTING in Google Play Console.

RULES
- Internal testing track only. Never open, edit or roll out Production, Closed testing or Open testing, and never click "Send changes for review" for the whole app.
- Do not change store listing, App content, pricing, countries or in-app products.
- If Play Console shows any error (for example a version code already used, a signing key mismatch, or a policy block), stop and copy it to me word for word. Do not work around it.
- Copy text exactly.

1. Go to https://play.google.com/console, open Gesture Volume, then Test and release › Testing › Internal testing.
2. If there is no tester list, stop and tell me. Otherwise note the tester list name and how many testers it has.
3. Click "Create new release". If a draft release already exists, open it instead and tell me what it contained.
4. STOP and ask me to upload the app bundle. Wait until I say it is uploaded, then confirm the bundle shows version code 35 and version name 1.4.0. If it shows anything else, stop and tell me.
5. Release name: 1.4.0 (35)
6. Release notes, English (United States) – en-US, exactly:

<en-US>
🎚 Quick slider — a full-height track that grows out of the bar, with animated fills, your own colours and an optional adaptive step sound
🔄 Dynamic position — the bar follows your phone's edge when you rotate
👁 Visibility — hide the bar in chosen apps
🪟 New panel styles and entrance animations
🪙 A redesigned coin toss
🔐 Privacy choices — change your ad consent any time
🛠 Fixes, including a freeze when opening the app
</en-US>

7. Click "Next" (or "Save" then "Review release"). Copy every error and warning shown on the review page, word for word.
8. If there are no errors (warnings are fine), click "Save and publish" / "Start rollout to Internal testing" and confirm. If there are errors, do not roll out; stop and report.
9. After rollout, open the Testers tab and copy the "Join on the web" / opt-in link.

REPORT
- Tester list name and count
- Bundle version shown
- Every warning and error, word for word
- Whether the rollout to internal testing started, and its status line
- The opt-in link
````
