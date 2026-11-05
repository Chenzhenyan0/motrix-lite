package com.motrixlite.downloader.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motrixlite.downloader.App
import com.motrixlite.downloader.ui.theme.MotrixLiteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            MotrixLiteTheme(themeMode = settings.themeMode) {
                MainScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val container = (application as App).container
        container.applicationScope.launch {
            runCatching { container.repository.flush() }
        }
    }
}
