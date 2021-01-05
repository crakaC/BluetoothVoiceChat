package com.crakac.bluetoothvoicechat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.IOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

private const val SERVER_NAME = "BluetoothVoiceChatServer"
private val ID = UUID.fromString("225b9254-9d8b-4b8b-84cc-4e37113cfd93")
private const val TAG = "BTVoiceChatService"

class BluetoothVoiceChatService {

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(buffer: ByteArray, bytes: Int)
    }

    private var scope = CoroutineScope(Job())
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var listener: ConnectionListener? = null

    private var serverSocket: BluetoothServerSocket? = null

    private val writeQueue = LinkedBlockingQueue<ByteArray>()
    private val readBuffer = ByteArray(8096)

    @Volatile
    private var readCount = 0L
    @Volatile
    private var writeCount = 0L
    @Volatile
    private var isConnected = false

    init {
        scope.launch {
            while(isActive){
                delay(1000)
                if(isConnected) {
                    Log.d(TAG, "read: $readCount, write: $writeCount")
                }
            }
        }
    }
    fun setConnectionListener(listener: ConnectionListener?) {
        this.listener = listener
    }

    fun start() {
        accept()
    }

    fun connect(address: String) {
        val device = adapter.getRemoteDevice(address)
        connectTo(device)
    }

    fun stop() {
        Log.d(TAG, "stop")
        scope.cancel()
        close(serverSocket)
    }

    fun write(out: ByteArray) {
        writeQueue.add(out)
    }

    @Synchronized
    private fun disconnected() {
        if(isConnected) {
            isConnected = false
            listener?.onDisconnected()
            accept()
        }
    }

    private fun connectionEstablished() {
        listener?.onConnected()
    }

    private fun handleMessage(buffer: ByteArray, bytes: Int) {
        listener?.onMessage(buffer, bytes)
    }

    private fun accept(){
        scope.launch {
            Log.d(TAG, "BEGIN Accept Coroutine")
            while (isActive && !isConnected) {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVER_NAME, ID)
                    val socket = serverSocket?.accept()!!
                    Log.d(TAG, "accepted!")
                    connected(socket)
                    close(serverSocket)
                } catch (e: IOException) {
                    Log.e(TAG, "Cannot accept()", e)
                    delay(3000)
                    continue
                }
            }
            Log.i(TAG, "END Accept Coroutine.")
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        scope.launch {
            Log.i(TAG, "BEGIN ConnectThread")
            while (isActive && !isConnected) {
                val socket = try {
                    device.createRfcommSocketToServiceRecord(ID)
                } catch (e: IOException) {
                    Log.e(TAG, "cannot create socket to $device", e)
                    delay(3000)
                    continue
                }
                try {
                    adapter.cancelDiscovery()
                    socket.connect()
                } catch (e: IOException) {
                    Log.e(TAG, "Connect to $device failed")
                    delay(3000)
                    continue
                }
                connected(socket)
            }
        }
    }

    @Synchronized
    private fun connected(socket: BluetoothSocket){
        if(isConnected){
            close(socket)
            return
        }
        connectionEstablished()
        isConnected = true
        scope.launch {
            while (isActive && isConnected) {
                try {
                    val bytes = socket.inputStream.read(readBuffer, 0, readBuffer.size)
                    handleMessage(readBuffer.copyOf(), bytes)
                    readCount += bytes
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    disconnected()
                }
            }
            close(socket)
        }
        scope.launch{
            while (isActive && isConnected) {
                val out = writeQueue.poll() ?: continue
                try {
                    socket.outputStream.write(out)
                    writeCount += out.size
                } catch (e: IOException) {
                    Log.e(TAG, "Cannot write to OutputStream", e)
                    disconnected()
                }
            }
            close(socket)
            writeQueue.clear()
        }
    }

    private fun close(vararg sockets: Closeable?) {
        for (socket in sockets) {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error on close $socket", e)
            }
        }
    }
}