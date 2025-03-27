package com.wardtn.uvccamera.render.internal

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.wardtn.uvccamera.R
import kotlin.math.cos
import kotlin.math.sin

/** Inherit from AbstractFboRender
 *      render preview data with preview_vertex.glsl and base_fragment.glsl
 *
 * @author Created by jiangdg on 2021/12/27
 */
class CaptureRender(context: Context) : AbstractFboRender(context) {
    private var mMVPMatrixHandle: Int = -1
    private var mMVPMatrix = FloatArray(16)

    override fun init() {
        Matrix.setIdentityM(mMVPMatrix, 0)
        // 上下翻转 (绕x轴180度)
        // glReadPixels读取的是大端数据，但是我们保存的是小端
        // 故需要将图片上下颠倒为正
        val radius = (180 * Math.PI / 180.0).toFloat()
        mMVPMatrix[5] *= cos(radius.toDouble()).toFloat()
        mMVPMatrix[6] += (-sin(radius.toDouble())).toFloat()
        mMVPMatrix[9] += sin(radius.toDouble()).toFloat()
        mMVPMatrix[10] *= cos(radius.toDouble()).toFloat()
        // 获取句柄
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
    }

    override fun beforeDraw() {
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0)
    }

    override fun getVertexSourceId(): Int = R.raw.capture_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}