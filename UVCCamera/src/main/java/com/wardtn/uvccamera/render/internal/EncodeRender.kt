
package com.wardtn.uvccamera.render.internal

import android.content.Context
import android.opengl.EGLContext
import android.view.Surface
import com.wardtn.uvccamera.R
import com.wardtn.uvccamera.render.env.EGLEvn

/** Inherit from AbstractFboRender
 *      render data to EGL from fbo and encode it
 *
 * @author Created by jiangdg on 2021/12/27
 */
class EncodeRender(context: Context): AbstractRender(context) {

    private var mEgl: EGLEvn? = null

    fun initEGLEvn(glContext: EGLContext) {
        mEgl = EGLEvn()
        mEgl?.initEgl(glContext)
    }

    fun setupSurface(surface: Surface) {
        mEgl?.setupSurface(surface)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}