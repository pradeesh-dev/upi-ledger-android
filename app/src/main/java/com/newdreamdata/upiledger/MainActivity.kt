package com.newdreamdata.upiledger

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.newdreamdata.upiledger.ui.Event
import com.newdreamdata.upiledger.ui.LedgerViewModel
import com.newdreamdata.upiledger.ui.screens.HomeScreen
import com.newdreamdata.upiledger.ui.screens.RegistryScreen
import com.newdreamdata.upiledger.ui.screens.ReviewScreen
import com.newdreamdata.upiledger.ui.screens.SettingsScreen
import com.newdreamdata.upiledger.ui.screens.SummaryScreen
import com.newdreamdata.upiledger.ui.theme.UpiLedgerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UpiLedgerTheme { Root(vm) } }
        if (savedInstanceState == null) handleIntent(intent)
        askPermissionsOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                if (type.startsWith("image/")) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (uri != null) vm.startFromImage(uri)
                    else Toast.makeText(this, "No image was shared", Toast.LENGTH_SHORT).show()
                } else if (type.startsWith("text/")) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    if (text.isNotBlank()) vm.startFromText(text)
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "upiledger" && data.host == "expense") {
                    data.lastPathSegment?.toLongOrNull()?.let { vm.startEdit(it) }
                }
            }
        }
    }

    private fun askPermissionsOnce() {
        lifecycleScope.launch {
            val app = application as LedgerApp
            if (app.settings.onboardedFlow.first()) return@launch
            app.settings.setOnboarded()
            val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(perms.toTypedArray())
        }
    }
}

@Composable
private fun Root(vm: LedgerViewModel) {
    val draft by vm.draft.collectAsState()
    val event by vm.events.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(event) {
        when (val e = event) {
            is Event.Toast -> { Toast.makeText(ctx, e.text, Toast.LENGTH_SHORT).show(); vm.consumeEvent() }
            is Event.Saved -> { tab = 0; vm.consumeEvent() }
            null -> {}
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val d = draft
        if (d != null) {
            BackHandler { vm.discard() }
            ReviewScreen(vm, d)
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Expenses") }, icon = {
                            if (reviewCount > 0) BadgedBox(badge = { Badge { Text(reviewCount.toString()) } }) { Icon(Icons.AutoMirrored.Filled.List, null) }
                            else Icon(Icons.AutoMirrored.Filled.List, null)
                        })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Summary") }, icon = { Icon(Icons.Outlined.Info, null) })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Registry") }, icon = { Icon(Icons.Default.AccountBox, null) })
                        NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) })
                    }
                }
            ) { inner: PaddingValues ->
                when (tab) {
                    0 -> HomeScreen(vm, inner)
                    1 -> SummaryScreen(vm, inner)
                    2 -> RegistryScreen(vm, inner)
                    else -> SettingsScreen(vm, inner)
                }
            }
        }
    }
}
