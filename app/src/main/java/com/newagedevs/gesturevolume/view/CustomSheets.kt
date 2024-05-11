package com.newagedevs.gesturevolume.view

import android.content.Context
import android.graphics.text.LineBreaker.JUSTIFICATION_MODE_INTER_WORD
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.codesgood.views.JustifiedTextView
import com.maxkeppeler.sheets.core.Sheet
import com.maxkeppeler.sheets.core.views.SheetsContent
import com.newagedevs.gesturevolume.R

private typealias PositiveListener = () -> Unit

@Suppress("unused")
class CustomSheet() : Sheet() {

    override val dialogTag = "CustomSheet"
    lateinit var description:String
    var price:String? = null

    fun description(value: String) {
        this.description = value
    }

    fun price(value: String) {
        this.price = "${value}/"
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
        val descriptionText = view.findViewById<JustifiedTextView>(R.id.description)
        val priceText = view.findViewById<TextView>(R.id.product_price)
        val priceContainer = view.findViewById<LinearLayout>(R.id.product_price_container)

        descriptionText.text = this.description

        this.price?.let {
            priceContainer.visibility = View.VISIBLE
            priceText.text = it
        }
    }

    fun show(ctx: Context, width: Int? = null, func: CustomSheet.() -> Unit): CustomSheet {
        this.windowContext = ctx
        this.width = width
        this.func()
        this.show()
        return this
    }
}