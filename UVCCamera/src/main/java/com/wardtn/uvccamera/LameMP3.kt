package com.wardtn.uvccamera

object LameMP3 {
    init {
        System.loadLibrary("native-render")
    }

    external fun lameInit(
        inSampleRate: Int,
        outChannel: Int,
        outSampleRate: Int,
        outBitRate: Int,
        quality: Int,
    )

    external fun lameEncode(
        leftBuf: ShortArray?,
        rightBuf: ShortArray?,
        sampleRate: Int,
        mp3Buf: ByteArray?,
    ): Int

    external fun lameFlush(mp3buf: ByteArray?): Int

    external fun lameClose()


}