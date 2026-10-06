package dev.pulsarupb.switchinput.plugin

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Maps Android [KeyEvent]s produced by the Nintendo Switch virtual Pro Controller
 * (LineageOS `joycond` driver) to stable, logical Switch button names.
 *
 * The primary mapping is done on the raw Linux evdev code ([KeyEvent.getScanCode])
 * because that is where the Switch button layout is defined. The Android key code is
 * only used as a fallback.
 */
object KeyMap {
    // Linux evdev button codes.
    private const val BTN_SOUTH = 304 // physical Switch B
    private const val BTN_EAST = 305 // physical Switch A
    private const val BTN_NORTH = 307 // physical Switch X
    private const val BTN_WEST = 308 // physical Switch Y
    private const val BTN_Z = 309
    private const val BTN_TL = 310 // L
    private const val BTN_TR = 311 // R
    private const val BTN_TL2 = 312 // ZL
    private const val BTN_TR2 = 313 // ZR
    private const val BTN_SELECT = 314 // Minus
    private const val BTN_START = 315 // Plus
    private const val BTN_MODE = 316 // Home
    private const val BTN_THUMBL = 317 // Left stick click
    private const val BTN_THUMBR = 318 // Right stick click
    private const val BTN_DPAD_UP = 544
    private const val BTN_DPAD_DOWN = 545
    private const val BTN_DPAD_LEFT = 546
    private const val BTN_DPAD_RIGHT = 547

    fun buttonName(event: KeyEvent): String? =
        fromScanCode(event.scanCode) ?: fromAndroidKeyCode(event.keyCode)

    fun fromScanCode(code: Int): String? = when (code) {
        BTN_SOUTH -> "B"
        BTN_EAST -> "A"
        BTN_NORTH -> "X"
        BTN_WEST -> "Y"
        BTN_Z -> "Z"
        BTN_TL -> "L"
        BTN_TR -> "R"
        BTN_TL2 -> "ZL"
        BTN_TR2 -> "ZR"
        BTN_SELECT -> "MINUS"
        BTN_START -> "PLUS"
        BTN_MODE -> "HOME"
        BTN_THUMBL -> "LSTICK"
        BTN_THUMBR -> "RSTICK"
        BTN_DPAD_UP -> "UP"
        BTN_DPAD_DOWN -> "DOWN"
        BTN_DPAD_LEFT -> "LEFT"
        BTN_DPAD_RIGHT -> "RIGHT"
        else -> null
    }

    /**
     * Fallback mapping from the standard Android gamepad key codes. Note the face-button
     * swap: Android's BUTTON_A is the bottom button (physical Switch B).
     */
    fun fromAndroidKeyCode(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "B"
        KeyEvent.KEYCODE_BUTTON_B -> "A"
        KeyEvent.KEYCODE_BUTTON_X -> "Y"
        KeyEvent.KEYCODE_BUTTON_Y -> "X"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R"
        KeyEvent.KEYCODE_BUTTON_L2 -> "ZL"
        KeyEvent.KEYCODE_BUTTON_R2 -> "ZR"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "LSTICK"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "RSTICK"
        KeyEvent.KEYCODE_BUTTON_START -> "PLUS"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "MINUS"
        KeyEvent.KEYCODE_BUTTON_MODE -> "HOME"
        KeyEvent.KEYCODE_DPAD_UP -> "UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        else -> null
    }

    fun isGamepadSource(source: Int): Boolean {
        return (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
}
