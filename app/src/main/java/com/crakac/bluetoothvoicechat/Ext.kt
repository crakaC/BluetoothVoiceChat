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

fun Fragment.showSnackBar(msg: String){
    if(!isVisible) return
    Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
}