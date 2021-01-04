package com.crakac.bluetoothvoicechat

import android.view.View
import androidx.lifecycle.*
import kotlinx.coroutines.launch

class MainViewModel(val deviceName: String, val macAddress: String) : ViewModel(),
    BluetoothVoiceChatService.ConnectionListener, AudioRecordServiceListener {
    val TAG: String = "MainViewModel"

    class Factory(private val deviceName: String, private val address: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MainViewModel(deviceName, address) as T
        }
    }

    private val chatService = BluetoothVoiceChatService()
    private val audioService = AudioRecordService()

    private val _message = MutableLiveData<String>()
    val message: LiveData<String>
        get() = _message

    private val _state = MutableLiveData<String?>()
    val state: LiveData<String?>
        get() = _state

    private val _spinner = MutableLiveData(true)
    val spinnerVisibility: LiveData<Int>
        get() = Transformations.map(_spinner) { if (it) View.VISIBLE else View.GONE }

    private val _buttonEnabled = MutableLiveData(false)
    val buttonEnabled: LiveData<Boolean>
        get() = _buttonEnabled

    private var _voiceEnabled = false
    var voiceEnabled: Boolean
        get() = _voiceEnabled
        set(enabled) {
            if (_voiceEnabled != enabled) {
                _voiceEnabled = enabled
            }
        }

    private var _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean>
    get() = _isConnected

    init {
        chatService.setConnectionListener(this)
        audioService.setListener(this)
    }

    fun start() {
        chatService.start()
        chatService.connect(macAddress)
    }

    override fun onCleared() {
        audioService.stop()
        chatService.stop()
        audioService.setListener(null)
        chatService.setConnectionListener(null)
    }

    override fun onConnected() {
        _isConnected.postValue(true)
        _spinner.postValue(false)
        _buttonEnabled.postValue(true)
        _state.postValue("Connected")
        audioService.start()
    }

    override fun onDisconnected() {
        _isConnected.postValue(false)
        _spinner.postValue(true)
        _buttonEnabled.postValue(false)
        _state.postValue("Disconnected")
        audioService.stop()
    }

    override fun onMessage(buffer: ByteArray, bytes: Int) {
        audioService.play(buffer, bytes)
    }

    override fun onAudioRead(buffer: ByteArray, bytes: Int) {
        if (isConnected.value!! && voiceEnabled) {
            chatService.write(buffer)
        }
    }

    fun sendMessage(message: String) = viewModelScope.launch {
        val bytes = message.toByteArray()
        chatService.write(bytes)
    }
}