# Gesture Volume — Google Play policy risk review

Reviewed 13 September 2026 against the current build (`targetSdk 37`, AppLovin MAX 13.6.4 with Meta,
Unity, Liftoff/Vungle, Mintegral and InMobi adapters, Play Billing 9.1.0). Updated after clipboard
(capture, history and Paste on tap), weather and the location permission, song identification and
running without a notification were removed, Privacy choices was added, and interstitial timing was
tightened.

This is a practical review, not legal advice. Policies change; re-check the linked Play Console
help pages before each submission.

---

## Summary

| # | Risk | Severity | Status |
| --- | --- | --- | --- |
| 1 | Data safety form says "no data collected", but the ad SDKs collect data | **High** | Correct answers written; **you must update Play Console** |
| 2 | Privacy policy is from 2023 and does not match the app | **High** | New policy written; **you must publish it** |
| 3 | Accessibility declaration and in-app disclosure were out of date | **High** | **Fixed** in code and declaration text |
| 4 | Accessibility API used for non-accessibility features | Low–Medium (was High) | **Reduced**: only system actions, volume keys and foreground-app detection (hide-in-apps, per-app gestures) remain; no window content access |
| 5 | App-open ad could appear over another app | **High** | **Fixed in code** |
| 6 | Special-use foreground service needs a declaration | Medium | Declaration text written; video needed |
| 7 | Interstitial ads at screen transitions | Medium | **Fixed in code** |
| 8 | No way to change ad consent later (GDPR) | Medium | **Fixed in code** |
| 9 | Location, contacts and phone permissions | Low (was Low–Medium) | **Location and contacts removed**; phone remains, on-device only |
| 10 | "Open source" claim with no licence file | Low | Listing reworded |
| 11 | Credits for assets no longer shipped | Low | Removed from listing |
| 12 | Notes included in Android backup | Low | Disclosed in policy |

---

## High

### 1. Data safety form contradicts the ad SDKs

**Problem.** The previous listing told you to answer *"No"* to "Does your app collect or share any
user data?". The app's own code collects nothing, but Google counts **data collected by SDKs in your
app** as yours. AppLovin and the mediated networks collect the advertising ID, IP-derived location,
device information, ad interactions and diagnostics.

**Done.** `play-listing-en.md` has the correct table. The weather row is gone with the feature; the
approximate-location row stays because the ad SDKs derive it from IP.

**You must.** Update *Play Console › App content › Data safety* before the next release, and
cross-check against AppLovin's Data safety guidance for SDK 13.6.x and each adapter.

### 2. Privacy policy out of date

**Problem.** The live policy (24 May 2023) says the app does not gather any personal data, names no
third-party services, and says nothing about the accessibility service, ads or permissions.

**Done.** `privacy-policy.html` — full rewrite, ready to paste into Blogger: on-device data, the three
accessibility uses, every permission, AppLovin and each mediated network with links, Privacy
choices, Google Play services, retention, children, GDPR/CCPA rights and contact.

**You must.** Replace the text at
https://newagedevs.com/products/gesture-volume/privacy (moved from Blogger on 4 October 2026), and publish
the terms at https://newagedevs.com/products/gesture-volume/terms (the current terms mention a "device administrator"
permission the app does not use, and have no purchase, refund, advertising or governing-law sections).

### 3. Accessibility declaration and disclosure — fixed

**Problem.** The old declaration under-stated what the service did (window-state events for
Visibility, the key filter for *Hide in screenshots*), and later versions of the dialog did not name
Paste on tap.

**Done.**
- The in-app disclosure and service description list exactly three uses — system actions, the two
  volume keys (Instant) and noticing the app in front, to hide the bar in chosen apps or use the
  gestures given to them. The third was widened for per-app gestures, so the 11 translations of the
  three disclosure strings were removed rather than left describing less than the service does;
  every locale shows the English text until they are translated again.
- `accessibility_service_config.xml` declares only `typeWindowStateChanged`, with
  `canRetrieveWindowContent="false"`.
- The declaration, store description and reviewer reply in `play-listing-en.md` match the dialog.

**You must.** Paste the new declaration and record the demo video described there.

### 5. App-open ad over other apps — fixed

The app-open ad fired on any activity start, including the transparent QR scanner and voice search
activities the Deck opens over other apps. It now shows only when `MainActivity` starts
(`GestureApplication.AppOpenManager`). The bar, Deck, Quick panel and menu never show ads.

---

## Medium

### 4. Accessibility API for convenience features — reduced

Play allows the Accessibility API for non-accessibility tools only with a prominent disclosure,
affirmative consent, an accurate declaration and a video.

**Removed:**
- **Clipboard capture, clipboard history and Paste on tap** — the service no longer observes copies
  and can no longer read window content.
- **Running without a notification** — the bar is always hosted by the foreground service with the
  overlay permission. The accessibility service draws only the lock-screen copy below, and only
  while the phone is locked.
- **Hide in screenshots** — the key filter no longer hides the bar on Volume down; the Deck's
  Screenshot tile takes a picture with the bar out of it.

**Remaining, each off by default and behind its own setting:**

