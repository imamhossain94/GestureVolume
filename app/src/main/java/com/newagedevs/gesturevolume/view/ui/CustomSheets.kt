package com.newagedevs.gesturevolume.view.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.maxkeppeler.sheets.core.Sheet
import com.maxkeppeler.sheets.core.views.SheetsContent
import com.newagedevs.gesturevolume.R

private typealias PositiveListener = () -> Unit

@Suppress("unused")
class CustomSheet() : Sheet() {

    override val dialogTag = "CustomSheet"
    lateinit var content:String

    fun content(content: String) {
        this.content = content
    }

    fun onPositive(positiveListener: PositiveListener) {
        this.positiveListener = positiveListener
    }

    fun onPositive(@StringRes positiveRes: Int, positiveListener: PositiveListener? = null) {
        this.positiveText = windowContext.getString(positiveRes)
        this.positiveListener = positiveListener
    }

    fun onPositive(positiveText: String, positiveListener: PositiveListener? = null) {
        this.positiveText = positiveText
        this.positiveListener = positiveListener
    }

    override fun onCreateLayoutView(): View {
        return LayoutInflater.from(activity).inflate(R.layout.sheets_custom, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val contentText = view.findViewById<SheetsContent>(R.id.contentTextView)
        contentText.text = this.content
    }

    fun show(ctx: Context, width: Int? = null, func: CustomSheet.() -> Unit): CustomSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        this.show()
        return this
    }
}