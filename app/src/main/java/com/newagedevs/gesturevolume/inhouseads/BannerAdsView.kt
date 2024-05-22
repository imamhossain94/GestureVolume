package com.newagedevs.gesturevolume.inhouseads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.newagedevs.gesturevolume.R
import kotlin.math.abs

class BannerAdsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var recyclerView: RecyclerView
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var currentIndex = 0

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.view_in_house_banner_ads, this, true)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setItemViewCacheSize(3)

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                applyPageTransformerEffect()
            }
        })

        startCarousel()
    }

    fun setAdsData(bannerAds: List<BannerAd>, onInstallClick: (String) -> Unit) {
        val adapter = BannerAdsAdapter(context, bannerAds, onInstallClick)
        recyclerView.adapter = adapter
        measureCurrentView()
        applyPageTransformerEffect()
    }

    private fun startCarousel() {
        val animationDurationMillis = 5000 // Change this value to adjust the animation duration
        runnable = object : Runnable {
            override fun run() {
                if (recyclerView.adapter != null && (recyclerView.adapter?.itemCount ?: 0) > 0) {
                    currentIndex = (currentIndex + 1) % recyclerView.adapter!!.itemCount
                    val smoothScroller = object : LinearSmoothScroller(context) {
                        override fun calculateTimeForScrolling(dx: Int): Int {
                            // Adjust the duration here, default value is 250ms
                            return 350
                        }
                    }
                    smoothScroller.targetPosition = currentIndex
                    recyclerView.layoutManager?.startSmoothScroll(smoothScroller)
                }
                handler.postDelayed(this, animationDurationMillis.toLong())
            }
        }
        handler.postDelayed(runnable!!, animationDurationMillis.toLong())
    }


    private fun measureCurrentView() {
        val adapter = recyclerView.adapter as? BannerAdsAdapter ?: return
        if (adapter.itemCount == 0) return

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(currentIndex) ?: return

        view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val height = view.measuredHeight
        val layoutParams = recyclerView.layoutParams
        layoutParams.height = height
        recyclerView.layoutParams = layoutParams
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runnable?.let {
            handler.removeCallbacks(it)
        }
        runnable = null
    }

    private fun applyPageTransformerEffect() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val scrollOffset = recyclerView.computeHorizontalScrollOffset()
        val width = recyclerView.width
        val maxDepth = 400
        val maxRotationAngle = 30
        val maxTranslationX = width / 2

        for (i in 0 until layoutManager.childCount) {
            val view = layoutManager.getChildAt(i) ?: continue
            val position = layoutManager.getPosition(view)
            val start = layoutManager.getDecoratedLeft(view)
            val center = start + view.width / 2

            val distanceFromCenter = abs(center - width / 2f) / width

            // Fade Transformation
            val fadeAlpha = 1 - distanceFromCenter
            view.alpha = fadeAlpha
        }
    }


}


