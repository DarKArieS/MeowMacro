package com.nekroz.meowmacro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.floating.FloatingWindowService
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

/**
 * Entry point: obtains the overlay permission, then hands the UI off to
 * [com.nekroz.meowmacro.floating.FloatingWindowService] so it floats above other apps, and closes itself.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The foreground-service notification is optional; the overlay works without it.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MeowMacroTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OverlayPermissionScreen(
                        onGrantClick = ::openOverlayPermissionSettings,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also covers returning from the settings screen after granting.
        if (Settings.canDrawOverlays(this)) {
            FloatingWindowService.start(this)
            finish()
        }
    }

    private fun openOverlayPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }
}

@Composable
fun OverlayPermissionScreen(onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.overlay_permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.overlay_permission_desc),
            textAlign = TextAlign.Center
        )
        Button(onClick = onGrantClick) {
            Text(stringResource(R.string.overlay_permission_grant))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OverlayPermissionScreenPreview() {
    MeowMacroTheme {
        OverlayPermissionScreen(onGrantClick = {})
    }
}
