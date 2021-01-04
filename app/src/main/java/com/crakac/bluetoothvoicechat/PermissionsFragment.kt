package com.crakac.bluetoothvoicechat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

private const val REQUEST_CODE = 11
private val PERMISSIONS_REQUIRED = arrayOf(
    Manifest.permission.RECORD_AUDIO
)

class PermissionsFragment : Fragment() {
    val TAG: String = "PermissionFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasPermissions(requireContext())) {
            requestPermissions(PERMISSIONS_REQUIRED, REQUEST_CODE)
        } else {
            findNavController().popBackStack()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode != REQUEST_CODE) return
        if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(context, "Permission request denied", Toast.LENGTH_SHORT).show()
        }
        findNavController().popBackStack()
    }

    companion object {
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}