# Gesture Volume — Play Store listing (1.4.0)

Everything below is ready to paste into Play Console. Character counts are given against Google's
limits. Written to sit beside Edge Deck without copying its wording, and to declare the
AccessibilityService plainly, because that declaration is what the review turns on.

---

## App name (30 max)

```
Gesture Volume: Edge Bar
```
23 characters. Keeps the searchable brand and says what it is. If you would rather not change the
installed name at all, `Gesture Volume` on its own is still correct.

---

## Short description (80 max)

```
Volume, brightness and a deck of tools, from one bar on your screen edge.
```

Alternate, leads with the original promise:
```
Save your volume buttons: a screen-edge bar for volume, brightness and tools.
```

---

## Full description (4000 max)

```
Gesture Volume puts a slim bar on the edge of your screen. Swipe it for volume, swipe it for
brightness, tap it for anything you like — and pull it inward for the Deck, a panel of your apps
and tools that slides out beside it.

It began as a way to save a worn-out volume rocker, and it still is one.

▸ THE BAR
• Swipe up and down for volume, or for brightness
• Follows what is playing — a call, an alarm, a ringing phone, or your music
• Drag it anywhere; it stays where you drop it, per orientation
• Seven looks to start from, including Notch — a slim bar across the top
• Size, colour, opacity, corners, icon and edge distance are yours

▸ GESTURES
Six slots and about forty actions to fill them: single tap, double tap, triple tap, long press,
swipe in, swipe out, and a long-press menu you choose the contents of.

Actions include the Deck, volume panel, mute, adaptive brightness, flashlight, Do Not Disturb,
auto-rotate, media keys, screenshot, lock screen, Back, Home, recents, notification shade, quick
settings, power menu, hide the bar and stop the service.

▸ THE DECK
Pull inward from the bar and a panel slides out. Fill it with what you use:

• Pinned apps, as many as you like
• Quick dial — one-tap call buttons for the people you actually ring
• Search — apps, contacts, sums as you type, numbers routed to the dialler, SMS, WhatsApp or
  Telegram, and the web via Google, YouTube, Maps, Play Store, Wikipedia, DuckDuckGo or Amazon.
  Voice search included.
• Volume — media, ringtone and alarm sliders together
• Brightness — a slider and the adaptive switch
• Media — play, pause and skip whatever is playing
• Timer that keeps running after the Deck closes
• Calculator with a real keypad, answer copied in one tap
• Notes, clipboard history and a checklist
• Weather — local conditions and today's range
• QR scanner, song identification, screenshot, lock, flashlight, Do Not Disturb, auto-rotate,
  Wi-Fi and Bluetooth

Its width, height, corners, colours and auto-close are adjustable, and every tile can be
switched off or moved.

▸ NO NOTIFICATION, IF YOU PREFER
Android will not run an ordinary background service without a permanent notification. Switch on
"Run without a notification" and the accessibility service draws the bar instead — no
notification, and no "display over other apps" permission either. Entirely optional.

▸ ACCESSIBILITY SERVICE
Gesture Volume includes an optional AccessibilityService. It stays off until you turn it on in
Settings › Accessibility, and the app shows you what it is for first. It does three things, each
of which you choose:

1. Performs the system actions you assign to the bar. Lock screen, Screenshot, Back, Home,
   Recent apps, Notifications, Quick settings and the Power menu have no other route on Android.
2. Draws the bar without a permanent notification, if you switch that on.
3. Saves text you copy elsewhere into the app's clipboard history, only if you switch that on.

It reads your screen for nothing else. Nothing is collected, nothing is uploaded, nothing leaves
your phone. Turn it off at any time and the bar carries on with a notification instead.

▸ PERMISSIONS
Required — one of: Display over other apps, or the accessibility service.

Asked for only when you switch on the feature that needs it: Notifications, Modify system settings, Do Not Disturb access, Contacts, Phone, and Approximate location for the weather tile, which sends only a rounded latitude and longitude to open-meteo.com.

▸ PRIVACY
Notes, clipboard history, checklist and settings stay on your phone. No account, no sync, no
analytics. The only network requests are the weather lookup, ads (removed by the one-time Pro
purchase) and Google Play's own billing and update checks. Open source on GitHub:
https://github.com/imamhossain94/GestureVolume

In 15 languages, English, Bangla, Arabic, Hindi and Chinese among them.

Credits — Icons: svgrepo.com · Lottie: S M Rony · Photos: pexels.com
```

