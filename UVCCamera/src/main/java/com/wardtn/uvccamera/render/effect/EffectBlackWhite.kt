
package com.wardtn.uvccamera.render.effect

import android.content.Context
import com.wardtn.uvccamera.R
import com.wardtn.uvccamera.render.effect.bean.CameraEffect

/** Black White effect
 *
 * @author Created by jiangdg on 2022/1/26
 */
class EffectBlackWhite(ctx: Context) : AbstractEffect(ctx) {

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CameraEffect.CLASSIFY_ID_FILTER

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.effect_blackw_fragment

    companion object {
        const val ID = 100
    }
}