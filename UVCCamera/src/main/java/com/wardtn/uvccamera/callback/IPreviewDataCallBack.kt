package com.wardtn.uvccamera.callback

/**
 *  Camera preview data callback
 */
interface IPreviewDataCallBack {

    fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: DataFormat)

    enum class DataFormat {
        NV21, RGBA
    }
}