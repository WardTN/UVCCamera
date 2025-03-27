package com.wardtn.uvccamera.camera.bean

import androidx.annotation.Keep
import com.wardtn.uvccamera.render.effect.AbstractEffect
import com.wardtn.uvccamera.render.env.RotateType

@Keep
class CameraRequest private constructor() {
    var previewWidth: Int = DEFAULT_WIDTH  //预览宽高
    var previewHeight: Int = DEFAULT_HEIGHT
    var renderMode: RenderMode = RenderMode.OPENGL // 渲染模式
    var isAspectRatioShow: Boolean = true // 是否显示宽高比
    var isRawPreviewData: Boolean = false // 是否使用原始预览数据
    var isCaptureRawImage: Boolean = false //是否捕获原始图像
    var defaultEffect: AbstractEffect? = null // 滤镜
    var defaultRotateType: RotateType = RotateType.ANGLE_0 // 旋转角度
    var audioSource: AudioSource = AudioSource.SOURCE_AUTO // 录音音频源
    var previewFormat: PreviewFormat = PreviewFormat.FORMAT_MJPEG //预览格式

    @kotlin.Deprecated("Deprecated since version 3.3.0")
    var cameraId: String = ""

    @kotlin.Deprecated("Deprecated since version 3.3.0")
    var isFrontCamera: Boolean = false

    /**
     * Camera request builder
     *
     * @constructor Create empty Camera request builder
     */
    class Builder {
        private val mRequest by lazy {
            CameraRequest()
        }

        /**
         * Set front camera
         *
         * @param isFrontCamera front camera flag
         * @return [Builder]
         */
        @kotlin.Deprecated("Deprecated since version 3.3.0")
        fun setFrontCamera(isFrontCamera: Boolean): Builder {
            mRequest.isFrontCamera = isFrontCamera
            return this
        }

        /**
         * Set preview width
         *
         * @param width camera preview width
         * @return see [Builder]
         */
        fun setPreviewWidth(width: Int): Builder {
            mRequest.previewWidth = width
            return this
        }

        /**
         * Set preview height
         *
         * @param height camera preview height
         * @return [Builder]
         */
        fun setPreviewHeight(height: Int): Builder {
            mRequest.previewHeight = height
            return this
        }

        /**
         * Set camera id, not for uvc
         *
         * @param cameraId camera id
         * @return see [Builder]
         */
        @kotlin.Deprecated("Deprecated since version 3.3.0")
        fun setCameraId(cameraId: String): Builder {
            mRequest.cameraId = cameraId
            return this
        }

        /**
         * Using opengl es render or not
         *
         * @param renderMode default is [RenderMode.OPENGL]
         * @return see [Builder]
         */
        fun setRenderMode(renderMode: RenderMode): Builder {
            mRequest.renderMode = renderMode
            return this
        }

        /**
         * Set aspect ratio show
         *
         * @param isAspectRatioShow  default is true
         * @return see [Builder]
         */
        fun setAspectRatioShow(isAspectRatioShow: Boolean): Builder {
            mRequest.isAspectRatioShow = isAspectRatioShow
            return this
        }

        /**
         * Set should need raw preview data when OpenGL ES render opened
         *
         * @param isRawPreviewData default is false
         * @return see [Builder]
         */
        fun setRawPreviewData(isRawPreviewData: Boolean): Builder {
            mRequest.isRawPreviewData = isRawPreviewData
            return this
        }

        /**
         * Capture raw jpeg image when OpenGL ES render opened
         *  You also should set setRawPreviewData(true) at the same time.
         *
         * @param isCaptureRawImage default is false
         * @return see [Builder]
         */
        fun setCaptureRawImage(isCaptureRawImage: Boolean): Builder {
            mRequest.isCaptureRawImage = isCaptureRawImage
            return this
        }

        /**
         * Set default effect, only OPENGL mode effect
         *
         * @param defaultEffect default is null
         * @return  see [Builder]
         */
        fun setDefaultEffect(defaultEffect: AbstractEffect): Builder {
            mRequest.defaultEffect = defaultEffect
            return this
        }

        /**
         * Set default rotate type, only OPENGL mode useful
         *
         * @param defaultRotateType default is [RotateType.ANGLE_0]
         * @return  see [Builder]
         */
        fun setDefaultRotateType(defaultRotateType: RotateType): Builder {
            mRequest.defaultRotateType = defaultRotateType
            return this
        }

        /**
         * Set audio source
         *
         * @param source audio record source, default is [AudioSource.SOURCE_AUTO]
         * @return see [Builder]
         */
        fun setAudioSource(source: AudioSource): Builder {
            mRequest.audioSource = source
            return this
        }

        /**
         * Set preview format
         *
         * @param format preview format, default is [PreviewFormat.FORMAT_MJPEG]
         * @return see [Builder]
         */
        fun setPreviewFormat(format: PreviewFormat): Builder {
            mRequest.previewFormat = format
            return this
        }

        /**
         * Create a CameraRequest
         *
         * @return see [CameraRequest]
         */
        fun create(): CameraRequest {
            return mRequest
        }
    }

    /**
     * Camera render mode
     *
     * NORMAL: normal render
     * OPENGL: opengl es render,default mode.
     */
    enum class RenderMode {
        NORMAL,
        OPENGL
    }

    /**
     * Audio record source
     *
     * NONE: not record audio
     * SOURCE_SYS_MIC: record from system mic
     * SOURCE_DEV_MIC: record from camera device mic(UAC)
     * SOURCE_AUTO: record from camera device mic, if unsupported
     *              switch to system mic.default mode.
     */
    enum class AudioSource {
        NONE,
        SOURCE_SYS_MIC, //系统麦克风
        SOURCE_DEV_MIC, //设备麦克风
        SOURCE_AUTO     // 单纯渲染视频
    }

    /**
     * Preview format
     *
     * FORMAT_MJPEG: default format with high frame rate
     * FORMAT_YUYV: yuv format with lower frame rate
     */
    enum class PreviewFormat {
        FORMAT_MJPEG,
        FORMAT_YUYV
    }

    companion object {
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 480
    }
}