
package com.wardtn.uvccamera.utils

import android.app.Application

/** Logger utils
 *
 *  Default log files dir:  /storage/emulated/0/Android/data/packagename/files
 *  or /data/data/packagename/files
 *
 * @author Created by jiangdg on 2022/1/24
 */
object Logger {
    fun init(application: Application, folderPath: String? = null) {
        XLogWrapper.init(application, folderPath)
    }

    fun i(flag: String, msg: String) {
        XLogWrapper.i(flag, msg)
    }

    fun d(flag: String, msg: String) {
        XLogWrapper.d(flag, msg)
    }

    fun w(flag: String, msg: String) {
        XLogWrapper.w(flag, msg)
    }

    fun w(flag: String, throwable: Throwable?) {
        XLogWrapper.w(flag, throwable)
    }

    fun w(flag: String, msg: String, throwable: Throwable?) {
        XLogWrapper.w(flag, msg, throwable)
    }

    fun e(flag: String, msg: String) {
        XLogWrapper.e(flag, msg)
    }

    fun e(flag: String, msg: String, throwable: Throwable?) {
        XLogWrapper.e(flag, msg, throwable)
    }
}