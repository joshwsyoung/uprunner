package com.uprunner.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Stub for M1. M5 replaces this with the real text-only AI Coach conversation UI backed by
// the local llama.cpp JNI instance (spec §3 Tab 3).
@Composable
fun ChatScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Chat — coming in a future milestone")
    }
}
