package com.newagedevs.gesturevolume.utils

/** One remembered clipboard item. Newest first in every list the app hands out. */
data class ClipboardEntry(
    val id: Long,
    val text: String,
    val timeMillis: Long
)
