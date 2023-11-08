package com.newagedevs.gesturevolume.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Entity
@Parcelize
data class AppHandler(
    val enabledHandler: Boolean,
    var gravity: String?,
    var gravityLand: String?,
    val topMargin: Float?,
    val topMarginLand: Float?,
    val color: Int?,
    val colorLand: Int?,
    val size: String?,
    val sizeLand: String?,
    val width: String?,
    val widthLand: String?,
    val clickAction: String?,
    val doubleClickAction: String?,
    val longClickAction: String?,
    val upperSwipe: String?,
    val bottomSwipe: String?,
    val activeLand: Boolean
) : Parcelable {
    @IgnoredOnParcel
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
