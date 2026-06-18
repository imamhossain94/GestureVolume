# Gesture Volume — Ads Revenue Maximization & Premium-Feel Plan

## Context

**30-day report: $3.58 revenue, 63,140 impressions, $0.06 blended eCPM.**

| Format | Rev | Impr | eCPM | Share of impr | Share of rev |
|---|---|---|---|---|---|
| Interstitial | $1.59 | 513 | $3.10 | 0.8% | 44% |
| Banner | $0.83 | 57,432 | $0.01 | 91% | 23% |
| App Open | $0.73 | 1,731 | $0.42 | 3% | 20% |
| Native | $0.44 | 3,464 | $0.13 | 5% | 12% |

**The banner is 91% of all impressions (57,432) at $0.01 eCPM** — pure junk volume from a banner pinned to the bottom of *every* screen. Meanwhile **interstitial pays the best at $3.10** but fired only on the service-toggle, so it barely showed (513 impr).

**Network breakdown (30d) shows the root cause:**

| Network | Rev | Impr | eCPM |
|---|---|---|---|
| AppLovin Bidding | $1.33 | 42,733 | **$0.03** |
| Meta Bidding | $0.44 | 755 | $0.58 |
| Unity Bidding | $0.43 | 650 | $0.66 |
| InMobi Bidding | $0.41 | 5,877 | $0.07 |
| Liftoff (Vungle) Bidding | $0.39 | 993 | $0.40 |
| AppLovin Exchange | $0.34 | 11,613 | $0.03 |
| Meta Native Bidding | $0.23 | 443 | $0.52 |

1. **No consent / CMP existed.** **AppLovin Bidding + Exchange ate 54,346 impressions at $0.03** (mostly the everywhere-banner) while Meta ($0.58), Unity ($0.66), Liftoff ($0.40), Meta Native ($0.52) — the networks with real eCPM — won almost nothing. Classic no-consent signature: premium demand bids blind, low-tier inventory mops up the junk banner volume.
2. **No impression-level revenue tracking** — `setRevenueListener` was nowhere.
3. **Impression mix maximally backwards:** the worst-paying format ($0.01 banner) was 91% of impressions; the best ($3.10 interstitial) was 0.8%.

**Decisions:** tasteful premium polish (keep current Material 3); skip rewarded for now; remove the banner from the home/control flow, keep one (adaptive) banner on the secondary info screens.

---

## What shipped in this pass (code)

### Tier 1 — eCPM unlock
**1A. AppLovin Terms & Privacy / Google UMP consent flow** — [GestureApplication.kt](app/src/main/java/com/newagedevs/gesturevolume/GestureApplication.kt) `initializeAppLovinSdk()`. Configured `appLovinSdk.settings.termsAndPrivacyPolicyFlowSettings` (`isEnabled = true`, `privacyPolicyUri`, `termsOfServiceUri`, debug `debugUserGeography = GDPR`) **before** building the init config — the correct SDK-13.6.2 API (no `showTermsAndPrivacyPolicyAlertInGdpr`). Added `Constants.TERMS_OF_SERVICE_URL`. This is the single biggest lever given 86% of impressions went to $0.03 networks.

