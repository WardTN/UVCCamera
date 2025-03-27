/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCPreview.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#include <stdlib.h>
#include <linux/time.h>
#include <unistd.h>

#if 1	// set 1 if you don't need debug log
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// w/o LOGV/LOGD/MARK
	#endif
	#undef USE_LOGALL
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
//	#undef NDEBUG
#endif

#include "utilbase.h"
#include "UVCPreview.h"
#include "libuvc_internal.h"

#define	LOCAL_DEBUG 0
#define MAX_FRAME 4
#define PREVIEW_PIXEL_BYTES 4	// RGBA/RGBX
#define FRAME_POOL_SZ MAX_FRAME + 2

struct timespec ts;
struct timeval tv;

UVCPreview::UVCPreview(uvc_device_handle_t *devh)
:	mPreviewWindow(NULL),
	mCaptureWindow(NULL),
	mDeviceHandle(devh),
	requestWidth(DEFAULT_PREVIEW_WIDTH),
	requestHeight(DEFAULT_PREVIEW_HEIGHT),
	requestMinFps(DEFAULT_PREVIEW_FPS_MIN),
	requestMaxFps(DEFAULT_PREVIEW_FPS_MAX),
	requestMode(DEFAULT_PREVIEW_MODE),
	requestBandwidth(DEFAULT_BANDWIDTH),
	frameWidth(DEFAULT_PREVIEW_WIDTH),
	frameHeight(DEFAULT_PREVIEW_HEIGHT),
	frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2),	// YUYV
	frameMode(0),
	previewBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * PREVIEW_PIXEL_BYTES),
	previewFormat(WINDOW_FORMAT_RGBA_8888),
	mIsRunning(false),
	mIsCapturing(false),
	captureQueu(NULL),
	mFrameCallbackObj(NULL),
	mFrameCallbackFunc(NULL),
	callbackPixelBytes(2) {

	ENTER();
	pthread_cond_init(&preview_sync, NULL);
	pthread_mutex_init(&preview_mutex, NULL);
    // 初始化并关联 capture_clock_attr
    //pthread_condattr_init(&capture_clock_attr);
    //pthread_condattr_setclock(&capture_clock_attr, CLOCK_MONOTONIC);
	pthread_cond_init(&capture_sync, NULL);
	pthread_mutex_init(&capture_mutex, NULL);

	pthread_mutex_init(&pool_mutex, NULL);
	EXIT();
}

UVCPreview::~UVCPreview() {

	ENTER();
	if (mPreviewWindow)
		ANativeWindow_release(mPreviewWindow);
	mPreviewWindow = NULL;
	if (mCaptureWindow)
		ANativeWindow_release(mCaptureWindow);
	mCaptureWindow = NULL;
	clearPreviewFrame();
	clearCaptureFrame();
	clear_pool();
	pthread_mutex_lock(&preview_mutex);
	pthread_mutex_destroy(&preview_mutex);
	pthread_cond_destroy(&preview_sync);
	pthread_mutex_lock(&capture_mutex);
	pthread_mutex_destroy(&capture_mutex);
	pthread_cond_destroy(&capture_sync);
	// 释放 capture_clock_aatr
    // pthread_condattr_destroy(&capture_clock_attr);
	pthread_mutex_destroy(&pool_mutex);
	EXIT();
}

/**
 * get uvc_frame_t from frame pool
 * if pool is empty, create new frame
 * this function does not confirm the frame size
 * and you may need to confirm the size
 * 从帧池中获取 uvc_frame_t
 */
uvc_frame_t *UVCPreview::get_frame(size_t data_bytes) {
	uvc_frame_t *frame = NULL;
    // 获取最新一帧
    pthread_mutex_lock(&pool_mutex);
	{
		if (!mFramePool.isEmpty()) {
			frame = mFramePool.last();
		}
	}
	pthread_mutex_unlock(&pool_mutex);
    // 不符合一帧
	if UNLIKELY(!frame) {
		LOGW("allocate new frame");
		frame = uvc_allocate_frame(data_bytes);
	}
	return frame;
}


/**
 *
 * @param frame
 */
