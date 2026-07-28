package com.uprunner.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Stub for M1. M2 replaces this with the full-screen MapLibre 3D map, GPX route
// search/load, target-split editing, and offline elevation-region download (spec §3 Tab 2).
@Composable
fun PlanScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Plan — coming in a future milestone")
    }
}
