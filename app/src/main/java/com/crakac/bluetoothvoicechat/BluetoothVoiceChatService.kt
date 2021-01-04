package com.crakac.bluetoothvoicechat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val SERVER_NAME = "BluetoothVoiceChatServer"
private val ID = UUID.fromString("225b9254-9d8b-4b8b-84cc-4e37113cfd93")
private const val TAG = "BTVoiceChatService"

enum class ChatServiceState(value: Int) {
    NONE(0),
    LISTEN(1),
    CONNECTING(2),
    CONNECTED(3)
}

class BluetoothVoiceChatService {

    interface ConnectionListener {
        fun onConnected()
        fun onLostConnection()
        fun onDisconnected()
        fun onMessage(bytes: Int, buffer: ByteArray)
        fun onConnectionFailed()
    }

    private var scope = CoroutineScope(Job())
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var listener: ConnectionListener? = null

    @Volatile
    private var _state = ChatServiceState.NONE
    private var state: ChatServiceState
        get() = _state
        @Synchronized set(value) {
            _state = value
        }

    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var connectedJob: Job? = null

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun setConnectionListener(listener: ConnectionListener?) {
        this.listener = listener
    }

    @Synchronized
    fun start() {
        acceptJob?.cancel()
        connectJob?.cancel()
        connectedJob?.cancel()

        acceptJob = accept()
    }

    @Synchronized
    fun connect(address: String) {
        Log.d(TAG, "connect to $address")
        connectJob?.cancel()
        connectedJob?.cancel()
        val device = adapter.getRemoteDevice(address)
        connectJob = connectTo(device)
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        connectJob?.cancel()
        connectedJob?.cancel()
        acceptJob?.cancel()

        close(serverSocket)
        connectedJob = connectedTo(socket)
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")
        state = ChatServiceState.NONE
        scope.cancel()
        close(socket, serverSocket)
    }

    suspend fun write(out: ByteArray) {
        if (state != ChatServiceState.CONNECTED) return
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Try to write buffer")
                outputStream?.write(out)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot write to OutputStream", e)
            }
        }
    }

    fun connectionFailed() {
        state = ChatServiceState.NONE
        listener?.onConnectionFailed()
    }

    private fun connectionLost() {
        state = ChatServiceState.NONE
        listener?.onLostConnection()
        start()
    }

    private fun disconnected() {
        listener?.onDisconnected()
    }

    private fun connectionSucceeded() {
        listener?.onConnected()
    }

    private fun handleMessage(bytes: Int, buffer: ByteArray) {
        listener?.onMessage(bytes, buffer)
    }

    private fun accept() = scope.launch {
        withContext(Dispatchers.IO) {
            serverSocket = try {
                adapter.listenUsingRfcommWithServiceRecord(SERVER_NAME, ID)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot open serverSocket", e)
                return@withContext
            }
            state = ChatServiceState.LISTEN
            Log.d(TAG, "BEGIN Accept Coroutine")
            while (state != ChatServiceState.CONNECTED) {
                val socket = try {
                    serverSocket?.accept()!!
                } catch (e: IOException) {
                    Log.e(TAG, "Accept failed.", e)
                    break
                }
                yield()
                Log.d(TAG, "accepted!")
                synchronized(this@BluetoothVoiceChatService) {
                    when (state) {
                        ChatServiceState.LISTEN, ChatServiceState.CONNECTING -> {
                            connected(socket, socket.remoteDevice)
                        }
                        ChatServiceState.NONE, ChatServiceState.CONNECTED -> {
                            Log.d(TAG, "close unwanted socket")
                            close(socket)
                        }
                    }
                }
            }
            Log.i(TAG, "END Accept Coroutine.")
        }
    }

    private fun connectTo(device: BluetoothDevice) = scope.launch {
        Log.i(TAG, "BEGIN ConnectThread")
        state = ChatServiceState.CONNECTING
        withContext(Dispatchers.IO) {
            adapter.cancelDiscovery()
            while (state == ChatServiceState.CONNECTING || state == ChatServiceState.LISTEN) {
                val socket = try {
                    device.createRfcommSocketToServiceRecord(ID)
                } catch (e: IOException) {
                    Log.e(TAG, "cannot create socket to $device", e)
                    delay(3000)
                    continue
                }
                try {
                    socket?.connect()
                } catch (e: IOException) {
                    Log.e(TAG, "Connect to $device failed")
                    delay(3000)
                    continue
                }
                connected(socket!!, device)
                break
            }
        }
    }

    private fun connectedTo(socket: BluetoothSocket) = scope.launch {
        Log.d(TAG, "create ConnectedThread")
        this@BluetoothVoiceChatService.socket = socket
        try {
            inputStream = socket.inputStream
            outputStream = socket.outputStream
        } catch (e: IOException) {
            Log.e(TAG, "Cannot get IOStream from socket", e)
        }
        state = ChatServiceState.CONNECTED
        connectionSucceeded()
        withContext(Dispatchers.IO) {
            Log.i(TAG, "BEGIN ConnectedThread")
            val buffer = ByteArray(8096)
            var bytes: Int
            while (state == ChatServiceState.CONNECTED) {
                try {
                    bytes = inputStream!!.read(buffer)
                    Log.d(TAG, "read $bytes bytes")
                    handleMessage(bytes, buffer)
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
            disconnected()
        }
    }


    private fun close(vararg sockets: Closeable?){
        for(socket in sockets) {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error on close $socket", e)
            }
        }
    }
}