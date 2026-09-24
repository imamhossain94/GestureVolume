package com.newagedevs.gesturevolume.utils

import java.time.LocalDate

/**
 * Every release the app has had, newest first, for the What's new screen — with the install
 * milestones between them, the way the Play Store's own changelog lists them.
 *
 * The notes are the ones Google Play showed for each version, in English, as Play shows them.
 * Nothing was published for 1.0.6, 1.0.9 and 1.1.1 that has survived, so theirs are written from
 * what went into those releases.
 *
 * A release goes at the top of [HISTORY] when its version name is bumped. Until it is out its date
 * stays null, and the screen shows no date for it.
 */
object ReleaseNotes {

    /** One line of the history, a release or a milestone. */
    sealed interface Entry

    /**
     * One release and what it brought.
     *
     * @param firstRelease the version the app first went out as, which the screen says.
     */
    data class Release(
        val version: String,
        val date: LocalDate?,
        val notes: List<String>,
        val firstRelease: Boolean = false,
    ) : Entry

    /** Installs passing a mark, on the day Play reported it. */
    data class Milestone(val installs: Int, val date: LocalDate) : Entry

    val HISTORY: List<Entry> = listOf(
        Release(
            version = "1.5.1",
            date = null,
            notes = listOf(
                "🙂 Simple or Advanced — the original round button is back, and the extras fold away until you want them",
                "🙋 Gestures for chosen apps — give YouTube, or any app, gestures of its own",
                "🟩 Pixels — a new Quick panel fill: a grid of lights with 19 patterns you can tune",
                "✨ Shaders — 12 live effects for the Quick panel, from lava lamp to liquid chrome (Android 13+)",
                "🏀 Bouncy — buttons and cards spring back when tapped, lists stretch like rubber at their ends",
                "⌨️ The bar moves above the keyboard, or hides while you type",
                "🎵 Show the bar only while something plays, or during calls",
                "👆 Swipe up and down can do any action, or move the volume a fixed 5, 10 or 20%",
                "📶 New actions: Wi-Fi, Bluetooth and Internet, ring or vibrate, camera and voice assistant",
                "💬 Quick dial straight into a WhatsApp, Telegram or SMS chat",
                "📏 A bar as thin as 1 dp, and tabs with no line along the screen edge",
                "🆕 This What's new screen",
                "🛠 Fixes and updated libraries",
            ),
        ),
        Release(
            version = "1.5.0",
            date = LocalDate.of(2026, 9, 14),
            notes = listOf(
                "🎚 Quick slider — a full-height track that grows out of the bar, with animated fills, your own colors and an optional adaptive step sound",
                "🔄 Dynamic position — the bar follows your phone's edge when you rotate",
                "👁 Visibility — hide the bar in chosen apps",
                "🪟 New panel styles and entrance animations",
                "🪙 A redesigned coin toss",
                "🔐 Privacy choices — change your ad consent any time",
                "🛠 Fixes, including a freeze when opening the app",
            ),
        ),
        Release(
            version = "1.3.4",
            date = LocalDate.of(2026, 9, 4),
            notes = listOf(
                "👆 Drag the bar anywhere — it stays where you drop it",
                "🙈 Hide it from the press-and-hold menu; the floating ✕ is gone",
                "👻 A hidden bar no longer reappears by itself",
                "🔒 Screen lock removed",
            ),
        ),
        Release(
            version = "1.3.1",
            date = LocalDate.of(2026, 8, 30),
            notes = listOf(
                "✨ Drag the handler anywhere you want — portrait and landscape remember their own spots",
                "🧲 Optional snap to edges",
                "❌ Drag onto the X to hide it",
                "📋 Hold the bar for a quick-action menu you can customise",
                "🔊 Volume % right on the bar as you swipe",
                "🔕 Quieter ongoing notification",
                "⚡ Smaller download, updated libraries",
            ),
        ),
        Release(
            version = "1.2.9",
            date = LocalDate.of(2026, 8, 4),
            notes = listOf(
                "✋ Long press to move the handler",
                "🔊 Swipe gestures fixed — no more conflicts",
                "☀️ Brightness control + edge offset",
                "🎉 All features free, Pro just removes ads",
            ),
        ),
        Release(
            version = "1.2.8",
            date = LocalDate.of(2026, 7, 18),
            notes = listOf(
                "✨ Smoother onboarding — swipe between welcome screens",
                "🎨 Refreshed, lighter card designs throughout the app",
                "🔧 Updated for compatibility with the latest Android version",
                "🐞 Bug fixes and performance improvements",
            ),
        ),
        Release(
            version = "1.2.7",
            date = LocalDate.of(2026, 6, 18),
            notes = listOf("🐞 Bug fixes and performance improvements"),
        ),
        Release(
            version = "1.2.6",
            date = LocalDate.of(2026, 6, 1),
            notes = listOf("🐞 Bug fixes and performance improvements"),
        ),
        Release(
            version = "1.2.5",
            date = LocalDate.of(2026, 4, 18),
            notes = listOf("Bug fixes and policy compliance update"),
        ),
        Milestone(installs = 10_000, date = LocalDate.of(2026, 4, 8)),
        Release(
            version = "1.2.4",
            date = LocalDate.of(2026, 3, 25),
            notes = listOf(
                "🎨 Enhanced handler appearance experience",
                "🔥 Updated UI/UX",
                "🌍 Added language support",
                "🐞 Added troubleshooting features",
                "⚒️ Bug fixes and performance improvements",
            ),
        ),
        Release(
            version = "1.2.2",
            date = LocalDate.of(2025, 12, 8),
            notes = listOf("🔥 UI updated", "🐞 Bug fixes"),
        ),
        Release(
            version = "1.1.9",
            date = LocalDate.of(2025, 11, 21),
            notes = listOf("🐞 Bug fixes & performance improvements"),
        ),
        Release(
            version = "1.1.7",
            date = LocalDate.of(2025, 10, 20),
            notes = listOf("🔧 More customization options", "🐞 Bug fixes & performance improvements"),
        ),
        Milestone(installs = 5_000, date = LocalDate.of(2025, 2, 5)),
        Release(
            version = "1.1.3",
            date = LocalDate.of(2024, 12, 15),
            notes = listOf(
                "🗝️ Made some Pro features free",
                "🔥 Android 15 support",
                "⚒️ Bug fixes and performance improvements",
            ),
        ),
        Release(
            version = "1.1.1",
            date = LocalDate.of(2024, 5, 22),
            notes = listOf("🔇 Mute or unmute action", "🛠 Fixes for Pro activation"),
        ),
        Release(
            version = "1.0.9",
            date = LocalDate.of(2024, 5, 11),
            notes = listOf(
                "🎨 A refreshed look",
                "👑 Pro, a one-time purchase",
                "👆 Smoother touch on the handler",
            ),
        ),
        Release(
            version = "1.0.6",
            date = LocalDate.of(2023, 11, 7),
            notes = listOf(
                "🎚 Swipe the bar on the screen edge to change the volume",
                "👆 Tap, double tap and long press actions, including hiding the bar and opening the app",
                "🎵 Music overlay",
                "📱 Its own place in landscape",
            ),
            firstRelease = true,
        ),
    )

    val releases: List<Release> get() = HISTORY.filterIsInstance<Release>()

    /** A version name without a build's suffix: "1.5.1-sideload" is 1.5.1. */
    fun normalize(versionName: String): String = versionName.substringBefore('-').trim()

    /** The release [versionName] is, when there are notes for it. */
    fun releaseFor(versionName: String): Release? {
        val version = normalize(versionName)
        return releases.firstOrNull { it.version == version }
    }

    /**
     * A note split into its leading emoji and its words, so the emoji can stand in a column of its
     * own. A note without one — some were published plain — has none.
     */
    fun splitEmoji(note: String): Pair<String?, String> {
        val first = note.substringBefore(' ')
        val isEmoji = first.isNotEmpty() && first != note && first.none { it.isLetterOrDigit() }
        return if (isEmoji) first to note.substringAfter(' ').trim() else null to note
    }
}