void UVCPreview::recycle_frame(uvc_frame_t *frame) {
	pthread_mutex_lock(&pool_mutex);
	if (LIKELY(mFramePool.size() < FRAME_POOL_SZ)) {
        // 如果帧池没有满，将当前帧放入帧池中进行重用
		mFramePool.put(frame);
        // 将指针置为 NULL，表示当前帧已经不再被当前函数使用
		frame = NULL;
	}
	pthread_mutex_unlock(&pool_mutex);
    // 当前帧不符合一帧标准 释放帧的内存，避免内存泄漏
	if (UNLIKELY(frame)) {
		uvc_free_frame(frame);
	}
}


void UVCPreview::init_pool(size_t data_bytes) {
	ENTER();

	clear_pool();
	pthread_mutex_lock(&pool_mutex);
	{
		for (int i = 0; i < FRAME_POOL_SZ; i++) {
			mFramePool.put(uvc_allocate_frame(data_bytes));
		}
	}
	pthread_mutex_unlock(&pool_mutex);

	EXIT();
}

void UVCPreview::clear_pool() {
	ENTER();

	pthread_mutex_lock(&pool_mutex);
	{
		const int n = mFramePool.size();
		for (int i = 0; i < n; i++) {
			uvc_free_frame(mFramePool[i]);
		}
		mFramePool.clear();
	}
	pthread_mutex_unlock(&pool_mutex);
	EXIT();
}

inline const bool UVCPreview::isRunning() const {return mIsRunning; }

/**
 * 设置 预览尺寸配置
 * @param width
 * @param height
 * @param min_fps
 * @param max_fps
 * @param mode
 * @param bandwidth
 * @return
 */
int UVCPreview::setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth) {
	ENTER();
	
	int result = 0;
	if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {
		requestWidth = width;
		requestHeight = height;
		requestMinFps = min_fps;
		requestMaxFps = max_fps;
		requestMode = mode;
		requestBandwidth = bandwidth;

		uvc_stream_ctrl_t ctrl;
		result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, &ctrl,
			!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
			requestWidth, requestHeight, requestMinFps, requestMaxFps);
	}
	
	RETURN(result, int);
}

/**
 * 设置预览窗口
 * @param preview_window
 * @return
 */
