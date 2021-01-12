package com.crakac.bluetoothvoicechat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.crakac.bluetoothvoicechat.databinding.FragmentConnectionBinding

class ConnectionFragment : Fragment() {
    val TAG: String = "ConnectionFragment"
    lateinit var deviceName: String
    lateinit var macAddress: String
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(deviceName, macAddress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = navArgs<ConnectionFragmentArgs>()
        deviceName = args.value.deviceName
        macAddress = args.value.address
        viewModel.start()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConnectionBinding.inflate(inflater)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        viewModel.state.observe(viewLifecycleOwner){
            it?.let{
                showSnackBar(it)
            }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        ensureBluetoothEnabled()
    }
}