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
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val buffer = ByteArray(bufferSize)

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
                val bytes = audioRecord.read(buffer, 0, bufferSize)
                listener?.onAudioRead(buffer.copyOf(bytes), bytes)
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