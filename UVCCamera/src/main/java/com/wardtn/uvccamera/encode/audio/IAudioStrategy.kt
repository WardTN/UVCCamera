package com.wardtn.uvccamera.encode.audio
import com.wardtn.uvccamera.camera.bean.RawData


interface IAudioStrategy {
    fun initAudioRecord()
    fun startRecording()
    fun stopRecording()
    fun releaseAudioRecord()
    fun read(): RawData?
    fun isRecording(): Boolean
    fun getSampleRate(): Int
    fun getAudioFormat(): Int
    fun getChannelCount(): Int
    fun getChannelConfig(): Int
}