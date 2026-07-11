package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.KavachApp
import com.example.ui.KavachViewModel
import com.example.ui.KavachState
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val viewModel: KavachViewModel by viewModels()

  private var volumeClickCount = 0
  private var lastVolumeClickTime = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        KavachApp(viewModel = viewModel)
      }
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      val currentTime = System.currentTimeMillis()
      if (currentTime - lastVolumeClickTime < 1500) {
        volumeClickCount++
      } else {
        volumeClickCount = 1
      }
      lastVolumeClickTime = currentTime

      if (volumeClickCount >= 3) {
        volumeClickCount = 0 // reset
        if (viewModel.uiState.value == KavachState.MONITORING) {
          viewModel.triggerSafety("VOLUME_KEYS", "Physical hardware triple-press on volume keys.")
          return true // Consume event
        }
      }
    }
    return super.onKeyDown(keyCode, event)
  }
}
