package com.crakac.bluetoothvoicechat

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.crakac.bluetoothvoicechat.databinding.FragmentDevicelistBinding

class DeviceListFragment : Fragment() {
    val TAG: String = "DeviceListFragment"

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureBluetoothEnabled()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(DeviceListFragmentDirections.actionDeviceListFragmentToPermissionsFragment())
        }
        ensureBluetoothEnabled()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDevicelistBinding.inflate(inflater)
        val listView = binding.listView
        val adapter = DeviceListAdapter(requireContext()) { name, address ->
            findNavController().navigate(
                DeviceListFragmentDirections.actionDeviceListFragmentToConnectionFragment(
                    name,
                    address
                )
            )
        }
        adapter.addAll(bluetoothAdapter.bondedDevices.map { it.name to it.address })
        listView.adapter = adapter
        return binding.root
    }

    class DeviceListAdapter(context: Context, private val listener: (String, String) -> Unit) :
        ArrayAdapter<Pair<String, String>>(context, -1) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: inflateView()
            val titleText = v.findViewById<TextView>(R.id.deviceName)
            val subText = v.findViewById<TextView>(R.id.macAddress)
            val (name, address) = getItem(position)!!
            titleText.text = name
            subText.text = address
            v.setOnClickListener {
                listener(name, address)
            }
            return v
        }

        private fun inflateView() =
            LayoutInflater.from(context).inflate(R.layout.list_item_bluetooth, null)
    }
}