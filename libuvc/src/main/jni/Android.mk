#include $(call all-subdir-makefiles)
PROJ_PATH	:= $(call my-dir)
# 返回当前 Android.mk 文件所在的绝对路径。
# 包含不同模块的 Android.mk 文件
include $(CLEAR_VARS)
include $(PROJ_PATH)/UVCCamera/Android.mk
include $(PROJ_PATH)/libjpeg-turbo-1.5.0/Android.mk
include $(PROJ_PATH)/libusb/android/jni/Android.mk
include $(PROJ_PATH)/libuvc/android/jni/Android.mk