int UVCPreview::setPreviewDisplay(ANativeWindow *preview_window) {
	ENTER();
	pthread_mutex_lock(&preview_mutex);
	{
        // 检查当前的预览窗口是否与传入的不同
		if (mPreviewWindow != preview_window) {
            // 如果当前已经有一个预览窗口，释放它的资源
            if (mPreviewWindow)
				ANativeWindow_release(mPreviewWindow);
            // 设置新的预览窗口
			mPreviewWindow = preview_window;

            // 如果新的预览窗口不为空
			if (LIKELY(mPreviewWindow)) {
                // 设置缓冲区的几何属性（宽、高、格式）
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					frameWidth, frameHeight, previewFormat);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}

/**
 * 设置帧回调 并捕获视频帧将其传递给 Java 层 的回调方法
 * @param env
 * @param frame_callback_obj
 * @param pixel_format
 * @return
 */
int UVCPreview::setFrameCallback(JNIEnv *env, jobject frame_callback_obj, int pixel_format) {
	
	ENTER();
	pthread_mutex_lock(&capture_mutex);
	{
        // 如果正在运行并且捕获中，停止捕获
		if (isRunning() && isCapturing()) {
			mIsCapturing = false;
            // 如果已有帧回调对象，唤醒并等待捕获操作结束
			if (mFrameCallbackObj) {
				pthread_cond_signal(&capture_sync);      // 通知捕获停止
				pthread_cond_wait(&capture_sync, &capture_mutex);	// wait finishing capturing  等待捕获结束
			}
		}

        // 检查传入的回调对象是否与当前帧回调对象相同
		if (!env->IsSameObject(mFrameCallbackObj, frame_callback_obj))	{
            // 重置帧回调方法
			iframecallback_fields.onFrame = NULL;

            // 如果已有回调对象，删除其全局引用
			if (mFrameCallbackObj) {
				env->DeleteGlobalRef(mFrameCallbackObj);
			}
            // 更新帧回调对象
            mFrameCallbackObj = frame_callback_obj;

			if (frame_callback_obj) {
				// get method IDs of Java object for callback
                // 获取Java对象的类，以便查找回调方法
				jclass clazz = env->GetObjectClass(frame_callback_obj);
				if (LIKELY(clazz)) {
                    // 查找Java中的 `onFrame` 方法，签名为接受 `ByteBuffer` 参数
					iframecallback_fields.onFrame = env->GetMethodID(clazz,
						"onFrame",	"(Ljava/nio/ByteBuffer;)V");
				} else {
					LOGW("failed to get object class");
				}
				env->ExceptionClear();
				if (!iframecallback_fields.onFrame) {
					LOGE("Can't find IFrameCallback#onFrame");
					env->DeleteGlobalRef(frame_callback_obj);
					mFrameCallbackObj = frame_callback_obj = NULL;
				}
			}
		}
        // 如果帧回调对象有效，更新像素格式并触发格式更改回调
		if (frame_callback_obj) {
			mPixelFormat = pixel_format;
			callbackPixelFormatChanged(); // 通知像素格式发生改变
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	RETURN(0, int);
}

void UVCPreview::callbackPixelFormatChanged() {
	mFrameCallbackFunc = NULL;
    // 分辨率
	const size_t sz = requestWidth * requestHeight;
	switch (mPixelFormat) {
	  case PIXEL_FORMAT_RAW:
		LOGI("PIXEL_FORMAT_RAW:");
		callbackPixelBytes = sz * 2;
		break;
	  case PIXEL_FORMAT_YUV:
		LOGI("PIXEL_FORMAT_YUV:");
		callbackPixelBytes = sz * 2;
		break;
	  case PIXEL_FORMAT_RGB565:
		LOGI("PIXEL_FORMAT_RGB565:");
		mFrameCallbackFunc = uvc_any2rgb565;
		callbackPixelBytes = sz * 2;
		break;
	  case PIXEL_FORMAT_RGBX:
		LOGI("PIXEL_FORMAT_RGBX:");
		mFrameCallbackFunc = uvc_any2rgbx;
		callbackPixelBytes = sz * 4;
		break;
	  case PIXEL_FORMAT_YUV20SP:
		LOGI("PIXEL_FORMAT_YUV20SP:");
		mFrameCallbackFunc = uvc_yuyv2iyuv420SP;
		callbackPixelBytes = (sz * 3) / 2;
		break;
	  case PIXEL_FORMAT_NV21:
		LOGI("PIXEL_FORMAT_NV21:");
		mFrameCallbackFunc = uvc_yuyv2yuv420SP;
		callbackPixelBytes = (sz * 3) / 2;
		break;
	}
}

void UVCPreview::clearDisplay() {
	ENTER();

	ANativeWindow_Buffer buffer;
	pthread_mutex_lock(&capture_mutex);
	{
		if (LIKELY(mCaptureWindow)) {
			if (LIKELY(ANativeWindow_lock(mCaptureWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * PREVIEW_PIXEL_BYTES;
				const int stride = buffer.stride * PREVIEW_PIXEL_BYTES;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mCaptureWindow);
			}
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	pthread_mutex_lock(&preview_mutex);
	{
		if (LIKELY(mPreviewWindow)) {
			if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * PREVIEW_PIXEL_BYTES;
				const int stride = buffer.stride * PREVIEW_PIXEL_BYTES;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mPreviewWindow);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);

	EXIT();
}

/**
 * 启动摄像头预览
 * @return
 */
int UVCPreview::startPreview() {
	ENTER();

	int result = EXIT_FAILURE;
	// 检查预览线程是否未运行
	if (!isRunning()) {
		mIsRunning = true;  // 标记预览正在运行

		// 锁定预览的互斥锁，确保线程安全
		pthread_mutex_lock(&preview_mutex);
		{
			// 如果预览窗口存在，尝试创建预览线程
			if (LIKELY(mPreviewWindow)) {
				// 创建线程，运行 `preview_thread_func` 函数，传递 `this` 作为参数
				result = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);
			}
		}
		// 如果线程创建失败
		pthread_mutex_unlock(&preview_mutex);
		if (UNLIKELY(result != EXIT_SUCCESS)) {
			LOGW("UVCCamera::window does not exist/already running/could not create thread etc.");
			mIsRunning = false;
			// 锁定互斥锁，并发出同步信号
			pthread_mutex_lock(&preview_mutex);
			{
				pthread_cond_signal(&preview_sync);// 发出条件信号，通知可能等待的线程
			}
			pthread_mutex_unlock(&preview_mutex); // 解锁互斥锁
		}
	}
	RETURN(result, int);
}

int UVCPreview::stopPreview() {
	ENTER();
	bool b = isRunning();
	if (LIKELY(b)) {
		mIsRunning = false;
        pthread_cond_signal(&preview_sync);
        // jiangdg:fix stopview crash
        // because of capture_thread may null when called do_preview()
		if (mHasCapturing) {
            pthread_cond_signal(&capture_sync);
            if (capture_thread && pthread_join(capture_thread, NULL) != EXIT_SUCCESS) {
                LOGW("UVCPreview::terminate capture thread: pthread_join failed");
            }
		}
		if (preview_thread && pthread_join(preview_thread, NULL) != EXIT_SUCCESS) {
			LOGW("UVCPreview::terminate preview thread: pthread_join failed");
		}
		clearDisplay();
	}
	mHasCapturing = false;
	clearPreviewFrame();
	clearCaptureFrame();
	// check preview mutex available
	if (pthread_mutex_lock(&preview_mutex) == 0) {
		if (mPreviewWindow) {
			ANativeWindow_release(mPreviewWindow);
			mPreviewWindow = NULL;
		}
		pthread_mutex_unlock(&preview_mutex);
	}
	if (pthread_mutex_lock(&capture_mutex) == 0) {
		if (mCaptureWindow) {
			ANativeWindow_release(mCaptureWindow);
			mCaptureWindow = NULL;
		}
		pthread_mutex_unlock(&capture_mutex);
	}
	RETURN(0, int);
}

/**
 * 用于处理来自摄像头的 UVC（USB Video Class）帧数据
 * @param frame
 * @param vptr_args
 */
void UVCPreview::uvc_preview_frame_callback(uvc_frame_t *frame, void *vptr_args) {
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	// preview->isRunning()
	// !frame || !frame->frame_format || !frame->data || !frame->data_bytes  验证帧的格式、数据指针以及数据大小是否有效
	if UNLIKELY(!preview->isRunning() || !frame || !frame->frame_format || !frame->data || !frame->data_bytes) return;
	if (UNLIKELY(
		((frame->frame_format != UVC_FRAME_FORMAT_MJPEG) && (frame->actual_bytes < preview->frameBytes))
		|| (frame->width != preview->frameWidth) || (frame->height != preview->frameHeight) )) {

#if LOCAL_DEBUG
		LOGD("broken frame!:format=%d,actual_bytes=%d/%d(%d,%d/%d,%d)",
			frame->frame_format, frame->actual_bytes, preview->frameBytes,
			frame->width, frame->height, preview->frameWidth, preview->frameHeight);
#endif
		return;
	}
	// 如果帧通过了验证，函数会从 preview 获取一个空的帧缓冲区来复制该帧的数据：
	if (LIKELY(preview->isRunning())) {
		uvc_frame_t *copy = preview->get_frame(frame->data_bytes);
		if (UNLIKELY(!copy)) {
#if LOCAL_DEBUG
			LOGE("uvc_callback:unable to allocate duplicate frame!");
#endif
			return;
		}
		//原始帧数据复制到新分配的 copy 缓冲区中。如果复制过程中出错，回收该帧缓冲区并返回。
		uvc_error_t ret = uvc_duplicate_frame(frame, copy);
		if (UNLIKELY(ret)) {
			preview->recycle_frame(copy);
			return;
		}
        // 添加帧到预览队列
		preview->addPreviewFrame(copy);
	}
}

/**
 * 添加帧到预览队列
 * @param frame
 */
void UVCPreview::addPreviewFrame(uvc_frame_t *frame) {

	pthread_mutex_lock(&preview_mutex);
	if (isRunning() && (previewFrames.size() < MAX_FRAME)) {
		previewFrames.put(frame);
		frame = NULL;
        // 唤醒等待帧的其他线程，通知它们队列中有新帧可以处理。
		pthread_cond_signal(&preview_sync);
	}
	pthread_mutex_unlock(&preview_mutex);
    // 如果 frame 仍然非空，说明该帧未能成功加入队列（比如预览已经停止或队列已满），则调用 recycle_frame(frame) 回收该帧
	if (frame) {
		recycle_frame(frame);
	}
}

uvc_frame_t *UVCPreview::waitPreviewFrame() {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&preview_mutex);
	{
		if (!previewFrames.size()) {
			pthread_cond_wait(&preview_sync, &preview_mutex);
		}
		if (LIKELY(isRunning() && previewFrames.size() > 0)) {
			frame = previewFrames.remove(0);
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	return frame;
}

void UVCPreview::clearPreviewFrame() {
	pthread_mutex_lock(&preview_mutex);
	{
		for (int i = 0; i < previewFrames.size(); i++)
			recycle_frame(previewFrames[i]);
		previewFrames.clear();
	}
	pthread_mutex_unlock(&preview_mutex);
}

void *UVCPreview::preview_thread_func(void *vptr_args) {
	int result;

	ENTER();
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if (LIKELY(preview)) {
		uvc_stream_ctrl_t ctrl;
		result = preview->prepare_preview(&ctrl);
		if (LIKELY(!result)) {
			preview->do_preview(&ctrl);
		}
	}
	PRE_EXIT();
	pthread_exit(NULL);
}
/**
 * UVC 相机的预览配置进行准备工作
 * @param ctrl
 * @return
 */
int UVCPreview::prepare_preview(uvc_stream_ctrl_t *ctrl) {
	uvc_error_t result;

	ENTER();
    // 获取流控制配置
	result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, ctrl,
		!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
		requestWidth, requestHeight, requestMinFps, requestMaxFps
	);
	if (LIKELY(!result)) {
#if LOCAL_DEBUG
		uvc_print_stream_ctrl(ctrl, stderr);
#endif
		uvc_frame_desc_t *frame_desc;
        // 获取帧描述符 帧描述符包含了视频帧的详细信息

		result = uvc_get_frame_desc(mDeviceHandle, ctrl, &frame_desc);

        if (LIKELY(!result)) {
			frameWidth = frame_desc->wWidth;
			frameHeight = frame_desc->wHeight;
			LOGI("frameSize=(%d,%d)@%s", frameWidth, frameHeight, (!requestMode ? "YUYV" : "MJPEG"));
			pthread_mutex_lock(&preview_mutex);
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					frameWidth, frameHeight, previewFormat);
			}
			pthread_mutex_unlock(&preview_mutex);
		} else {
			frameWidth = requestWidth;
			frameHeight = requestHeight;
		}
		frameMode = requestMode;
		frameBytes = frameWidth * frameHeight * (!requestMode ? 2 : 4);
		previewBytes = frameWidth * frameHeight * PREVIEW_PIXEL_BYTES;
	} else {
		LOGE("could not negotiate with camera:err=%d", result);
	}
	RETURN(result, int);
}

/**
 * 函数负责处理 UVC 设备的预览流，包括启动流媒体、处理帧、执行 MJPEG 到 YUYV 的格式转换（如果必要），并在预览结束时停止流媒体。
 * @param ctrl
 */
void UVCPreview::do_preview(uvc_stream_ctrl_t *ctrl) {
	ENTER();

	uvc_frame_t *frame = NULL;
	uvc_frame_t *frame_mjpeg = NULL;

    // 启动 UVC 流媒体
	uvc_error_t result = uvc_start_streaming_bandwidth(
		mDeviceHandle, ctrl, uvc_preview_frame_callback, (void *)this, requestBandwidth, 0);

    // jiangdg:fix stopview crash
    // use mHasCapturing flag confirm capture_thread was be created
    mHasCapturing = false;
	if (LIKELY(!result)) {
		clearPreviewFrame();
        // 如果流媒体成功启动，函数会尝试创建捕获线程 capture_thread 来处理摄像头的预览帧：
		if (pthread_create(&capture_thread, NULL, capture_thread_func, (void *)this) == 0) {
		    mHasCapturing = true;
		}

#if LOCAL_DEBUG
		LOGI("Streaming...");
#endif
        // 根据预览模式（MJPEG 或 YUYV），有两个不同的帧处理循环
		if (frameMode) {
			// MJPEG mode
			for ( ; LIKELY(isRunning()) ; ) {
                // 通过 waitPreviewFrame() 函数等待新的 MJPEG 帧。
				frame_mjpeg = waitPreviewFrame();
				if (LIKELY(frame_mjpeg)) {
					frame = get_frame(frame_mjpeg->width * frame_mjpeg->height * 2);
                    // 调用 uvc_mjpeg2yuyv 将 MJPEG 格式的帧转换为 YUYV 格式
					result = uvc_mjpeg2yuyv(frame_mjpeg, frame);   // MJPEG => yuyv
					recycle_frame(frame_mjpeg);
					if (LIKELY(!result)) {
                        // 将处理后的帧交给 draw_preview_one 函数绘制到窗口，并将其转换为 RGBX 格式。
						frame = draw_preview_one(frame, &mPreviewWindow, uvc_any2rgbx, 4);
                        // 调用 addCaptureFrame(frame) 将处理后的帧添加到捕获队列中
						addCaptureFrame(frame);
					} else {
						recycle_frame(frame);
					}
				}
			}
		} else {
			// yuvyv mode
			for ( ; LIKELY(isRunning()) ; ) {
				frame = waitPreviewFrame();
				if (LIKELY(frame)) {
					frame = draw_preview_one(frame, &mPreviewWindow, uvc_any2rgbx, 4);
					addCaptureFrame(frame);
				}
			}
		}

        // 当预览停止时，唤醒捕获线程：
		pthread_cond_signal(&capture_sync);
#if LOCAL_DEBUG
		LOGI("preview_thread_func:wait for all callbacks complete");
#endif
        // 调用 uvc_stop_streaming 停止摄像头流媒体
		uvc_stop_streaming(mDeviceHandle);
#if LOCAL_DEBUG
		LOGI("Streaming finished");
#endif
	} else {
		uvc_perror(result, "failed start_streaming");
	}

	EXIT();
}

static void copyFrame(const uint8_t *src, uint8_t *dest, const int width, int height, const int stride_src, const int stride_dest) {
	const int h8 = height % 8;
	for (int i = 0; i < h8; i++) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
	for (int i = 0; i < height; i += 8) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
}


// transfer specific frame data to the Surface(ANativeWindow)
int copyToSurface(uvc_frame_t *frame, ANativeWindow **window) {
	// ENTER();
	int result = 0;
	if (LIKELY(*window)) {
		ANativeWindow_Buffer buffer;
		if (LIKELY(ANativeWindow_lock(*window, &buffer, NULL) == 0)) {
			// source = frame data
			const uint8_t *src = (uint8_t *)frame->data;
			const int src_w = frame->width * PREVIEW_PIXEL_BYTES;
			const int src_step = frame->width * PREVIEW_PIXEL_BYTES;
			// destination = Surface(ANativeWindow)
			uint8_t *dest = (uint8_t *)buffer.bits;
			const int dest_w = buffer.width * PREVIEW_PIXEL_BYTES;
			const int dest_step = buffer.stride * PREVIEW_PIXEL_BYTES;
			// use lower transfer bytes
			const int w = src_w < dest_w ? src_w : dest_w;
			// use lower height
			const int h = frame->height < buffer.height ? frame->height : buffer.height;
			// transfer from frame data to the Surface
			copyFrame(src, dest, w, h, src_step, dest_step);
			ANativeWindow_unlockAndPost(*window);
		} else {
			result = -1;
		}
	} else {
		result = -1;
	}
	return result; //RETURN(result, int);
}

// changed to return original frame instead of returning converted frame even if convert_func is not null.
uvc_frame_t *UVCPreview::draw_preview_one(uvc_frame_t *frame, ANativeWindow **window, convFunc_t convert_func, int pixcelBytes) {
	// ENTER();

	int b = 0;
	pthread_mutex_lock(&preview_mutex);
	{
		b = *window != NULL;
	}
	pthread_mutex_unlock(&preview_mutex);
	if (LIKELY(b)) {
		uvc_frame_t *converted;
		if (convert_func) {
			converted = get_frame(frame->width * frame->height * pixcelBytes);
			if LIKELY(converted) {
				b = convert_func(frame, converted);
				if (!b) {
					pthread_mutex_lock(&preview_mutex);
					copyToSurface(converted, window);
					pthread_mutex_unlock(&preview_mutex);
				} else {
					LOGE("failed converting");
				}
				recycle_frame(converted);
			}
		} else {
			pthread_mutex_lock(&preview_mutex);
			copyToSurface(frame, window);
			pthread_mutex_unlock(&preview_mutex);
		}
	}
	return frame; //RETURN(frame, uvc_frame_t *);
}

//======================================================================
//
//======================================================================
inline const bool UVCPreview::isCapturing() const { return mIsCapturing; }

int UVCPreview::setCaptureDisplay(ANativeWindow *capture_window) {
	ENTER();
	pthread_mutex_lock(&capture_mutex);
	{
		if (isRunning() && isCapturing()) {
			mIsCapturing = false;
			if (mCaptureWindow) {
				pthread_cond_signal(&capture_sync);
				pthread_cond_wait(&capture_sync, &capture_mutex);	// wait finishing capturing
			}
		}
		if (mCaptureWindow != capture_window) {
			// release current Surface if already assigned.
			if (UNLIKELY(mCaptureWindow))
				ANativeWindow_release(mCaptureWindow);
			mCaptureWindow = capture_window;
			// if you use Surface came from MediaCodec#createInputSurface
			// you could not change window format at least when you use
			// ANativeWindow_lock / ANativeWindow_unlockAndPost
			// to write frame data to the Surface...
			// So we need check here.
			if (mCaptureWindow) {
				int32_t window_format = ANativeWindow_getFormat(mCaptureWindow);
				if ((window_format != WINDOW_FORMAT_RGB_565)
					&& (previewFormat == WINDOW_FORMAT_RGB_565)) {
					LOGE("window format mismatch, cancelled movie capturing.");
					ANativeWindow_release(mCaptureWindow);
					mCaptureWindow = NULL;
				}
			}
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	RETURN(0, int);
}

void UVCPreview::addCaptureFrame(uvc_frame_t *frame) {
	pthread_mutex_lock(&capture_mutex);
	if (LIKELY(isRunning())) {
		// keep only latest one
		if (captureQueu) {
			recycle_frame(captureQueu);
		}
		captureQueu = frame;
		pthread_cond_broadcast(&capture_sync);
	} else {
		// Add this can solve native leak
		recycle_frame(frame);
	}
	pthread_mutex_unlock(&capture_mutex);
}

/**
 * get frame data for capturing, if not exist, block and wait
 */
uvc_frame_t *UVCPreview::waitCaptureFrame() {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&capture_mutex);
	{
		if (!captureQueu) {
			 //  这里有阻塞的情况，替换成 pthread_cond_timedwait 方法，设置相对的超时时间为 1s
             //pthread_cond_wait(&capture_sync, &capture_mutex);
            /*struct timespec tv;
            clock_gettime(CLOCK_MONOTONIC, &tv);
            tv.tv_sec += 1;
            pthread_cond_timedwait(&capture_sync, &capture_mutex,&tv);*/
                ts.tv_sec = 0;
                ts.tv_nsec = 0;

            #if _POSIX_TIMERS > 0
                      clock_gettime(CLOCK_REALTIME, &ts);
            #else
                      gettimeofday(&tv, NULL);
                      ts.tv_sec = tv.tv_sec;
                      ts.tv_nsec = tv.tv_usec * 1000;
            #endif
                      ts.tv_sec += 1;
                      ts.tv_nsec += 0;
            pthread_cond_timedwait(&capture_sync, &capture_mutex,&ts);
		}
		if (LIKELY(isRunning() && captureQueu)) {
			frame = captureQueu;
			captureQueu = NULL;
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	return frame;
}

/**
 * clear drame data for capturing
 */
void UVCPreview::clearCaptureFrame() {
	pthread_mutex_lock(&capture_mutex);
	{
		if (captureQueu)
			recycle_frame(captureQueu);
		captureQueu = NULL;
	}
	pthread_mutex_unlock(&capture_mutex);
}

//======================================================================
/*
 * thread function
 * @param vptr_args pointer to UVCPreview instance
 */
// static
void *UVCPreview::capture_thread_func(void *vptr_args) {
	int result;

	ENTER();
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if (LIKELY(preview)) {
		JavaVM *vm = getVM();
		JNIEnv *env;
		// attach to JavaVM
		vm->AttachCurrentThread(&env, NULL);
		preview->do_capture(env);	// never return until finish previewing
		// detach from JavaVM
		vm->DetachCurrentThread();
		MARK("DetachCurrentThread");
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

/**
 * the actual function for capturing
 */
void UVCPreview::do_capture(JNIEnv *env) {

	ENTER();

	clearCaptureFrame();
	callbackPixelFormatChanged();
	for (; isRunning() ;) {
		mIsCapturing = true;
		if (mCaptureWindow) {
			do_capture_surface(env);
		} else {
			do_capture_idle_loop(env);
		}
		pthread_cond_broadcast(&capture_sync);
	}	// end of for (; isRunning() ;)
	EXIT();
}

void UVCPreview::do_capture_idle_loop(JNIEnv *env) {
	ENTER();
	
	for (; isRunning() && isCapturing() ;) {
		do_capture_callback(env, waitCaptureFrame());
	}
	
	EXIT();
}

/**
 * write frame data to Surface for capturing
 * 处理捕获图像的渲染，将图像帧转换为 RGB 格式，并将其复制到捕获窗口表面（如 Android Surface）
 */
void UVCPreview::do_capture_surface(JNIEnv *env) {
	ENTER();

	uvc_frame_t *frame = NULL;
	uvc_frame_t *converted = NULL;
	char *local_picture_path;

    // 函数进入捕获循环，当预览和捕获都在运行时，持续等待并处理捕获的帧。
	for (; isRunning() && isCapturing() ;) {
        // waitCaptureFrame() 会等待并返回捕获的帧数据，这些帧始终是 YUYV 格式
		frame = waitCaptureFrame();
		if (LIKELY(frame)) {
			// frame data is always YUYV format.
			if LIKELY(isCapturing()) {
				if (UNLIKELY(!converted)) {
                    // 分配一个缓冲区 converted，用于存储转换后的 RGB 数据。
					converted = get_frame(previewBytes);
				}
				if (LIKELY(converted)) {
                    // 它将 YUYV 格式的帧转换为 RGBX 格式
					int b = uvc_any2rgbx(frame, converted);
					if (!b) {
						if (LIKELY(mCaptureWindow)) {
                            // 如果转换成功，并且捕获窗口存在，则调用 copyToSurface
                            // 将 RGB 数据复制到表面窗口（mCaptureWindow），以便显示或渲染
							copyToSurface(converted, &mCaptureWindow);
						}
					}
				}
			}
            // 无论是否进行帧转换，都会调用 do_capture_callback(env, frame) 来执行捕获的回调操作。
            // 这一步可能是将帧数据传递到其他模块进行进一步的处理，如保存图片或视频。
			do_capture_callback(env, frame);
		}
	}
	if (converted) {
		recycle_frame(converted);
	}
	if (mCaptureWindow) {
		ANativeWindow_release(mCaptureWindow);
		mCaptureWindow = NULL;
	}

	EXIT();
}

/**
    * call IFrameCallback#onFrame if needs
    * 帧回调 用于 保存图片或者视频
 */
void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
	ENTER();

	if (LIKELY(frame)) {
		uvc_frame_t *callback_frame = frame;
        // 如果回调对象 mFrameCallbackObj 存在，函数会通过 JNI 将处理后的帧数据传递给 Java 层
		if (mFrameCallbackObj) {
			if (mFrameCallbackFunc) {
                //为回调分配新的帧
				callback_frame = get_frame(callbackPixelBytes);
				if (LIKELY(callback_frame)) {
                    // 调用回调函数转换帧
					int b = mFrameCallbackFunc(frame, callback_frame);
                    // 回收帧
					recycle_frame(frame);
					if (UNLIKELY(b)) {
						LOGW("failed to convert for callback frame");
						goto SKIP;
					}
				} else {
					LOGW("failed to allocate for callback frame");
					callback_frame = frame;
					goto SKIP;
				}
			}
            // 将帧数据转换为 Java 中的 ByteBuffer 对象，允许直接访问底层的帧数据。
			jobject buf = env->NewDirectByteBuffer(callback_frame->data, callbackPixelBytes);

            if (iframecallback_fields.onFrame) {
				env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf);
			}
			env->ExceptionClear();
			env->DeleteLocalRef(buf);
		}
 SKIP:
		recycle_frame(callback_frame);
	}
	EXIT();
}
