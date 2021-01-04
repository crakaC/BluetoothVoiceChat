package com.crakac.bluetoothvoicechat

import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.*

class AudioRecordService {
    val TAG: String = "AudioRecordService"
    private val scope = CoroutineScope(Job())
    private val samplingRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2
    private val buffer = ByteArray(bufferSize)

    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        samplingRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_VOICE_CALL,
        samplingRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    private val echoCanceler = if (AcousticEchoCanceler.isAvailable())
        AcousticEchoCanceler.create(audioRecord.audioSessionId) else null

    fun start() {

        echoCanceler?.enabled = true
        audioRecord.startRecording()
        audioTrack.play()
        scope.launch {
            withContext(Dispatchers.Default) {
                while (isActive) {
                    audioRecord.read(buffer, 0, bufferSize)
                    audioTrack.write(buffer, 0, bufferSize)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        audioRecord.stop()
        audioTrack.stop()
    }
}