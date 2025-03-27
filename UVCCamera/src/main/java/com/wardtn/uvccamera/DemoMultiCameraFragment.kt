package com.wardtn.uvccamera

import android.content.Context
import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ToastUtils
import com.wardtn.uvccamera.callback.ICameraStateCallBack
import com.wardtn.uvccamera.callback.ICaptureCallBack
import com.wardtn.uvccamera.camera.CameraUVC
import com.wardtn.uvccamera.camera.bean.CameraRequest
import com.wardtn.uvccamera.databinding.FragmentMultiCameraBinding

/**
 *  Multi-road camera demo
 */
class DemoMultiCameraFragment : MultiCameraFragment(), ICameraStateCallBack {
    private lateinit var mAdapter: CameraAdapter
    private lateinit var mViewBinding: FragmentMultiCameraBinding
    private val mCameraList by lazy {
        ArrayList<MultiCameraClient.ICamera>()
    }
    private val mHasRequestPermissionList by lazy {
        ArrayList<MultiCameraClient.ICamera>()
    }

    override fun onCameraAttached(camera: MultiCameraClient.ICamera) {
        mAdapter.data.add(camera)
        mAdapter.notifyItemInserted(mAdapter.data.size - 1)
        mViewBinding.multiCameraTip.visibility = View.GONE
    }

    override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        mHasRequestPermissionList.remove(camera)
        for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == camera.getUsbDevice().deviceId) {
                camera.closeCamera()
                mAdapter.data.removeAt(position)
                mAdapter.notifyItemRemoved(position)
                break
            }
        }
        if (mAdapter.data.isEmpty()) {
            mViewBinding.multiCameraTip.visibility = View.VISIBLE
        }
    }

    override fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == camera.getUsbDevice().deviceId) {
                val textureView = getViewByPosition(position, R.id.multi_camera_texture_view)
                textureView?.let {
                    cam.openCamera(textureView, getCameraRequest())
                    cam.setCameraStateCallBack(this)
                }

                break
            }
        }
        // request permission for other camera
        mAdapter.data.forEach { cam ->
            val device = cam.getUsbDevice()
            if (!hasPermission(device)) {
                mHasRequestPermissionList.add(cam)
                requestPermission(device)
                return@forEach
            }
        }
    }

    override fun onCameraDisConnected(camera: MultiCameraClient.ICamera) {
        camera.closeCamera()
    }

    fun getViewByPosition(position: Int, viewId: Int): View? {
        val viewHolder = mViewBinding.multiCameraRv.findViewHolderForAdapterPosition(position)
        return viewHolder?.itemView?.findViewById(viewId)
    }



    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?,
    ) {
        if (code == ICameraStateCallBack.State.ERROR) {
            ToastUtils.showLong(msg ?: "open camera failed.")
        }
        for ((position, cam) in mAdapter.data.withIndex()) {
            if (cam.getUsbDevice().deviceId == self.getUsbDevice().deviceId) {
                mAdapter.notifyItemChanged(position, "switch")
                break
            }
        }
    }


    override fun initView() {
        super.initView()
        openDebug(true)
        mAdapter = CameraAdapter { camera, view ->
            when (view.id) {
                R.id.multi_camera_capture_image -> {
                    camera.captureImage(object : ICaptureCallBack {
                        override fun onBegin() {}

                        override fun onError(error: String?) {
                            ToastUtils.showLong(error ?: "capture image failed")
                        }

                        override fun onComplete(path: String?) {
                            ToastUtils.showLong(path ?: "capture image success")
                        }
                    })
                }
                R.id.multi_camera_capture_video -> {
                    if (camera.isRecording()) {
                        camera.captureVideoStop()
                    }
                    camera.captureVideoStart(object : ICaptureCallBack {
                        override fun onBegin() {
//                            mAdapter.notifyItemChanged(position, "video")
                            mAdapter.notifyDataSetChanged()
                        }

                        override fun onError(error: String?) {
//                            mAdapter.notifyItemChanged(position, "video")
                            ToastUtils.showLong(error ?: "capture video failed")
                            mAdapter.notifyDataSetChanged()

                        }

                        override fun onComplete(path: String?) {
//                            mAdapter.notifyItemChanged(position, "video")
                            ToastUtils.showLong(path ?: "capture video success")
                            mAdapter.notifyDataSetChanged()

                        }
                    })
                }
                else -> {
                }
            }
        }

        mAdapter.setData(mCameraList)
        mViewBinding.multiCameraRv.adapter = mAdapter
        mViewBinding.multiCameraRv.layoutManager = GridLayoutManager(requireContext(), 2)

    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentMultiCameraBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    private fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .create()
    }

    inner class CameraAdapter(
        private val onItemClick: (MultiCameraClient.ICamera, View) -> Unit,
    ) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

         val data = mutableListOf<MultiCameraClient.ICamera>()

        fun setData(s: List<MultiCameraClient.ICamera>) {
            this.data.apply {
                clear()
                addAll(s)
            }
        }

        // 定义ViewHolder
        inner class CameraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cameraName: TextView = itemView.findViewById(R.id.multi_camera_name)
            val switchIv: ImageView = itemView.findViewById(R.id.multi_camera_switch)
            val captureVideoIv: ImageView = itemView.findViewById(R.id.multi_camera_capture_video)
            val captureImageIv: ImageView = itemView.findViewById(R.id.multi_camera_capture_image)

            // 为按钮添加点击监听
            fun bind(camera: MultiCameraClient.ICamera) {
                cameraName.text = camera.getUsbDevice().deviceName

                captureVideoIv.setOnClickListener {
                    // 捕获视频点击事件
                    // 在此处处理视频录制逻辑
                    onItemClick(camera, it) // 传递点击的 View
                }

                captureImageIv.setOnClickListener {
                    // 捕获图片点击事件
                    // 在此处处理图片捕获逻辑
                    onItemClick(camera, it) // 传递点击的 View
                }

                // 更新开关图标状态
                if (camera.isCameraOpened()) {
                    switchIv.setImageResource(R.mipmap.ic_switch_on)
                } else {
                    switchIv.setImageResource(R.mipmap.ic_switch_off)
                }

                // 更新视频录制图标状态
                if (camera.isRecording()) {
                    captureVideoIv.setImageResource(R.mipmap.ic_capture_video_on)
                } else {
                    captureVideoIv.setImageResource(R.mipmap.ic_capture_video_off)
                }
            }
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_item_camera, parent, false)
            return CameraViewHolder(view)
        }

        override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
            val camera = data[position]
            holder.bind(camera)
        }

        override fun getItemCount(): Int {
            return data.size
        }

        // 使用 Payloads 进行局部更新
        override fun onBindViewHolder(
            holder: CameraViewHolder,
            position: Int,
            payloads: MutableList<Any>,
        ) {
            val camera = data[position]
            if (payloads.isEmpty()) {
                // 如果 payloads 为空，进行完全绑定
                onBindViewHolder(holder, position)
            } else {
                // 局部更新
                if (payloads.contains("switch")) {
                    if (camera.isCameraOpened()) {
                        holder.switchIv.setImageResource(R.mipmap.ic_switch_on)
                    } else {
                        holder.switchIv.setImageResource(R.mipmap.ic_switch_off)
                    }
                }
                if (payloads.contains("video")) {
                    if (camera.isRecording()) {
                        holder.captureVideoIv.setImageResource(R.mipmap.ic_capture_video_on)
                    } else {
                        holder.captureVideoIv.setImageResource(R.mipmap.ic_capture_video_off)
                    }
                }
            }
        }
    }
}