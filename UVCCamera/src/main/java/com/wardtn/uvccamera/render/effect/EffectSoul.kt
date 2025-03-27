
package com.wardtn.uvccamera.render.effect

import android.content.Context
import android.opengl.GLES20
import com.wardtn.uvccamera.R
import com.wardtn.uvccamera.render.effect.bean.CameraEffect

/** Soul effect
 *
 * @author Created by jiangdg on 2022/2/17
 */
class EffectSoul(context: Context): AbstractEffect(context) {

    private var mTimeStampsHandler = -1
    private var mTimeCount = 0

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CameraEffect.CLASSIFY_ID_ANIMATION

    override fun init() {
        mTimeStampsHandler = GLES20.glGetUniformLocation(mProgram, "timeStamps")
    }

    override fun beforeDraw() {
        if (mTimeCount > 65535) {
            mTimeCount = 0
        }
        GLES20.glUniform1f(mTimeStampsHandler, (++mTimeCount % 9).toFloat())
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.effect_soul_fragment

    companion object {
        const val ID = 200
    }
}