package com.newoether.agora.memoh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.ui.theme.AgoraTheme

/** Dedicated native entry. Execution and durable history are owned by Memoh. */
class MemohActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgoraTheme {
                val model: MemohViewModel = viewModel()
                MemohScreen(model)
            }
        }
    }
}