**1B. Impression-level revenue tracking** — new [AdRevenueTracker.kt](app/src/main/java/com/newagedevs/gesturevolume/helper/AdRevenueTracker.kt), wired via `setRevenueListener` on **interstitial, native, banner** ([ApplovinAdsManager.kt](app/src/main/java/com/newagedevs/gesturevolume/helper/ApplovinAdsManager.kt)) and **app open** (GestureApplication.kt). Logs per-impression `revenue/network/format/unit/placement` to Logcat (`AdRevenue` tag).
> **Follow-up (needs a Firebase project):** no `google-services.json` here, so revenue logs to Logcat. To get aggregated ARPDAU by placement, add `google-services.json` + the `com.google.gms.google-services` plugin + `firebase-analytics`, then swap the `AdRevenueTracker` body to the standard `ad_impression` event (snippet in the file's TODO). MAX dashboard gives per-network/format revenue meanwhile.

### Tier 2 — Shift impression mix to high-value formats
- **Interstitial at more natural transitions** — [MainViewModel.kt](app/src/main/java/com/newagedevs/gesturevolume/ui/viewmodels/MainViewModel.kt) `maybeShowInterstitialAd()` is now also called when opening the **Appearance** and **Actions** screens ([MainNavigation.kt](app/src/main/java/com/newagedevs/gesturevolume/ui/activities/MainNavigation.kt)), not just on service-toggle. This is the key revenue lever — interstitial is ~50× the banner eCPM.
- **Retention-safe caps** — [SharedPref.kt](app/src/main/java/com/newagedevs/gesturevolume/data/local/SharedPref.kt): `INTERSTITIAL_AD_COOLDOWN` 90s → **3 min**, plus a new **per-session cap of 5** so the added triggers can't stack up. The 90s any-ad gap is unchanged.
- **App-open cooldown** `APP_OPEN_AD_COOLDOWN` 60 min → **25 min** — more qualified resumes at $0.42 eCPM (~40× banner); first-launch / overlay-permission / paused guards untouched.

### Tier 3 — Banner declutter & premium feel
**Removed the nav-level persistent banner** that rendered on every screen ([MainNavigation.kt](app/src/main/java/com/newagedevs/gesturevolume/ui/activities/MainNavigation.kt)) — it was the source of the 57,432 junk impressions at $0.01. The banner now renders **only on the secondary info screens** (About / Feedback / Troubleshoot), gated by the current nav route. The remaining banner is **adaptive** (`setExtraParameter("adaptive_banner","true")` + `AppLovinSdkUtils` sizing). Home/control, Appearance, Actions, Permissions, and Walkthrough are now banner-free → a markedly cleaner, more premium utility-app feel.

### Mediation
**Added the Mintegral adapter** ([libs.versions.toml](gradle/libs.versions.toml) + [app/build.gradle.kts](app/build.gradle.kts)) — Mintegral was the top-eCPM bidder in the portfolio and was missing here (only Meta/InMobi/Unity/Vungle were present). The Mintegral Maven repo was already configured in `settings.gradle.kts`.

---

## Tier 1C — AppLovin MAX dashboard optimization (do this — no code, biggest lever)
1. **Enable a CMP:** MAX → *Privacy* → enable **Google UMP**, add the privacy-policy URL. Pairs with 1A; without it no consent string is generated.
2. **Approve/enable every network per ad unit** (Meta/FAN, Unity, Vungle/Liftoff, InMobi, **Mintegral** — newly added): confirm **Live/Active** with valid credentials.
3. **Per ad unit** (Banner, Native, Interstitial, App Open): enable **all** networks + **Auto-CPM**; drop over-high manual floors.
4. **Act on the 30-day signal:** Meta ($0.58), Unity ($0.66), Liftoff ($0.40), Meta Native ($0.52) all show healthy eCPM but tiny volume — consent + fewer junk-banner impressions should let them win far more. **AppLovin Bidding/Exchange at $0.03** were only winning the blind banner inventory that's now removed.
5. After 3–5 days, prune chronic low-fillers using per-network reports + the 1B revenue logs.

## Deferred (after 3–5 days of data)
- **Firebase** for dashboard ARPDAU (see 1B follow-up).
- **Rewarded** format — skipped this pass per decision; a strong fit later for a utility app ("watch an ad to unlock a preset / remove ads for 30 min") at zero retention cost once a rewarded ad unit exists.
- **Firebase Remote Config** to tune the cooldowns/caps live without a release.

## Critical files
- [GestureApplication.kt](app/src/main/java/com/newagedevs/gesturevolume/GestureApplication.kt) — consent (1A) + app-open revenue listener (1B).
- [helper/AdRevenueTracker.kt](app/src/main/java/com/newagedevs/gesturevolume/helper/AdRevenueTracker.kt) — revenue logging (1B).
- [helper/ApplovinAdsManager.kt](app/src/main/java/com/newagedevs/gesturevolume/helper/ApplovinAdsManager.kt) — revenue listeners + adaptive banner.
- [data/local/SharedPref.kt](app/src/main/java/com/newagedevs/gesturevolume/data/local/SharedPref.kt) — interstitial cooldown + session cap + app-open cooldown.
- [ui/viewmodels/MainViewModel.kt](app/src/main/java/com/newagedevs/gesturevolume/ui/viewmodels/MainViewModel.kt) — `maybeShowInterstitialAd()`.
- [ui/activities/MainNavigation.kt](app/src/main/java/com/newagedevs/gesturevolume/ui/activities/MainNavigation.kt) — banner route-gating (Tier 3) + interstitial triggers (Tier 2).
- [gradle/libs.versions.toml](gradle/libs.versions.toml) + [app/build.gradle.kts](app/build.gradle.kts) — Mintegral adapter.

## Verification
- **Build/run debug**; run AppLovin **Mediation Debugger** — every network (incl. Mintegral) **Ready**, consent registered.
- The debug `debugUserGeography = GDPR` override forces the consent prompt outside the EEA — confirm it appears once on first launch.
- Walk the flow: cold start → app-open (25-min cooldown); toggle service / open Appearance / open Actions → interstitial respecting the 3-min/session caps; home → native; About/Feedback/Troubleshoot → adaptive banner; **no banner on home, Appearance, Actions, Permissions**. Confirm pro path shows **zero** ads.
- Confirm `AdRevenue` `ad_impression` lines appear in Logcat.
- Track MAX dashboard eCPM/ARPDAU over 3–5 days vs the **$0.06** baseline — expect the largest relative lift of the portfolio, since the junk-banner volume that dragged the blended eCPM down is gone and consent unlocks the high-eCPM networks.
