# Gesture Volume — Play Store listing

Ready to paste into Play Console. Character counts are checked against Google's limits. Updated
19 November 2026 for 1.5.5 (build 43, already set in `app/build.gradle.kts`): the button animation,
the Button looks fill, step sounds, and the volume and edge fixes. The description, What's new, the
accessibility declaration and the reply to reviewers all name the button animation and the bar
over the status bar, as the in-app disclosure does.

Companion documents in this folder:
- `privacy-policy.html` — the privacy policy's former Blogger source; the live copy is now on newagedevs.com at the privacy URL the app links to (see "Links" below)
- `terms-and-conditions.html` — the terms' former Blogger source; the live copy is now on newagedevs.com at the terms URL
- `play-policy-risks.md` — what could get this listing rejected, and what has been done about it

---

## Links

| Field | Value |
| --- | --- |
| Privacy policy URL (Play Console › App content › Privacy policy) | https://newagedevs.com/products/gesture-volume/privacy |
| Terms URL (used by the consent form and the About screen in the app) | https://newagedevs.com/products/gesture-volume/terms |
| Contact email | imamagun94@gmail.com |

Both documents moved from Blogger to newagedevs.com on 4 October 2026, and the app, the ad consent
form and the About screen point at the new URLs. Make sure both pages are live **before** submitting —
reviewers compare the policy with the Data safety form.

---

## App name (30 max)

```
Gesture Volume: Edge Bar
```
24 characters.

---

## Short description (80 max)

```
Volume, brightness and a deck of tools, from one bar on your screen edge.
```
73 characters.

Alternate:
```
Save your volume buttons: a screen-edge bar for volume, brightness and tools.
```
77 characters.

---

## Full description (4000 max)

```
Gesture Volume puts a slim bar on the edge of your screen. Swipe it for volume or brightness, pull out the Quick slider, tap it for any action you like, or pull it inward for the Deck — a panel of your apps and tools.

It began as a way to save a worn-out volume rocker, and it still is one.

▸ THE BAR
• Swipe up and down for volume or brightness
• Follows what is playing — a call, an alarm, a ringtone or your music
• Drag it anywhere; it snaps to the edge if you like
• Dynamic position: it keeps to the same edge of your phone when you rotate
• Presets to start from, then size, shape, colour, opacity, icon and edge distance are yours

▸ BUTTON ANIMATION
Press a volume button and the screen's edge answers beside it, with the level. 32 animations with their own sounds — water, fireworks, balloons, a cute cat and more. The power button can play them too.

▸ QUICK SLIDER
A full-height track that grows out of the bar. Set the level in one move, then it tucks itself away.
• Adaptive volume — the call's volume in a call, otherwise whatever is playing — or media, ring or alarm volume, or brightness
• Animated fills — waves, aurora, plasma, galaxy, button looks and more — in your own colours
• Your own icon, colours, entrance animation and haptics
• Optional step sounds — 34 to choose from, or one from your phone
• Opens straight from the volume keys if you want

▸ GESTURES
Single, double and triple tap, long press, swipe up, down, in and out — over forty actions to put on any of them, plus a long-press menu you fill yourself. Give an app gestures of its own: in YouTube, swipe up could set the brightness instead.

Actions include the Deck, Quick slider, volume panel, mute, brightness, flashlight, Do Not Disturb, auto-rotate, Wi-Fi and Bluetooth panels, camera, media keys, screenshot, lock screen, Back, Home, recent apps, quick settings and the power menu.

▸ THE DECK
• Pinned apps, and quick-dial contacts that call, text or open a WhatsApp or Telegram chat
• Search: apps, people, sums, numbers and the web — with voice search
• Volume, brightness and media controls
• Timer, calculator, coin toss, notes, checklist, QR scanner and quick toggles

▸ LOOKS
Glass, frosted, paper, AMOLED and more panel styles, with over a dozen entrance animations.

▸ VISIBILITY
• Hide the bar in apps you choose
• Show it only while media plays or during calls
• Lift it above the keyboard, or hide it while you type

▸ ACCESSIBILITY SERVICE
Gesture Volume includes an optional AccessibilityService. It is off until you turn it on in Settings › Accessibility, and the app first shows you what it is for. It is used only for what you switch on:
1. Performing the system actions you assign — lock screen, screenshot, Back, Home, recent apps, notifications, quick settings, power menu.
2. Watching the two volume keys, to open the Quick slider instantly.
3. Noticing which app is open, to hide the bar in apps you chose or use the gestures you gave them.
4. Showing the bar on the lock screen, if you turn that on. While the phone is locked it does only what the lock screen allows: volume, the Quick slider and quick toggles.
5. Drawing the button animation beside your buttons — watching the two volume keys, and noticing the power menu by its window's name only — and the bar over the status bar, each if you turn it on.
It cannot read your screen, does not capture what you copy or type, and nothing it sees leaves your phone. Turn it off any time.

▸ PERMISSIONS
Required: Display over other apps. Asked only when you use the feature: the accessibility service, Notifications, Modify system settings, Do Not Disturb access and Phone.

▸ PRIVACY
No account, no analytics and no location. Settings, notes and checklist stay on your phone. The free version shows ads from AppLovin and partners, who may use your advertising ID; change your ad consent any time in Privacy choices. Pro, a one-time purchase, removes ads.

15 languages, including English, Bangla, Arabic, Hindi and Chinese.
```
3993 characters.

