package com.uprunner.app.ui.plan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.maps.MapLibreMap

@Composable
fun PlanScreen(viewModel: PlanViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::loadGpxFromUri) }

    Box(modifier = Modifier.fillMaxSize()) {
        UprunnerMap(
            track = uiState.track,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map = it },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(onClick = { gpxPickerLauncher.launch("*/*") }) {
                        Text("Load GPX")
                    }
                    Button(
                        onClick = {
                            map?.let { m ->
                                viewModel.downloadOfflineRegion(
                                    bounds = m.projection.visibleRegion.latLngBounds,
                                    minZoom = (m.cameraPosition.zoom - 1).coerceAtLeast(0.0),
                                    maxZoom = (m.cameraPosition.zoom + 3).coerceAtMost(20.0),
                                )
                            }
                        },
                        enabled = map != null,
                    ) {
                        Text("Download Offline Region")
                    }
                }

                uiState.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                uiState.offlineDownloadStatus?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }

                if (uiState.splitTargetsText.isNotEmpty()) {
                    Text(
                        "Target splits (mm:ss per km)",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(uiState.splitTargetsText.keys.sorted()) { km ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Km $km")
                                OutlinedTextField(
                                    value = uiState.splitTargetsText[km].orEmpty(),
                                    onValueChange = { viewModel.updateSplitTarget(km, it) },
                                    placeholder = { Text("mm:ss") },
                                    modifier = Modifier.heightIn(max = 56.dp),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                    Button(onClick = viewModel::saveSplits, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Save Splits")
                    }
                }
            }
        }
    }
}
