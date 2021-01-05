package com.crakac.bluetoothvoicechat

import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface AudioRecordServiceListener {
    fun onAudioRead(buffer: ByteArray, bytes: Int)
}

class AudioRecordService {
    val TAG: String = "AudioRecordService"
    private val scope = CoroutineScope(Job())
    private val samplingRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val buffer = ShortArray(bufferSize)

    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    private val trackBufferSize = AudioTrack.getMinBufferSize(
        samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    private val attr = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .build()
    private val format = AudioFormat.Builder()
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setSampleRate(samplingRate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()
    private val audioTrack = AudioTrack(
        attr,
        format,
        trackBufferSize,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE
    )

    private val echoCanceler = if (AcousticEchoCanceler.isAvailable())
        AcousticEchoCanceler.create(audioRecord.audioSessionId) else null

    private var job: Job? = null
    private var listener: AudioRecordServiceListener? = null

    fun start() {
        echoCanceler?.enabled = true
        audioRecord.startRecording()
        audioTrack.play()
        job = scope.launch {
            while (isActive) {
                val shorts = audioRecord.read(buffer, 0, bufferSize)
                if (shorts < 0) {
                    val msg = when (shorts) {
                        AudioRecord.ERROR_INVALID_OPERATION -> "Invalid Operation"
                        AudioRecord.ERROR_BAD_VALUE -> "Bad Value"
                        AudioRecord.ERROR_DEAD_OBJECT -> "Dead Object"
                        else -> "Unknown Error"
                    }
                    Log.e(TAG, msg)
                }
                val byteArray = buffer.toByteArray(shorts)
                listener?.onAudioRead(byteArray, byteArray.size)
            }
        }
    }

    fun stop() {
        job?.cancel()
        audioRecord.stop()
        audioTrack.stop()
    }

    fun play(data: ByteArray) {
        val byteArray = data.toShortArray()
        audioTrack.write(byteArray, 0, byteArray.size)
    }

    fun setListener(listener: AudioRecordServiceListener?) {
        this.listener = listener
    }
}