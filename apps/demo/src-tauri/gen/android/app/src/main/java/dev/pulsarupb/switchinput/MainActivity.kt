package dev.pulsarupb.switchinput

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import dev.pulsarupb.switchinput.plugin.SwitchInputBridge

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
  }

  // Forward raw input to the reusable switch-input plugin before the WebView handles it.
  // Gamepad keys are consumed by the plugin; everything else falls through unchanged.
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (SwitchInputBridge.onKeyEvent(event)) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (SwitchInputBridge.onGenericMotionEvent(event)) {
      return true
    }
    return super.dispatchGenericMotionEvent(event)
  }

  // Touch is observed, never consumed, so the WebView still receives its own touch events.
  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    SwitchInputBridge.onTouchEvent(event)
    return super.dispatchTouchEvent(event)
  }
}
