
package com.wardtn.uvccamera.render.internal

import android.content.Context
import android.view.Surface
import com.wardtn.uvccamera.R
import com.wardtn.uvccamera.render.env.EGLEvn

/** Inherit from AbstractFboRender
 *      render data to screen from fbo with base_vertex.glsl and base_fragment.glsl
 *
 * @author Created by jiangdg on 2021/12/27
 */
class ScreenRender(context: Context) : AbstractRender(context) {
    private var mEgl: EGLEvn? = null

    fun initEGLEvn() {
        mEgl = EGLEvn()
        mEgl?.initEgl()
    }

    fun setupSurface(surface: Surface?, surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
        mEgl?.setupSurface(surface, surfaceWidth, surfaceHeight)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    fun getCurrentContext() = mEgl?.getEGLContext()

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}