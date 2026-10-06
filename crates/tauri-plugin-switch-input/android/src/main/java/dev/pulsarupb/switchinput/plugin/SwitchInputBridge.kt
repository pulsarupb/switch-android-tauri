package dev.pulsarupb.switchinput.plugin

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Static entry point used by the host Activity to forward raw input events to the
 * switch-input plugin.
 *
 * Keeping this in the plugin (rather than in the app) makes the capture logic reusable:
 * an app only has to forward its `dispatchKeyEvent` / `dispatchGenericMotionEvent` /
 * `dispatchTouchEvent` overrides to the matching methods here.
 */
object SwitchInputBridge {
    @Volatile
    private var plugin: SwitchInputPlugin? = null

    fun attach(plugin: SwitchInputPlugin) {
        this.plugin = plugin
    }

    fun detach(plugin: SwitchInputPlugin) {
        if (this.plugin === plugin) {
            this.plugin = null
        }
    }

    /** @return true if the event was consumed by the plugin. */
    fun onKeyEvent(event: KeyEvent): Boolean = plugin?.captureKeyEvent(event) ?: false

    /** @return true if the event was consumed by the plugin. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean =
        plugin?.captureGenericMotionEvent(event) ?: false

    /** @return true if the event was consumed by the plugin. */
    fun onTouchEvent(event: MotionEvent): Boolean = plugin?.captureTouchEvent(event) ?: false
}
