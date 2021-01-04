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
    private var outSocket: BluetoothSocket? = null
    private var inSocket: BluetoothSocket? = null

    private var _isInSocketReady = false
    private var isInSocketReady: Boolean
        @Synchronized
        get() = _isInSocketReady
        @Synchronized set(value) {
            _isInSocketReady = value
        }

    private var _isOutnSocketReady = false
    private var isOutnSocketReady: Boolean
        @Synchronized
        get() = _isOutnSocketReady
        @Synchronized
        set(value) {
            _isOutnSocketReady = value
        }

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val writeQueue = LinkedBlockingQueue<ByteArray>()
    private val readBuffer = ByteArray(8096)

    @Volatile
    private var readCount = 0L
    @Volatile
    private var writeCount = 0L

    fun setConnectionListener(listener: ConnectionListener?) {
        this.listener = listener
        scope.launch {
            while(isActive){
                delay(1000)
                Log.d(TAG, "read: $readCount, write: $writeCount")
            }
        }
    }

    @Synchronized
    fun start() {
        accept()
    }

    @Synchronized
    fun connect(address: String) {
        val device = adapter.getRemoteDevice(address)
        connectTo(device)
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")
        scope.cancel()
        close(inSocket, outSocket, serverSocket)
    }

    fun write(out: ByteArray) {
        writeQueue.add(out)
    }

    private fun disconnected() {
        listener?.onDisconnected()
    }

    private fun connectionSucceeded() {
        listener?.onConnected()
    }

    private fun handleMessage(buffer: ByteArray, bytes: Int) {
        listener?.onMessage(buffer, bytes)
    }

    @Synchronized
    private fun checkBiDirectionalConnection() {
        if (isInSocketReady && isOutnSocketReady) {
            connectionSucceeded()
        }
    }

    private fun accept(){
        scope.launch {
            Log.d(TAG, "BEGIN Accept Coroutine")
            while (isActive) {
                serverSocket = try {
                    adapter.listenUsingRfcommWithServiceRecord(SERVER_NAME, ID)
                } catch (e: IOException) {
                    val d = 5000L
                    Log.e(TAG, "Cannot open serverSocket. retry after $d msec", e)
                    delay(d)
                    continue
                }
                isInSocketReady = false
                inSocket = try {
                    serverSocket?.accept()!!
                } catch (e: IOException) {
                    Log.e(TAG, "Accept failed.", e)
                    break
                }
                Log.d(TAG, "accepted!")
                try {
                    inputStream = inSocket?.inputStream
                } catch (e: IOException) {
                    Log.e(TAG, "Cannot get IOStream from socket", e)
                    continue
                }
                isInSocketReady = true
                checkBiDirectionalConnection()
                while (isActive) {
                    try {
                        val bytes = inputStream!!.read(readBuffer, 0, readBuffer.size)
                        handleMessage(readBuffer, bytes)
                        readCount += bytes
                    } catch (e: IOException) {
                        Log.e(TAG, "disconnected", e)
                        disconnected()
                        break
                    }
                }
            }
            Log.i(TAG, "END Accept Coroutine.")
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        scope.launch {
            Log.i(TAG, "BEGIN ConnectThread")
            while (isActive) {
                isOutnSocketReady = false
                outSocket = try {
                    device.createRfcommSocketToServiceRecord(ID)
                } catch (e: IOException) {
                    Log.e(TAG, "cannot create socket to $device", e)
                    delay(3000)
                    continue
                }
                try {
                    adapter.cancelDiscovery()
                    outSocket?.connect()
                    outputStream = outSocket?.outputStream
                } catch (e: IOException) {
                    Log.e(TAG, "Connect to $device failed")
                    delay(3000)
                    continue
                }
                isOutnSocketReady = true
                checkBiDirectionalConnection()
                while (isActive) {
                    val out = writeQueue.poll() ?: continue
                    try {
                        outputStream?.write(out)
                        writeCount += out.size
                    } catch (e: IOException) {
                        Log.e(TAG, "Cannot write to OutputStream", e)
                        disconnected()
                    }
                }
                writeQueue.clear()
            }
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