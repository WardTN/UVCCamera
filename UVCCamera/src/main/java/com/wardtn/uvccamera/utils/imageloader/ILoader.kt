package com.wardtn.uvccamera.utils.imageloader

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation

/**
 * Image loader
 *
 * @param T image view
 * @author Created by jiangdg on 2022/3/16
 */
interface ILoader<T> {
    /**
     * Load image from url
     *
     * @param imageView image view
     * @param url image uri
     * @param placeHolder place holder
     */
    fun load(imageView: T, url: String?, placeHolder: Int)

    /**
     * Load image from url width default place holder
     *
     * @param imageView image view
     * @param url image url
     */
    fun load(imageView: T, url: String?)

    /**
     * Load image from resource id
     *
     * @param imageView image view
     * @param resId resource id
     */
    fun load(imageView: T, resId: Int)

    /**
     * Load image from url with transform
     *
     * @param imageView image view
     * @param url image url
     * @param placeHolder place holder
     * @param bitmapTransformation transformation
     */
    fun load(
        imageView: T,
        url: String?,
        placeHolder: Int,
        bitmapTransformation: BitmapTransformation?
    )

    /**
     * Load rounded from url
     *
     * @param imageView image view
     * @param url image url
     * @param placeHolder resource id of place holder
     * @param radius radius of rounded image
     */
    fun loadRounded(imageView: T, url: String?, placeHolder: Int, radius: Float)

    /**
     * Load rounded from url
     *
     * @param imageView image view
     * @param url image url
     * @param placeHolder drawable type of place holder
     * @param radius radius of rounded image
     */
    fun loadRounded(imageView: T, url: String?, placeHolder: Drawable?, radius: Float)

    /**
     * Load rounded from url
     *
     * @param imageView image view
     * @param url image url
     * @param radius radius of rounded image
     */
    fun loadRounded(imageView: T, url: String?, radius: Float)

    /**
     * Load circle from url
     *
     * @param imageView image view
     * @param url image url
     * @param placeHolder resource id of place holder
     */
    fun loadCircle(imageView: T, url: String?, placeHolder: Int)

    /**
     * Load circle from url
     *
     * @param imageView image view
     * @param url image url
     */
    fun loadCircle(imageView: T, url: String?)

    /**
     * Load circle from url
     *
     * @param imageView image view
     * @param resId image resId
     * @param placeHolder resource id of place holder
     */
    fun loadCircle(imageView: T, resId: Int, placeHolder: Int)

    /**
     * Load circle from resource id
     *
     * @param imageView image view
     * @param resId image resId
     */
    fun loadCircle(imageView: T, resId: Int)

    fun loadAsBitmap(url: String?, width: Int, height: Int, listener: OnLoadedResultListener)

    interface OnLoadedResultListener {
        fun onLoadedSuccess(bitmap: Bitmap?)
        fun onLoadedFailed(error: Exception?)
    }
}