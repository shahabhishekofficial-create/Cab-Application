package com.caboperations.driver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.ui.DriverApp

class MainActivity : ComponentActivity() {
    private val oauthUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthUri.value = intent?.data
        setContent {
            DriverApp(
                oauthUri = oauthUri.value,
                onOAuthUriConsumed = { oauthUri.value = null },
            )
        }
        enqueueAutomaticSync()
    }

    override fun onResume() {
        super.onResume()
        enqueueAutomaticSync()
    }

    private fun enqueueAutomaticSync() {
        SyncScheduler.enqueue(this, BuildConfig.API_BASE_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthUri.value = intent.data
    }
}
