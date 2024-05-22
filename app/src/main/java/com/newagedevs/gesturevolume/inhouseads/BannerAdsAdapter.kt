package com.newagedevs.gesturevolume.inhouseads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.newagedevs.gesturevolume.R

class BannerAdsAdapter(
    private val context: Context,
    private val bannerAds: List<BannerAd>,
    private val onInstallClick: (String) -> Unit
) : RecyclerView.Adapter<BannerAdsAdapter.BannerAdViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerAdViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_banner_ad, parent, false)
        return BannerAdViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerAdViewHolder, position: Int) {
        val bannerAd = bannerAds[position]
        holder.bind(bannerAd)
    }

    override fun getItemCount(): Int = bannerAds.size

    inner class BannerAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.app_icon)
        private val titleView: TextView = itemView.findViewById(R.id.title)
        private val descriptionView: TextView = itemView.findViewById(R.id.description)
        private val installButton: View = itemView.findViewById(R.id.install_button)

        fun bind(bannerAd: BannerAd) {
            Glide.with(context).load(bannerAd.appIconUrl).into(imageView)
            titleView.text = bannerAd.title
            descriptionView.text = bannerAd.description

            installButton.setOnClickListener {
                onInstallClick(bannerAd.appLink)
            }
        }
    }
}
