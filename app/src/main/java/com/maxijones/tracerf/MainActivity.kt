package com.maxijones.tracerf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: TraceRfViewModel by viewModels()

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TraceRfApp(vm) {
                permissions.launch(vm.requiredPermissions())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
    }
}
