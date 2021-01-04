package com.crakac.bluetoothvoicechat

import android.media.*
import android.media.audiofx.AcousticEchoCanceler
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
        AudioFormat.ENCODING_PCM_8BIT
    )
    private val buffer = ByteArray(bufferSize)

    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_8BIT,
        bufferSize
    )

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_VOICE_CALL,
        samplingRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_8BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
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
                audioRecord.read(buffer, 0, bufferSize)
                listener?.onAudioRead(buffer, bufferSize)
            }
        }
    }

    fun stop() {
        job?.cancel()
        audioRecord.stop()
        audioTrack.stop()
    }

    fun play(data: ByteArray, bytes: Int) {
        audioTrack.write(data, 0, bytes)
    }

    fun setListener(listener: AudioRecordServiceListener?) {
        this.listener = listener
    }
}