Keep the accessibility block whole whatever else you trim: it is the part a reviewer reads.

---

## What's new (500 max)

```
🗂 The Deck — pull inward from the bar for your apps, quick dial, search, timer, calculator,
notes, clipboard, checklist, weather, QR scanner and more
👆 Triple tap, swipe in and swipe out are now action slots of their own
🔒 Lock screen, screenshot, Back, Home, recents, shade, quick settings and power menu are back
🔕 Optional: run without any notification at all
🔦 Flashlight, Do Not Disturb, auto-rotate and media keys
📐 A new Notch preset — a slim bar across the top
```
About 420 characters.

---

## Data safety form — what to answer

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — no data is collected |
| Do you provide a way for users to request that their data is deleted? | Not applicable |

Location is *used* but not collected: the coarse latitude and longitude are sent to open-meteo.com
to fetch a temperature and are not stored or logged. Google's form counts data as "collected" only
when it leaves the device **and is retained**. If you would rather over-declare than argue the
point, tick Location → Approximate location → "App functionality", and mark it as not shared and
processed ephemerally.

Contacts, phone number and clipboard content never leave the device and must **not** be declared
as collected.

---

## Accessibility declaration (Play Console → App content → Accessibility API)

Play asks why you use the API and how you tell users. Paste:

```
Gesture Volume is a floating on-screen bar for volume, brightness and shortcuts. Its
AccessibilityService is optional and off by default. Users enable it themselves in
Settings › Accessibility, after an in-app disclosure screen that lists every use.

The service is used for three purposes, each of which the user opts into inside the app:

1. Performing system actions the user has assigned to the floating bar:
   GLOBAL_ACTION_LOCK_SCREEN, GLOBAL_ACTION_TAKE_SCREENSHOT, GLOBAL_ACTION_BACK,
   GLOBAL_ACTION_HOME, GLOBAL_ACTION_RECENTS, GLOBAL_ACTION_NOTIFICATIONS,
   GLOBAL_ACTION_QUICK_SETTINGS and GLOBAL_ACTION_POWER_DIALOG. There is no other API through
   which an app can perform these.

2. Drawing the floating bar as an accessibility overlay window, so that users who prefer it can
   run the app without a persistent foreground-service notification. This is a user-facing
   setting, off by default.

3. Optional clipboard capture: with the user's explicit opt-in, the service observes text
   selection and copy events so that copied text can be saved to the app's own clipboard
   history, and pastes a chosen item back into a focused field on request. Off by default.

The service subscribes to no accessibility events at all unless clipboard capture is switched on.
No screen content is read for any other purpose. No user data is collected, stored off-device or
transmitted. The service can be disabled at any time in system settings, and the app continues to
work with a foreground-service notification instead.

isAccessibilityTool is declared false, because this is a general convenience feature rather than
an assistive technology.
```

---

## Store graphics — what to shoot

Nine screenshots, in this order. The first three decide installs.

1. The bar on a home screen with the volume level showing mid-swipe
2. The Deck open, strip plus pinned apps and tiles
3. The Deck's search card with a sum answered inline
4. The action picker, showing the grouped grid and the Accessibility badges
5. The Deck settings screen — tiles, shortcuts, quick dial, search
6. Appearance, with the live preview and the preset chips
7. The long-press menu beside the bar
8. Timer and calculator cards side by side (a two-panel composite)
9. The Permissions screen with everything granted

Feature graphic (1024×500): the bar at the right edge with the Deck sliding out, on a plain
gradient. Put the words "Volume. Brightness. Everything else." on the left third.

---

## Reply to reviewers, if the accessibility declaration is questioned

```
The AccessibilityService is optional and disabled by default. It exists so that users can assign
system actions — lock screen, screenshot, Back, Home, recents, notification shade, quick settings
and the power menu — to the app's floating bar; these are only reachable through
performGlobalAction. Two further optional uses, each behind its own in-app switch, are drawing the
bar without a persistent notification and saving copied text to the app's local clipboard history.

Before the user is sent to Settings › Accessibility, the app shows a full-screen disclosure naming
all three uses, stating that no other screen content is read, that nothing is collected or
transmitted, and how to switch the service off again. The service registers for zero accessibility
event types unless clipboard capture is explicitly enabled. The app is fully functional with the
service disabled.
```