Keep the ACCESSIBILITY SERVICE block whole whatever else you trim — it is what the reviewer reads,
and it must match the in-app disclosure and the declaration below.

---

## What's new (500 max)

For 1.5.5 (build 43):

```
🫧 New: Button animation — press a volume button and the screen's edge answers beside it. 32 animations with their own sounds, bigger the louder it is
🎈 Balloons to blow up and burst, swing women, frogs, a cherry branch, water
🎯 Line them up with your phone's buttons; the power button too
🤝 Show the Quick slider with it, or pause it
💎 Button looks: 10 new Quick slider fills
🎵 34 step sounds, or one from your phone
🔈 Volume no longer stops short or snaps back
```
468 characters.

---

## Data safety form — what to answer

The app itself collects nothing, but **the ad SDKs do**, and Google requires SDK collection to be
declared. Answering "No data collected" while AppLovin is in the build is a policy violation.

**Data collection and security**

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (ad requests use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No** — no account; ad data is controlled through Privacy choices and the ad networks (optional: answer Yes and give the contact email) |

**Data types**

| Data type | Collected | Shared | Ephemeral | Required or optional | Purposes |
| --- | --- | --- | --- | --- | --- |
| Location › Approximate location | Yes | Yes (ad networks derive it from IP) | No | Required for ads in the free version | Advertising or marketing; Fraud prevention, security, compliance |
| Device or other IDs | Yes | Yes | No | Required for ads in the free version | Advertising or marketing; Analytics; Fraud prevention, security, compliance |
| App activity › App interactions | Yes | Yes | No | Required for ads in the free version | Advertising or marketing; Analytics |
| App info and performance › Diagnostics | Yes | Yes | No | Required for ads in the free version | Advertising or marketing; Analytics |
| App info and performance › Crash logs | Yes | Yes | No | Required for ads in the free version | Analytics |
| Financial info › Purchase history | Yes | No | No | Optional | App functionality (unlocking Pro) |

The approximate-location row is for the **ad SDKs**, which derive it from the IP address; the app
itself no longer requests any location permission. Cross-check these against AppLovin's own
guidance before submitting
(https://support.axon.ai/en/max/android/data-and-privacy/google-play-data-safety — and each mediated
network's page), as SDK behaviour changes between versions.

Do **not** declare: contacts, phone numbers, notes, installed apps or the apps the bar is hidden in.
They are processed only on the device and never transmitted.

---

## Accessibility declaration (Play Console › App content › Sensitive app permissions › Accessibility API)

Also record a short video showing: the disclosure dialog, turning the service on in Settings, a bar
action performing Lock screen, choosing an app to hide the bar in, giving an app a gesture of its
own, turning on Show on the lock screen (Visibility) and swiping the bar on the lock screen, turning
on the Button animation and pressing the volume keys (and holding the power button with that option
on), turning on Draw with the accessibility service (Visibility), and the service being switched
off again. The video below predates per-app gestures, the lock screen and the 1.5.5 uses in 5:
record a new one that shows them before submitting the updated declaration.

Video (recorded 14 September 2026, ads-off release build): https://drive.google.com/file/d/1FIes_6bU4usJcwVVLh0mfX5py25D84Ks/view?usp=sharing

```
Gesture Volume is an on-screen edge bar for volume, brightness and shortcuts. Its AccessibilityService is optional and off by default. The user turns it on in Settings › Accessibility only after an in-app disclosure dialog listing every use below, which they must accept. isAccessibilityTool is false: this is a convenience feature, not an assistive technology. The bar itself is drawn by a foreground service with the overlay permission; the accessibility service draws only what is described in 4 and 5.

The service is used for five purposes, each tied to a feature the user chooses:

1. System actions the user assigns to the bar: GLOBAL_ACTION_LOCK_SCREEN, GLOBAL_ACTION_TAKE_SCREENSHOT, GLOBAL_ACTION_BACK, GLOBAL_ACTION_HOME, GLOBAL_ACTION_RECENTS, GLOBAL_ACTION_NOTIFICATIONS, GLOBAL_ACTION_QUICK_SETTINGS and GLOBAL_ACTION_POWER_DIALOG. No other API allows an app to perform these.

2. Volume keys, only when the user sets the Quick slider's volume keys to Instant or turns on the button animation (5): key-event filtering is requested at runtime, only KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN are acted on, every other key is returned unconsumed, and no key is recorded. Used to open the app's volume slider immediately, or to play the button animation. For the quarter second a Volume-down press is undecided, the service also subscribes to TYPE_WINDOW_STATE_CHANGED and checks only whether the event's package is com.android.systemui, so a Volume-down + Power screenshot does not open the slider.

3. Hiding the bar in apps the user selects, and giving apps gestures of their own, only when the user has set either up: the service subscribes to TYPE_WINDOW_STATE_CHANGED and reads the event's package name to know when one of those apps is in front, so the bar can step aside or use that app's gestures. No history is kept.

4. Showing the bar on the lock screen, only when the user turns on "Show on the lock screen" (off by default): Android draws TYPE_APPLICATION_OVERLAY windows beneath the keyguard, so while the keyguard is locked and the screen is on, the service adds the same bar as a TYPE_ACCESSIBILITY_OVERLAY window, and removes it at ACTION_USER_PRESENT. On the lock screen the bar performs only what the lock screen already allows anyone to do — volume, the app's volume slider, brightness, flashlight, Do Not Disturb, ringer, media keys and the system actions in 1. The app's shortcut panel, its menu, launching apps and moving or hiding the bar are refused until the device is unlocked. No accessibility events are used for this; it follows the screen-on, screen-off and user-present broadcasts.

5. Drawing, each only when the user turns it on (off by default): (a) the button animation — on a volume key press, a transparent, non-touchable TYPE_ACCESSIBILITY_OVERLAY window animates beside the physical buttons with the volume level, using the key filtering in 2; with its power-button option, the service checks TYPE_WINDOW_STATE_CHANGED events only for the system power menu's package and class name, and follows the screen-on broadcast; (b) "Draw with the accessibility service" — the foreground service's edge bar is drawn in TYPE_ACCESSIBILITY_OVERLAY windows so it can sit over the status bar, and steps aside while the device is locked.

canRetrieveWindowContent is false: the service cannot read window content, and it does not capture copied or typed text. It subscribes to no accessibility events other than TYPE_WINDOW_STATE_CHANGED, and only while the user has chosen apps to hide the bar in or given an app gestures of its own, while the button animation answers the power button, or, with Instant volume keys or the button animation, for the quarter second a Volume-down press is undecided. No data obtained through the API is transmitted off the device, sold, or used for advertising. The app remains fully usable with the service disabled.
```

---

## Foreground service declaration (App content › Foreground service permissions)

Type: **Special use** (`FOREGROUND_SERVICE_SPECIAL_USE`).

```
The foreground service keeps the user-visible edge bar on screen while other apps are in use. The bar is the app's core feature: the user swipes it to change volume and brightness and taps it to run actions they configured. It must stay available system-wide, it responds to direct user touches rather than doing background work, and no other foreground service type covers a persistent user-facing overlay control. The service is started only when the user switches the bar on, shows an ongoing notification with Hide, Settings and Stop controls, and stops when the user stops it.
```

Attach a short video of the bar being switched on, used over another app, and stopped from the
notification.

Video (recorded 14 September 2026, ads-off release build): https://drive.google.com/file/d/1bO_3olqE8H6aPhh2rDlBRNyfrtitxQXT/view?usp=sharing

---

## Store graphics — what to shoot

Nine phone screenshots, in this order; the first three decide installs.

1. The bar on a home screen, mid-swipe, with the Quick slider open and an animated fill
2. The Deck open: strip, pinned apps and tiles
3. Dynamic position: the bar lying along the top edge in landscape
4. The Quick slider screen, showing the fill animations and colours
5. The Deck's search card with a sum answered inline
6. Appearance, with the live preview and presets
7. The long-press menu beside the bar, on a glass panel style
8. Visibility: the chosen apps beside all apps
9. The Deck's coin toss mid-flip

Feature graphic (1024×500): "Volume & brightness, your way" on the left third and, on the right
third, the Quick slider (Classic shape, 72 dp thick) in a dozen of its fills, in tilted columns, on
the light backdrop of the Bleed screenshots. Play also shows it as the promo video's cover, cropped
to 16:9 with a play button over the middle, so both stay inside that crop and the middle stays clear.

Content rating questionnaire: no violence, sexual content or gambling; **contains ads**; users
cannot interact with each other; the app does not share the user's location itself (ad networks may
derive approximate location from IP) — answer accordingly. Target audience: **13 and over** (18+ is
simplest with mediated ads).

---

## Reply to reviewers, if the accessibility use is questioned

```
The AccessibilityService is optional and disabled by default; the bar is drawn by a foreground service, and the accessibility service draws only a copy of it on the lock screen, where Android hides overlays, the button animation, and the bar over the status bar, each when the user turns it on. Its primary use is letting users assign system actions — lock screen, screenshot, Back, Home, recents, notification shade, quick settings and power menu — to the app's edge bar; these are only reachable through performGlobalAction.

Four further uses each sit behind their own in-app setting, all off by default: acting on the two volume keys to open the app's slider or play the button animation; noticing the foreground app's package name to hide the bar in apps the user picked or to use the gestures the user gave those apps; showing the bar on the lock screen while the device is locked, where it does only what the lock screen already allows and refuses the app's shortcut panel, menu and app launches until the device is unlocked; and drawing the button animation beside the physical buttons, in a non-touchable window, noticing the power menu by its window's package and class name only, and the bar over the status bar.

Before the user is sent to Settings › Accessibility, a disclosure dialog names all five uses, states that nothing else is read and nothing leaves the device, and explains how to turn the service off; the user must accept it. canRetrieveWindowContent is false, the service does not capture copied or typed text, subscribes only to window-state changes, and only while apps are chosen, the button animation answers the power button, or a Volume-down press is undecided, transmits nothing, and the app works fully with the service off.
```
