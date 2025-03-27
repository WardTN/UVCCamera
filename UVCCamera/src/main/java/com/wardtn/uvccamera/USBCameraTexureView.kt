package com.wardtn.uvccamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.TextureView
import com.wardtn.uvccamera.callback.ICameraStateCallBack
import com.wardtn.uvccamera.callback.IDeviceConnectCallBack
import com.wardtn.uvccamera.camera.CameraUVC
import com.wardtn.uvccamera.camera.bean.CameraRequest
import com.wardtn.uvccamera.render.env.RotateType
import com.wardtn.uvccamera.usb.USBMonitor
import com.wardtn.uvccamera.utils.Logger
import com.wardtn.uvccamera.utils.SettableFuture
import com.wardtn.uvccamera.widget.AspectRatioTextureView
import com.wardtn.uvccamera.widget.IAspectRatio
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class USBCameraTexureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : AspectRatioTextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener,
    ICameraStateCallBack {

    companion object {
        private const val TAG = "USBCameraTexureView"
    }

    private var mCameraClient: MultiCameraClient? = null
    private val mCameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()
    private val mRequestPermission: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    private var mCurrentCamera: SettableFuture<MultiCameraClient.ICamera>? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        registerMultiCamera()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceSizeChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        unRegisterMultiCamera()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }


    protected fun unRegisterMultiCamera() {
        mCameraMap.values.forEach {
            it.closeCamera()
        }
        mCameraMap.clear()
        mCameraClient?.unRegister()
        mCameraClient?.destroy()
        mCameraClient = null
    }


    protected fun registerMultiCamera() {
        mCameraClient = MultiCameraClient(context, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                context?.let {
                    if (mCameraMap.containsKey(device.deviceId)) {
                        return
                    }
                    //生成 Camera 实例
                    generateCamera(it, device).apply {
                        mCameraMap[device.deviceId] = this
                    }
                    // Initiate permission request when device insertion is detected
                    // If you want to open the specified camera, you need to override getDefaultCamera()
                    if (mRequestPermission.get()) {
                        return@let
                    }
//                    getDefaultCamera()?.apply {
//                        if (vendorId == device.vendorId && productId == device.productId) {
//                            Logger.i(CameraFragment.TAG, "default camera pid: $productId, vid: $vendorId")
//                            requestPermission(device)
//                        }
//                        return@let
//                    }
                    requestPermission(device)
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                }
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                context ?: return
                mCameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                }?.also { camera ->
                    try {
                        mCurrentCamera?.cancel(true)
                        mCurrentCamera = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    mCurrentCamera = SettableFuture()
                    mCurrentCamera?.set(camera)
                    openCamera(this@USBCameraTexureView)
                    Logger.i(TAG,
                        "camera connection. pid: ${device.productId}, vid: ${device.vendorId}")
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
            ) {
                closeCamera()
                mRequestPermission.set(false)
            }

            override fun onCancelDev(device: UsbDevice?) {
                mRequestPermission.set(false)
                try {
                    mCurrentCamera?.cancel(true)
                    mCurrentCamera = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
        mCameraClient?.register()
    }

    protected fun openCamera(st: IAspectRatio? = null) {
        when (st) {
            is TextureView, is SurfaceView -> {
                st
            }
            else -> {
                null
            }
        }.apply {
            getCurrentCamera()?.openCamera(this, getCameraRequest())
            getCurrentCamera()?.setCameraStateCallBack(this@USBCameraTexureView)
        }
    }

    protected fun closeCamera() {
        getCurrentCamera()?.closeCamera()
    }


    protected open fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder().setPreviewWidth(640).setPreviewHeight(480)
            .setRenderMode(CameraRequest.RenderMode.OPENGL).setDefaultRotateType(RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG).setAspectRatioShow(true)
            .setCaptureRawImage(false).setRawPreviewData(false).create()
    }


    private fun surfaceSizeChanged(surfaceWidth: Int, surfaceHeight: Int) {
        getCurrentCamera()?.setRenderSize(surfaceWidth, surfaceHeight)
    }


    /**
     * Get current opened camera
     *
     * @return current camera, see [MultiCameraClient.ICamera]
     */
    protected fun getCurrentCamera(): MultiCameraClient.ICamera? {
        return try {
            mCurrentCamera?.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate camera
     *
     * @param ctx context [Context]
     * @param device Usb device, see [UsbDevice]
     * @return Inheritor assignment camera api policy
     */
    protected open fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    /**
     * Request permission
     *
     * @param device see [UsbDevice]
     */
    protected fun requestPermission(device: UsbDevice?) {
        mRequestPermission.set(true)
        mCameraClient?.requestPermission(device)
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?,
    ) {
//        when (code) {
//            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
//            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
//            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
//        }
    }
}