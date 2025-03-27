package com.wardtn.uvccamera.encode.muxer

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.DateUtils
import com.wardtn.uvccamera.callback.ICaptureCallBack
import com.wardtn.uvccamera.utils.Logger
import com.wardtn.uvccamera.utils.MediaUtils
import com.wardtn.uvccamera.utils.Utils

import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 使用mediaMuxer实现音视频混合
 * 1. 创建对象 mMediaMuxer = MediaMuxer(path!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
 * 2. 添加 视频轨/音频轨道 根据添加顺序 生成Index 在写入时会用到
 * addTrack(mediaFormat)
 * 3.开启混合 mediaMuxer.start()
 * 4.写入数据  mMediaMuxer?.writeSampleData(index, outputBuffer, bufferInfo)
 * 5.释放资源
 */
class Mp4Muxer(
    context: Context?,
    callBack: ICaptureCallBack,
    private var path: String? = null,
    private val durationInSec: Long = 0, //录制时长
    private val isVideoOnly: Boolean = false
) {
    private var mContext: Context? = null
    private var mMediaMuxer: MediaMuxer? = null
    private var mFileSubIndex: Int = 0
    @Volatile
    private var mVideoTrackerIndex = -1
    @Volatile
    private var mAudioTrackerIndex = -1
    private var mVideoFormat: MediaFormat? = null
    private var mAudioFormat: MediaFormat? = null
    private var mBeginMillis: Long = 0
    private var mCaptureCallBack: ICaptureCallBack? = null
    private var mMainHandler: Handler = Handler(Looper.getMainLooper())
    private var mOriginalPath: String? = null
    private var mVideoPts: Long = 0L
    private var mAudioPts: Long = 0L
    private val mDateFormat by lazy {
        SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
    }


    // MediaMuxer 使用流程
    // 。

    private val mCameraDir by lazy {
//        getCameraPath()
        context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath
    }

    init {
        this.mCaptureCallBack = callBack
        this.mContext= context
        try {
            if (path.isNullOrEmpty()) {
                val date = mDateFormat.format(System.currentTimeMillis())
                path = "$mCameraDir/VID_JJCamera_$date"
            }
            mOriginalPath = path
            path = "${path}.mp4"
            mMediaMuxer = MediaMuxer(path!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            mCaptureCallBack?.onError(e.localizedMessage)
            Logger.e(TAG, "init media muxer failed, err = ${e.localizedMessage}", e)
        }
    }

    /**
     * Add tracker
     *
     * @param mediaFormat media format, see [MediaFormat]
     * @param isVideo media type, audio or video
     */
    @Synchronized
    fun addTracker(mediaFormat: MediaFormat?, isVideo: Boolean) {
        if (isMuxerStarter() || mediaFormat == null) {
            return
        }
        try {
            mMediaMuxer?.apply {
                val tracker = addTrack(mediaFormat)
                if (Utils.debugCamera) {
                    Logger.i(TAG, "addTracker index = $tracker isVideo = $isVideo")
                }
                if (isVideo) {
                    mVideoFormat = mediaFormat
                    mVideoTrackerIndex = tracker
                    if (mAudioTrackerIndex != -1 || isVideoOnly) {
                        start()
                        mMainHandler.post {
                            mCaptureCallBack?.onBegin()
                        }
                        mBeginMillis = System.currentTimeMillis()
                        if (Utils.debugCamera) {
                            Logger.i(TAG, "start media muxer")
                        }
                    }
                } else {
                    mAudioFormat = mediaFormat
                    mAudioTrackerIndex = tracker
                    if (mVideoTrackerIndex != -1) {
                        start()
                        mMainHandler.post {
                            mCaptureCallBack?.onBegin()
                        }
                        mBeginMillis = System.currentTimeMillis()
                        if (Utils.debugCamera) {
                            Logger.i(TAG, "start media muxer")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            release()
            mMainHandler.post {
                mCaptureCallBack?.onError(e.localizedMessage)
            }
            Logger.e(TAG, "addTracker failed, err = ${e.localizedMessage}", e)
        }
    }

    /**
     * 将音频（AAC）或视频（H.264）数据写入 MediaMuxer
     *
     * @param outputBuffer encode output buffer, see [MediaCodec]
     * @param bufferInfo encode output buffer info, see [MediaCodec.BufferInfo]
     * @param isVideo media data type, audio or video
     */
    @Synchronized
    fun pumpStream(outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean) {
        try {
            //检查 MediaMuxer 是否已启动
            if (!isMuxerStarter()) {
                return
            }
            if (bufferInfo.size <= 0) {
                return
            }

            val index = if (isVideo) {
                // 记录视频的起始时间戳
                if (mVideoPts == 0L) {
                    //
                    mVideoPts = bufferInfo.presentationTimeUs
                }
                // 更新 bufferInfo.presentationTimeUs
                bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - mVideoPts
                mVideoTrackerIndex
            } else {
                if (mAudioPts == 0L) {
                    mAudioPts = bufferInfo.presentationTimeUs
                }
                bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - mAudioPts
                mAudioTrackerIndex
            }
            // 设置 outputBuffer 的起始位置（bufferInfo.offset）
            outputBuffer.position(bufferInfo.offset)
            // 限制位置（bufferInfo.offset + bufferInfo.size），确保只处理有效的数据部分。
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            // 写入 MediaMuxer
            mMediaMuxer?.writeSampleData(index, outputBuffer, bufferInfo)
            // 是否需要保存新文件
            saveNewFileIfNeed()
        } catch (e: Exception) {
            Logger.e(TAG, "pumpStream failed, err = ${e.localizedMessage}", e)
        }
    }

    private fun saveNewFileIfNeed() {
        try {
            //
            val endMillis = System.currentTimeMillis()
            if (durationInSec == 0L) {
                return
            }
            //小于限定时长
            if (endMillis - mBeginMillis <= durationInSec * 1000) {
                return
            }

            mMediaMuxer?.stop()
            mMediaMuxer?.release()
            mMediaMuxer = null
            mAudioTrackerIndex = -1
            mVideoTrackerIndex = -1
            mAudioPts = 0L
            mVideoPts = 0L
            insertDCIM(mContext, path)

            // 创建新的 MediaMuxer 实例
            path = "${mOriginalPath}_${++mFileSubIndex}.mp4"
            // 指定输出格式为MP4
            mMediaMuxer = MediaMuxer(path!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // 添加音频 和 视频轨道
            addTracker(mVideoFormat, true)
            addTracker(mAudioFormat, false)
        } catch (e: Exception) {
            mMainHandler.post {
                mCaptureCallBack?.onError(e.localizedMessage)
            }
            Logger.e(TAG, "release media muxer failed, err = ${e.localizedMessage}", e)
        }
    }

    /**
     * Release mp4 muxer resource
     */
    @Synchronized
    fun release() {
        try {
            mMediaMuxer?.stop()
            mMediaMuxer?.release()
            insertDCIM(mContext, path, true)
            Logger.i(TAG, "stop media muxer")
        } catch (e: Exception) {
            mMainHandler.post {
                mCaptureCallBack?.onError(e.localizedMessage)
            }
            Logger.e(TAG, "release media muxer failed, err = ${e.localizedMessage}", e)
        } finally {
            mMediaMuxer = null
            mAudioTrackerIndex = -1
            mVideoTrackerIndex = -1
            mAudioPts = 0L
            mVideoPts = 0L
        }
    }

    fun getSavePath() = path

    private fun insertDCIM(context: Context?, videoPath: String?, notifyOut: Boolean = false) {
        context?.let { ctx ->
            if (videoPath.isNullOrEmpty()) {
                return
            }
            ctx.contentResolver.let { content ->
                val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                content.insert(uri, getVideoContentValues(videoPath))
                mMainHandler.post {
                    mCaptureCallBack?.onComplete(this.path)
                }
            }
        }
    }

    private fun getVideoContentValues(path: String): ContentValues {
        val file = File(path)
        val values = ContentValues()
        values.put(MediaStore.Video.Media.DATA, path)
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.SIZE, file.length())
        values.put(MediaStore.Video.Media.DURATION, getLocalVideoDuration(file.path))
        if (MediaUtils.isAboveQ()) {
            val relativePath = "${Environment.DIRECTORY_DCIM}${File.separator}Camera"
            val dateExpires = (System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS) / 1000
            values.put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            values.put(MediaStore.Video.Media.DATE_EXPIRES, dateExpires)
        }
        return values
    }


    fun isMuxerStarter() = mVideoTrackerIndex != -1 && (mAudioTrackerIndex != -1 || isVideoOnly)

    /**
     * 使用 MediaMetadataRetriever 获取Video 时长
     */
    private fun getLocalVideoDuration(filePath: String?): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(filePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    companion object {
        private const val TAG = "Mp4Muxer"
    }
}