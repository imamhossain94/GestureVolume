package com.newagedevs.edgegestures.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Entity
@Parcelize
data class AppHandler(
    var gravity: String?,
    val topMargin: Float?,
    val color: Int?,
    val size: String?,
    val width: String?,
) : Parcelable {
    @IgnoredOnParcel
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
