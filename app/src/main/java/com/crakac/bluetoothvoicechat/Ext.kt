package com.crakac.bluetoothvoicechat

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

const val REQUEST_ENABLE_BT = 1
fun Fragment.ensureBluetoothEnabled() {
    if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
        startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            REQUEST_ENABLE_BT
        )
    }
}

fun Fragment.showSnackBar(msg: String) {
    if (!isVisible) return
    Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
}

fun ShortArray.toByteArray(shorts: Int): ByteArray {
    val byteArray = ByteArray(shorts * 2)
    for (i in 0 until shorts) {
        val s = get(i).toInt()
        byteArray[i * 2] = s.and(0xFF).toByte()
        byteArray[i * 2 + 1] = s.shr(8).toByte()
    }
    return byteArray
}

fun ByteArray.toShortArray() = ShortArray(size / 2) { i ->
    (get(i * 2).toInt().and(0xFF) or (get(i * 2 + 1)).toInt().and(0xFF).shl(8)).toShort()
}
