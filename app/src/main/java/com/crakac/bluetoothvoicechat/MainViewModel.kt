package com.crakac.bluetoothvoicechat

import android.view.View
import androidx.lifecycle.*
import kotlinx.coroutines.launch

class MainViewModel(val deviceName: String, val macAddress: String) : ViewModel(),
    BluetoothVoiceChatService.ConnectionListener {
    val TAG: String = "MainViewModel"

    class Factory(private val deviceName: String, private val address: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return MainViewModel(deviceName, address) as T
        }
    }

    private val chatService = BluetoothVoiceChatService()

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

    init {
        chatService.setConnectionListener(this)
    }

    fun start() {
        chatService.start()
        chatService.connect(macAddress)
    }

    override fun onCleared() {
        chatService.stop()
        chatService.setConnectionListener(null)
    }

    override fun onConnectionFailed() {
        _spinner.postValue(true)
        _buttonEnabled.postValue(false)
//        _state.postValue("Connection failed")
    }

    override fun onConnected() {
        _spinner.postValue(false)
        _buttonEnabled.postValue(true)
        _state.postValue("Connected")
    }

    override fun onDisconnected() {
        _spinner.postValue(true)
        _buttonEnabled.postValue(false)
        _state.postValue("Disconnected")
    }

    override fun onLostConnection() {
        _spinner.postValue(true)
        _buttonEnabled.postValue(false)
        _state.postValue("Connection Lost")
    }

    override fun onMessage(bytes: Int, buffer: ByteArray) {
        _message.postValue(String(buffer, 0, bytes))
    }

    fun sendMessage(message: String) = viewModelScope.launch{
        val bytes = message.toByteArray()
        chatService.write(bytes)
    }
}