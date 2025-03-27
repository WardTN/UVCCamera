package com.wardtn.uvccamera

import android.content.Context
import android.hardware.usb.UsbDevice
import com.wardtn.uvccamera.BaseFragment
import com.wardtn.uvccamera.MultiCameraClient
import com.wardtn.uvccamera.callback.IDeviceConnectCallBack
import com.wardtn.uvccamera.usb.USBMonitor

/** Multi-road camera fragment
 *
 * @author Created by jiangdg on 2022/7/20
 *      Modified for v3.3.0 by jiangdg on 2023/1/15
 */
abstract class MultiCameraFragment: BaseFragment() {
    private var mCameraClient: MultiCameraClient? = null
    private val mCameraMap = hashMapOf<Int, MultiCameraClient.ICamera>()

    override fun initData() {
        mCameraClient = MultiCameraClient(requireContext(), object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                context?.let {
                    if (mCameraMap.containsKey(device.deviceId)) {
                        return
                    }
                    generateCamera(it, device).apply {
                        mCameraMap[device.deviceId] = this
                        onCameraAttached(this)
                    }
                    // Initiate permission request when device insertion is detected
                    // If you want to open the specified camera, you need to let isAutoRequestPermission() false
                    // And then you need to call requestPermission(device) in your own Fragment when onAttachDev() called
                    if (isAutoRequestPermission()) {
                        requestPermission(device)
                    }
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                mCameraMap.remove(device?.deviceId)?.apply {
                    setUsbControlBlock(null)
                    onCameraDetached(this)
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                context ?: return
                mCameraMap[device.deviceId]?.apply {
                    setUsbControlBlock(ctrlBlock)
                    onCameraConnected(this)
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                mCameraMap[device?.deviceId]?.apply {
                    onCameraDisConnected(this)
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                mCameraMap[device?.deviceId]?.apply {
                    onCameraDisConnected(this)
                }
            }
        })
        mCameraClient?.register()
    }

    override fun clear() {
        mCameraMap.values.forEach {
            it.closeCamera()
        }
        mCameraMap.clear()
        mCameraClient?.unRegister()
        mCameraClient?.destroy()
        mCameraClient = null
    }


    /**
     * Generate camera
     *
     * @param ctx context [Context]
     * @param device Usb device, see [UsbDevice]
     * @return Inheritor assignment camera api policy
     */
    abstract fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera

    /**
     * On camera is granted permission
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraConnected(camera: MultiCameraClient.ICamera)

    /**
     * On camera is cancelled permission
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraDisConnected(camera: MultiCameraClient.ICamera)

    /**
     * On camera attached
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraAttached(camera: MultiCameraClient.ICamera)

    /**
     * On camera detached
     *
     * @param camera see [MultiCameraClient.ICamera]
     */
    protected abstract fun onCameraDetached(camera: MultiCameraClient.ICamera)

    /**
     * Get current connected cameras
     */
    protected fun getCameraMap() = mCameraMap

    /**
     * Get all usb device list
     */
    protected fun getDeviceList() = mCameraClient?.getDeviceList()

    /**
     * Get camera client
     */
    protected fun getCameraClient() = mCameraClient

    /**
     * If you want to open the specified camera,you need to let isAutoRequestPermission() false.
    *  And then you need to call requestPermission(device) in your own Fragment
     * when onAttachDev() called, default is true.
     */
    protected fun isAutoRequestPermission() = true

    /**
     * Request permission
     *
     * @param device see [UsbDevice]
     */
    protected fun requestPermission(device: UsbDevice?) {
        mCameraClient?.requestPermission(device)
    }

    /**
     * Has permission
     *
     * @param device see [UsbDevice]
     */
    protected fun hasPermission(device: UsbDevice?) = mCameraClient?.hasPermission(device) == true

    protected fun openDebug(debug: Boolean) {
        mCameraClient?.openDebug(debug)
    }

    protected fun isFragmentDetached() = !isAdded || isDetached
}