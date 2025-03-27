package com.wardtn.uvccamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.provider.MediaStore
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.wardtn.uvccamera.callback.ICaptureCallBack
import com.wardtn.uvccamera.callback.IPreviewDataCallBack
import com.wardtn.uvccamera.MultiCameraClient
import com.wardtn.uvccamera.MultiCameraClient.Companion.CAPTURE_TIMES_OUT_SEC
import com.wardtn.uvccamera.MultiCameraClient.Companion.MAX_NV21_DATA
import com.wardtn.uvccamera.callback.ICameraStateCallBack
import com.wardtn.uvccamera.camera.bean.CameraRequest
import com.wardtn.uvccamera.camera.bean.PreviewSize
import com.wardtn.uvccamera.utils.CameraUtils
import com.wardtn.uvccamera.utils.Logger
import com.wardtn.uvccamera.utils.MediaUtils
import com.wardtn.uvccamera.utils.Utils
import com.wardtn.uvccamera.uvc.IFrameCallback
import com.wardtn.uvccamera.uvc.UVCCamera
import java.io.File
import java.util.concurrent.TimeUnit

/** UVC Camera
 *
 * @author Created by jiangdg on 2023/1/15
 */
class CameraUVC(ctx: Context, device: UsbDevice) : MultiCameraClient.ICamera(ctx, device) {

    private var mUvcCamera: UVCCamera? = null

    private val mCameraPreviewSize by lazy {
        arrayListOf<PreviewSize>()
    }

    /**
     * 帧回调
     */
    private val frameCallBack = IFrameCallback { frame ->
        frame?.apply {
            frame.position(0)
            val data = ByteArray(capacity())
            get(data)
            mCameraRequest?.apply {
                //  检查读取的数据大小是否符合预期的预览尺寸（通常为 YUV 格式的 3/2 倍）
                if (data.size != previewWidth * previewHeight * 3 / 2) {
                    return@IFrameCallback
                }

                // for preview callback
                // 遍历 mPreviewDataCbList，将预览数据（data）传递给每个回调对象。回调函数传入的数据格式为 NV21
                mPreviewDataCbList.forEach { cb ->
                    cb?.onPreviewData(data,
                        previewWidth,
                        previewHeight,
                        IPreviewDataCallBack.DataFormat.NV21)
                }

                // for image
                // 如果 NV21 数据队列的大小已经达到最大限制，则移除最后一个元素。
                if (mNV21DataQueue.size >= MAX_NV21_DATA) {
                    mNV21DataQueue.removeLast()
                }
                //将新的帧数据放入队列的头部
                mNV21DataQueue.offerFirst(data)
                // for video
                // avoid preview size changed
                putVideoData(data)
            }
        }
    }

