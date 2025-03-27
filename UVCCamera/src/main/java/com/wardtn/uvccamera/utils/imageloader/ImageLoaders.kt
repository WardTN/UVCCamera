package com.wardtn.uvccamera.utils.imageloader

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.wardtn.uvccamera.utils.imageloader.GlideLoader
import com.wardtn.uvccamera.utils.imageloader.ILoader

/**
 * Image loaders
 *
 * @author Created by jiangdg on 2022/3/16
 */
object ImageLoaders {
    /**
     * create a glide image loader
     *
     * @param fragment target is fragment
     * @return [GlideLoader]
     */
    fun of(fragment: Fragment): ILoader<ImageView> = GlideLoader(fragment)

    /**
     * create a glide image loader
     *
     * @param activity target is activity
     * @return [GlideLoader]
     */
    fun of(activity: Activity): ILoader<ImageView> = GlideLoader(activity)

    /**
     * create a glide image loader
     *
     * @param context target is context
     * @return [GlideLoader]
     */
    fun of(context: Context): ILoader<ImageView> = GlideLoader(context)

    /**
     * create a glide image loader
     *
     * @param view target is view
     * @return [GlideLoader]
     */
    fun of(view: View): ILoader<ImageView> = GlideLoader(view)
}