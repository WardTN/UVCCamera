package com.wardtn.uvccamera.render.effect.bean

import androidx.annotation.Keep
import com.wardtn.uvccamera.R
import com.wardtn.uvccamera.render.effect.AbstractEffect

/** camera filter info
 *
 * @author Created by jiangdg on 2022/3/16
 */
@Keep
data class CameraEffect(
    val id: Int,
    val name: String,
    val classifyId: Int,
    val effect: AbstractEffect? = null,
    val coverResId: Int? = null,
    val coverUrl: String? = null
) {
    companion object {
        val NONE_FILTER by lazy {
            CameraEffect(
                ID_NONE_FILTER,
                NAME_NONE,
                CLASSIFY_ID_FILTER,
                coverResId = R.drawable.effect_none
            )
        }

        val NONE_ANIMATION by lazy {
            CameraEffect(
                ID_NONE_ANIMATION,
                NAME_NONE,
                CLASSIFY_ID_ANIMATION,
                coverResId = R.drawable.effect_none
            )
        }

        const val CLASSIFY_ID_FILTER = 1
        const val CLASSIFY_ID_ANIMATION = 2
        const val ID_NONE_FILTER = -1
        const val ID_NONE_ANIMATION = -2
        private const val NAME_NONE = "None"
    }
}