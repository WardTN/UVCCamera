
package com.wardtn.uvccamera.uvc;

import java.nio.ByteBuffer;

/**
 * UVCCamera 类的回调接口
 * 如果你需要帧数据作为 ByteBuffer，你可以使用这个回调接口与 UVCCamera#setFrameCallback 一起使用
 */
public interface IFrameCallback {
	/**
	 * 该方法通过 JNI 从本地库调用，并且在与 UVCCamera#startCapture 相同的线程上执行。
	 * 你可以同时使用 UVCCamera#startCapture 和 #setFrameCallback，
	 * 但为了更好的性能，最好只使用其中一个。
	 * 你也可以将像素格式类型传递给 UVCCamera#setFrameCallback 供该方法使用。
	 * 如果该方法执行时间较长，某些帧可能会丢失。
	 * 当你使用一些像 NV21 这样的颜色格式时，该库不会执行颜色空间转换，
	 * 只会执行像素格式转换。如果你想得到与屏幕显示相同的结果，请考虑
	 * 通过纹理 (SurfaceTexture) 获取图像，并使用 OpenGL|ES2/3 从中读取像素缓冲区，
	 * 而不是使用 IFrameCallback（大多数情况下，这种方法比使用 IFrameCallback 更高效）。
	 * @param frame 这是来自 JNI 层的直接 ByteBuffer，你需要处理它的字节顺序和限制。
	 */
	public void onFrame(ByteBuffer frame);
}