    override fun getAllPreviewSizes(aspectRatio: Double?): MutableList<PreviewSize> {
        val previewSizeList = arrayListOf<PreviewSize>()
        val isMjpegFormat =
            mCameraRequest?.previewFormat == CameraRequest.PreviewFormat.FORMAT_MJPEG
        if (isMjpegFormat && (mUvcCamera?.supportedSizeList?.isNotEmpty() == true)) {
            mUvcCamera?.supportedSizeList
        } else {
            mUvcCamera?.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)
        }?.let { sizeList ->
            if (sizeList.size > mCameraPreviewSize.size) {
                mCameraPreviewSize.clear()
                sizeList.forEach { size ->
                    val width = size.width
                    val height = size.height
                    mCameraPreviewSize.add(PreviewSize(width, height))
                }
            }
            if (Utils.debugCamera) {
                Logger.i(TAG, "aspect ratio = $aspectRatio, supportedSizeList = $sizeList")
            }
            mCameraPreviewSize
        }?.onEach { size ->
            val width = size.width
            val height = size.height
            val ratio = width.toDouble() / height
            if (aspectRatio == null || aspectRatio == ratio) {
                previewSizeList.add(PreviewSize(width, height))
            }
        }
        return previewSizeList
    }

    /**
     * 开启摄像头实例
     */
    override fun <T> openCameraInternal(cameraView: T) {
        // 检查权限
        if (Utils.isTargetSdkOverP(ctx) && !CameraUtils.hasCameraPermission(ctx)) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "Has no CAMERA permission.")
            Logger.e(TAG,
                "open camera failed, need Manifest.permission.CAMERA permission when targetSdk>=28")
            return
        }
        // 控制摄像头的USB控制块是否为空
        if (mCtrlBlock == null) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR, "Usb control block can not be null ")
            return
        }
        // 1. create a UVCCamera
        val request = mCameraRequest!!
        try {
            mUvcCamera = UVCCamera().apply {
                open(mCtrlBlock)
            }
        } catch (e: Exception) {
            closeCamera()
            postStateEvent(ICameraStateCallBack.State.ERROR,
                "open camera failed ${e.localizedMessage}")
            Logger.e(TAG, "open camera failed.", e)
        }

        // 2. set preview size and register preview callback
        var previewSize = getSuitableSize(request.previewWidth, request.previewHeight).apply {
            mCameraRequest!!.previewWidth = width
            mCameraRequest!!.previewHeight = height
        }
        val previewFormat =
            if (mCameraRequest?.previewFormat == CameraRequest.PreviewFormat.FORMAT_YUYV) {
                UVCCamera.FRAME_FORMAT_YUYV
            } else {
                UVCCamera.FRAME_FORMAT_MJPEG
            }
        try {
            Logger.i(TAG, "getSuitableSize: $previewSize")

            // 检查和设置预览尺寸
            if (!isPreviewSizeSupported(previewSize)) {
                closeCamera()
                postStateEvent(ICameraStateCallBack.State.ERROR, "unsupported preview size")
                Logger.e(TAG,
                    "open camera failed, preview size($previewSize) unsupported-> ${mUvcCamera?.supportedSizeList}")
                return
            }

            initEncodeProcessor(previewSize.width, previewSize.height)
            // if give custom minFps or maxFps or unsupported preview size
            // this method will fail
            mUvcCamera?.setPreviewSize(previewSize.width,
                previewSize.height,
                MIN_FS,
                MAX_FPS,
                previewFormat,
                UVCCamera.DEFAULT_BANDWIDTH)
        } catch (e: Exception) {
            try {
                previewSize = getSuitableSize(request.previewWidth, request.previewHeight).apply {
                    mCameraRequest!!.previewWidth = width
                    mCameraRequest!!.previewHeight = height
                }
                if (!isPreviewSizeSupported(previewSize)) {
                    postStateEvent(ICameraStateCallBack.State.ERROR, "unsupported preview size")
                    closeCamera()
                    Logger.e(TAG,
                        "open camera failed, preview size($previewSize) unsupported-> ${mUvcCamera?.supportedSizeList}")
                    return
                }
                Logger.e(TAG,
                    " setPreviewSize failed(format is $previewFormat), try to use other format...")
                // 尝试设置预览尺寸
                mUvcCamera?.setPreviewSize(previewSize.width,
                    previewSize.height,
                    MIN_FS,
                    MAX_FPS,
                    if (previewFormat == UVCCamera.FRAME_FORMAT_YUYV) {
                        UVCCamera.FRAME_FORMAT_MJPEG
                    } else {
                        UVCCamera.FRAME_FORMAT_YUYV
                    },
                    UVCCamera.DEFAULT_BANDWIDTH)
            } catch (e: Exception) {
                closeCamera()
                postStateEvent(ICameraStateCallBack.State.ERROR, "err: ${e.localizedMessage}")
                Logger.e(TAG, " setPreviewSize failed, even using yuv format", e)
                return
            }
        }
        // if not opengl render or opengl render with preview callback
        // there should opened
        //  设置帧回调（可选） 如果需要获取原始预览数据或捕获原始图像，则设置帧回调，以接收摄像头传回的图像帧数据
        if (!isNeedGLESRender || mCameraRequest!!.isRawPreviewData || mCameraRequest!!.isCaptureRawImage) {
            mUvcCamera?.setFrameCallback(frameCallBack, UVCCamera.PIXEL_FORMAT_YUV420SP)
        }

        // 3. start preview
        when (cameraView) {
            is Surface -> {
                mUvcCamera?.setPreviewDisplay(cameraView)
            }
            is SurfaceTexture -> {
                mUvcCamera?.setPreviewTexture(cameraView)
            }
            is SurfaceView -> {
                mUvcCamera?.setPreviewDisplay(cameraView.holder)
            }
            is TextureView -> {
                mUvcCamera?.setPreviewTexture(cameraView.surfaceTexture)
            }
            else -> {
                throw IllegalStateException("Only support Surface or SurfaceTexture or SurfaceView or TextureView or GLSurfaceView--$cameraView")
            }
        }
        mUvcCamera?.autoFocus = true
        mUvcCamera?.autoWhiteBlance = true
        mUvcCamera?.startPreview()
        mUvcCamera?.updateCameraParams()
        isPreviewed = true
        postStateEvent(ICameraStateCallBack.State.OPENED)
        if (Utils.debugCamera) {
            Logger.i(TAG, " start preview, name = ${device.deviceName}, preview=$previewSize")
        }
    }

    override fun closeCameraInternal() {
        postStateEvent(ICameraStateCallBack.State.CLOSED)
        isPreviewed = false
        releaseEncodeProcessor()
        mUvcCamera?.destroy()
        mUvcCamera = null
        if (Utils.debugCamera) {
            Logger.i(TAG, " stop preview, name = ${device.deviceName}")
        }
    }

    override fun captureImageInternal(savePath: String?, callback: ICaptureCallBack) {
        mSaveImageExecutor.submit {
            if (!CameraUtils.hasStoragePermission(ctx)) {
                mMainHandler.post {
                    callback.onError("have no storage permission")
                }
                Logger.e(TAG, "open camera failed, have no storage permission")
                return@submit
            }
            if (!isPreviewed) {
                mMainHandler.post {
                    callback.onError("camera not previewing")
                }
                Logger.i(TAG, "captureImageInternal failed, camera not previewing")
                return@submit
            }
            val data = mNV21DataQueue.pollFirst(CAPTURE_TIMES_OUT_SEC, TimeUnit.SECONDS)
            if (data == null) {
                mMainHandler.post {
                    callback.onError("Times out")
                }
                Logger.i(TAG, "captureImageInternal failed, times out.")
                return@submit
            }
            mMainHandler.post {
                callback.onBegin()
            }
            val date = mDateFormat.format(System.currentTimeMillis())
            val title = savePath ?: "IMG_AUSBC_$date"
            val displayName = savePath ?: "$title.jpg"
            val path = savePath ?: "$mCameraDir/$displayName"
            val location = Utils.getGpsLocation(ctx)
            val width = mCameraRequest!!.previewWidth
            val height = mCameraRequest!!.previewHeight
            val ret = MediaUtils.saveYuv2Jpeg(path, data, width, height)
            if (!ret) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
                mMainHandler.post {
                    callback.onError("save yuv to jpeg failed.")
                }
                Logger.w(TAG, "save yuv to jpeg failed.")
                return@submit
            }
            val values = ContentValues()
            values.put(MediaStore.Images.ImageColumns.TITLE, title)
            values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, displayName)
            values.put(MediaStore.Images.ImageColumns.DATA, path)
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, date)
            values.put(MediaStore.Images.ImageColumns.LONGITUDE, location?.longitude)
            values.put(MediaStore.Images.ImageColumns.LATITUDE, location?.latitude)
            ctx.contentResolver?.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            mMainHandler.post {
                callback.onComplete(path)
            }
            if (Utils.debugCamera) {
                Logger.i(TAG, "captureImageInternal save path = $path")
            }
        }
    }

    /**
     * Is mic supported
     *
     * @return true camera support mic
     */
    fun isMicSupported() = CameraUtils.isCameraContainsMic(this.device)

    /**
     * Send camera command
     *
     * This method cannot be verified, please use it with caution
     */
    fun sendCameraCommand(command: Int) {
        mCameraHandler?.post {
            mUvcCamera?.sendCommand(command)
        }
    }

    /**
     * Set auto focus
     *
     * @param enable true enable auto focus
     */
    fun setAutoFocus(enable: Boolean) {
        mUvcCamera?.autoFocus = enable
    }

    /**
     * Get auto focus
     *
     * @return true enable auto focus
     */
    fun getAutoFocus() = mUvcCamera?.autoFocus

    /**
     * Reset auto focus
     */
    fun resetAutoFocus() {
        mUvcCamera?.resetFocus()
    }

    /**
     * Set auto white balance
     *
     * @param autoWhiteBalance true enable auto white balance
     */
    fun setAutoWhiteBalance(autoWhiteBalance: Boolean) {
        mUvcCamera?.autoWhiteBlance = autoWhiteBalance
    }

    /**
     * Get auto white balance
     *
     * @return true enable auto white balance
     */
    fun getAutoWhiteBalance() = mUvcCamera?.autoWhiteBlance

    /**
     * Set zoom
     *
     * @param zoom zoom value, 0 means reset
     */
    fun setZoom(zoom: Int) {
        mUvcCamera?.zoom = zoom
    }

    /**
     * Get zoom
     */
    fun getZoom() = mUvcCamera?.zoom

    /**
     * Reset zoom
     */
    fun resetZoom() {
        mUvcCamera?.resetZoom()
    }

    /**
     * Set gain
     *
     * @param gain gain value, 0 means reset
     */
    fun setGain(gain: Int) {
        mUvcCamera?.gain = gain
    }

    /**
     * Get gain
     */
    fun getGain() = mUvcCamera?.gain

    /**
     * Reset gain
     */
    fun resetGain() {
        mUvcCamera?.resetGain()
    }

    /**
     * Set gamma
     *
     * @param gamma gamma value, 0 means reset
     */
    fun setGamma(gamma: Int) {
        mUvcCamera?.gamma = gamma
    }

    /**
     * Get gamma
     */
    fun getGamma() = mUvcCamera?.gamma

    /**
     * Reset gamma
     */
    fun resetGamma() {
        mUvcCamera?.resetGamma()
    }

    /**
     * Set brightness
     *
     * @param brightness brightness value, 0 means reset
     */
    fun setBrightness(brightness: Int) {
        mUvcCamera?.brightness = brightness
    }

    /**
     * Get brightness
     */
    fun getBrightness() = mUvcCamera?.brightness

    fun getBrightnessMax() = mUvcCamera?.brightnessMax

    fun getBrightnessMin() = mUvcCamera?.brightnessMin

    /**
     * Reset brightnes
     */
    fun resetBrightness() {
        mUvcCamera?.resetBrightness()
    }

    /**
     * Set contrast
     *
     * @param contrast contrast value, 0 means reset
     */
    fun setContrast(contrast: Int) {
        mUvcCamera?.contrast = contrast
    }

    /**
     * Get contrast
     */
    fun getContrast() = mUvcCamera?.contrast

    /**
     * Reset contrast
     */
    fun resetContrast() {
        mUvcCamera?.resetContrast()
    }

    /**
     * Set sharpness
     *
     * @param sharpness sharpness value, 0 means reset
     */
    fun setSharpness(sharpness: Int) {
        mUvcCamera?.sharpness = sharpness
    }

    /**
     * Get sharpness
     */
    fun getSharpness() = mUvcCamera?.sharpness

    /**
     * Reset sharpness
     */
    fun resetSharpness() {
        mUvcCamera?.resetSharpness()
    }

    /**
     * Set saturation
     *
     * @param saturation saturation value, 0 means reset
     */
    fun setSaturation(saturation: Int) {
        mUvcCamera?.saturation = saturation
    }

    /**
     * Get saturation
     */
    fun getSaturation() = mUvcCamera?.saturation

    /**
     * Reset saturation
     */
    fun resetSaturation() {
        mUvcCamera?.resetSaturation()
    }

    /**
     * Set hue
     *
     * @param hue hue value, 0 means reset
     */
    fun setHue(hue: Int) {
        mUvcCamera?.hue = hue
    }

    /**
     * Get hue
     */
    fun getHue() = mUvcCamera?.hue

    /**
     * Reset saturation
     */
    fun resetHue() {
        mUvcCamera?.resetHue()
    }

    companion object {
        private const val TAG = "CameraUVC"
        private const val MIN_FS = 1
        private const val MAX_FPS = 61
    }
}