| Use | Review sensitivity | Mitigation |
| --- | --- | --- |
| System actions (lock, screenshot, Back, Home…) | Low — the textbook accepted use | — |
| Volume-key filtering | Medium — key interception | Only the two volume keys, others passed through, nothing recorded; filter requested only while Instant is on; for the quarter second a Volume down press is undecided, window-state events are checked for the System UI package only, so a button screenshot does not open the panel |
| Bar on the lock screen (1.5.2) | Medium — the service draws a `TYPE_ACCESSIBILITY_OVERLAY` window | Off by default, behind Visibility › Show on the lock screen; only while the keyguard is locked and the screen is on, gone at the unlock; does only what the lock screen allows (volume, Quick slider, toggles, system actions), refusing the Deck, the menu and app launches; no accessibility events used. Named in the disclosure, the service description and the declaration |
| Foreground app detection | Low | Package name only, only while apps are chosen to hide the bar in or have gestures of their own, no history, no window content. The Play Console declaration and its video must be updated for per-app gestures before the release that adds them |

**If the review still objects:** drop the bar on the lock screen first (it is the one use that
draws), then the *Instant* volume keys (this removes `canRequestFilterKeyEvents`), leaving only
system actions and foreground-app detection.

### 6. Special-use foreground service

`FOREGROUND_SERVICE_SPECIAL_USE` requires a declaration in *App content › Foreground service
permissions*, with a justification and a video. Text is in `play-listing-en.md`. The foreground
service is the only host for the bar, so show the bar being used over another app in the video.

### 7. Interstitial timing — fixed

All interstitial decisions go through one tested function, `AdPacing.mayShowInterstitial`
(16 unit tests). Interstitials show **only** after the user switches the service on or taps Save on
the Appearance screen. They are blocked on back navigation and plain screen transitions, within 60 s
of the app coming to the foreground, within 60 s of an app-open ad, while another ad is showing,
while a permission/settings/consent flow is open, and by the existing 3-minute cooldown,
per-session cap and post-install grace period.

### 8. Changing ad consent — fixed

A **Privacy choices** row on the About screen and in the navigation drawer reopens the consent form
(`AppLovinSdk.cmpService.showCmpForExistingUser`). It appears only when the SDK is initialised, the
user is in a consent region (`consentFlowUserGeography == GDPR`) and the user is not Pro. App-open ads
are paused while the form is open. It cannot be seen in local builds that use placeholder AppLovin
keys — check it in a build with the real keys.

---

## Low

9. **Permissions.** `ACCESS_COARSE_LOCATION` is gone with the weather tile, so there is no location
   prompt, no location in the prominent-disclosure rules and no weather row in the Data safety form.
   `READ_CONTACTS` is gone too (1.5.2): Play Console asked for a Contacts permission declaration,
   and the Deck's search now matches quick-dial entries instead, which the system contact picker
   fills without any permission. The manifest strips it with `tools:node="remove"`, so a library
   cannot bring it back. `CALL_PHONE` remains, asked only when its switch is turned on in the
   Deck's search settings, and used on the device only.
10. **Licence.** The repository has no `LICENSE` file, so "open source" was misleading. The listing no
    longer says it; the terms explain the position. Add a licence if you want the claim back.
11. **Credits.** Photos from pexels.com are no longer shipped and were removed from the listing.
    svgrepo.com icons and the S M Rony Lottie animation remain and are credited in the terms.
12. **Backup.** `android:allowBackup="true"` means notes can be in the user's Google backup.
    Disclosed in the privacy policy.
13. **Target audience.** Choose 13+ (or 18+); mediated ad networks are not Families-certified.
14. **Metadata.** No competitor names or keyword lists in the listing — keep it that way.
15. **Permissions handled well.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `QUERY_ALL_PACKAGES` are
    not declared; `WRITE_SETTINGS` and `SYSTEM_ALERT_WINDOW` match core features.

---

## Before you submit — checklist

- [x] Paste `privacy-policy.html` at the privacy URL and `terms-and-conditions.html` at the terms URL (Blogger › Edit post › HTML view) — live pages checked 14 September 2026, both dated 13 September 2026
- [ ] Legal pages moved to newagedevs.com (4 October 2026): check https://newagedevs.com/products/gesture-volume/privacy and https://newagedevs.com/products/gesture-volume/terms are live, then set the new privacy URL in Play Console (App content › Privacy policy, and Store settings)
- [ ] Update the Data safety form from `play-listing-en.md` (no weather row)
- [ ] Paste the new Accessibility API declaration (three uses) and upload its video — video recorded: https://drive.google.com/file/d/1FIes_6bU4usJcwVVLh0mfX5py25D84Ks/view?usp=sharing
- [ ] Complete the Foreground service declaration and upload its video — video recorded: https://drive.google.com/file/d/1bO_3olqE8H6aPhh2rDlBRNyfrtitxQXT/view?usp=sharing
- [ ] Remove any Play Console "Sensitive permissions" declaration for location if one was filed
- [ ] Enable Google UMP as the CMP in the AppLovin MAX dashboard (Privacy › CMP)
- [ ] Content rating: mark "contains ads"; target audience 13+
- [ ] Ads declaration: *Yes, contains ads*
- [ ] Paste the new short description, full description and What's new
- [ ] Bump `versionCode` / `versionName` in `app/build.gradle.kts`
