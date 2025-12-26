package com.example.volumetilemodule

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val SETTINGS_AUTHORITY = "com.example.volumetilemodule.settings"
        private val SETTINGS_URI: Uri = Uri.parse("content://$SETTINGS_AUTHORITY/prefs")
    }

    // Settings cache
    private var enableSlide = true
    private var enableLongPress = true
    private var enableSingleTap = false
    private var slideSensitivity = 20
    private var longPressDelay = 500

    private fun loadSettings(context: Context) {
        try {
            val cursor = context.contentResolver.query(SETTINGS_URI, null, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val key = it.getString(0)
                    val value = it.getString(1)
                    when (key) {
                        "enable_slide" -> enableSlide = value.toBoolean()
                        "enable_long_press" -> enableLongPress = value.toBoolean()
                        "enable_single_tap" -> enableSingleTap = value.toBoolean()
                        "slide_sensitivity" -> slideSensitivity = value.toIntOrNull() ?: 20
                        "long_press_delay" -> longPressDelay = value.toIntOrNull() ?: 500
                    }
                }
            }
            XposedBridge.log("VolumeTileModule: Loaded settings - enableSlide=$enableSlide, enableLongPress=$enableLongPress, enableSingleTap=$enableSingleTap")
        } catch (e: Throwable) {
            XposedBridge.log("VolumeTileModule: Error loading settings: $e")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.android.systemui") {
            XposedBridge.log("VolumeTileModule: Hooking SystemUI")
            hookSystemUI(lpparam)
        }
    }

    private fun hookSystemUI(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Status Bar for Volume Slide
        try {
            val phoneStatusBarViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                lpparam.classLoader
            )

            XposedBridge.hookAllMethods(
                phoneStatusBarViewClass,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        val context = view.context
                        
                        // Load settings from ContentProvider
                        loadSettings(context)
                    }
                }
            )

            XposedBridge.hookAllMethods(
                phoneStatusBarViewClass,
                "onTouchEvent",
                object : XC_MethodHook() {
                    private var startX = 0f
                    private var startY = 0f
                    private var isTracking = false
                    private var swipeThreshold = 0f
                    private var gestureDetector: GestureDetector? = null
                    private var currentLongPressDelay = 500
                    private var lastSettingsHash = 0
                    private var singleTapHandled = false
                    private var touchDownTime = 0L
                    private val slideStartDelay = 150L // Delay before slide detection starts (ms)

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args[0] as MotionEvent
                        val view = param.thisObject as View
                        val context = view.context
                        
                        // Reload settings on ACTION_DOWN (start of new gesture)
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            loadSettings(context)
                            singleTapHandled = false
                            startX = event.x
                            startY = event.y
                            
                            // Calculate settings hash to detect changes
                            val newSettingsHash = (enableLongPress.hashCode() * 31 + enableSingleTap.hashCode()) * 31 + longPressDelay
                            
                            // If settings changed, invalidate the gesture detector immediately
                            if (lastSettingsHash != newSettingsHash) {
                                XposedBridge.log("VolumeTileModule: Settings changed, invalidating GestureDetector")
                                gestureDetector = null
                                lastSettingsHash = newSettingsHash
                            }
                        }

                        // Initialize threshold based on settings
                        swipeThreshold = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            slideSensitivity.toFloat(),
                            context.resources.displayMetrics
                        )

                        // Handle single tap manually if only single tap is enabled (bypass GestureDetector)
                        if (enableSingleTap && !enableLongPress) {
                            // Clear GestureDetector immediately to prevent any long press detection
                            gestureDetector = null
                            
                            // Simple single tap detection without GestureDetector
                            if (event.action == MotionEvent.ACTION_UP && !singleTapHandled) {
                                val touchDuration = event.eventTime - event.downTime
                                val deltaX = Math.abs(event.x - startX)
                                val deltaY = Math.abs(event.y - startY)
                                
                                //movement threshold
                                val tapSlop = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    30f,
                                    context.resources.displayMetrics
                                )
                                
                                // If it was a quick tap
                                if (touchDuration < 500 && deltaX < tapSlop && deltaY < tapSlop) {
                                    singleTapHandled = true
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                                    XposedBridge.log("VolumeTileModule: Single tap detected (manual mode)")
                                }
                            }
                        } else if (enableLongPress || enableSingleTap) {
                            // Use GestureDetector when long press is involved
                            // GestureDetector will be null if settings changed (invalidated on ACTION_DOWN)
                            if (gestureDetector == null) {
                                currentLongPressDelay = longPressDelay
                                val handler = Handler(Looper.getMainLooper())
                                
                                // capture current settings at creation time
                                val capturedEnableLongPress = enableLongPress
                                val capturedEnableSingleTap = enableSingleTap
                                
                                gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(e: MotionEvent): Boolean {
                                        return true
                                    }

                                    override fun onShowPress(e: MotionEvent) {
                                        // Do nothing, just acknowledge
                                    }

                                    override fun onLongPress(e: MotionEvent) {
                                        // Use captured setting value
                                        if (!capturedEnableLongPress) return
                                        if (singleTapHandled) return
                                        
                                        // Stop tracking for slide so we dont adjust volume while lifting finger
                                        isTracking = false
                                        singleTapHandled = true
                                        
                                        // Mute and Open Panel
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                        audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                                        
                                        // Haptic feedback for long press
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        XposedBridge.log("VolumeTileModule: Long press detected")
                                    }

                                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                                        // Use captured setting value
                                        if (!capturedEnableSingleTap) return false
                                        if (singleTapHandled) return false
                                        
                                        singleTapHandled = true

                                        // Open Volume Panel
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                        audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                                        XposedBridge.log("VolumeTileModule: Single tap detected (GestureDetector mode)")
                                        return true
                                    }
                                }, handler)
                                
                                // Set the custom long press timeout using reflection
                                if (capturedEnableLongPress) {
                                    gestureDetector?.setIsLongpressEnabled(true)
                                    try {
                                        XposedHelpers.setObjectField(gestureDetector, "mLongpressTimeout", longPressDelay)
                                    } catch (e: Throwable) {
                                        XposedBridge.log("VolumeTileModule: Could not set long press timeout: $e")
                                    }
                                } else {
                                    gestureDetector?.setIsLongpressEnabled(false)
                                }
                                XposedBridge.log("VolumeTileModule: Created GestureDetector - longPress=$capturedEnableLongPress, singleTap=$capturedEnableSingleTap")
                            }
                            gestureDetector?.onTouchEvent(event)
                        } else {
                            // Neither feature enabled clear the detector
                            gestureDetector = null
                        }

                        // Skip slide handling entirely if only single tap is enabled
                        if (!enableSlide || (enableSingleTap && !enableLongPress)) return

                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                                touchDownTime = event.downTime
                                isTracking = true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!isTracking) return
                                
                                // Wait for slide delay before processing moves
                                // to preventthe quick taps from triggering slide detection
                                val touchDuration = event.eventTime - touchDownTime
                                if (touchDuration < slideStartDelay) return

                                val deltaX = event.x - startX
                                val deltaY = event.y - startY

                                // If vertical movement is larger, it is likely a shade pull so ignore this
                                if (Math.abs(deltaY) > Math.abs(deltaX)) {
                                    isTracking = false
                                    return
                                }

                                if (Math.abs(deltaX) > swipeThreshold) {
                                    // Adjust volume
                                    adjustVolume(context, deltaX > 0)
                                    
                                    // Reset startX to allow continuous sliding
                                    startX = event.x
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isTracking = false
                            }
                        }
                    }
                }
            )
             XposedBridge.log("VolumeTileModule: Successfully hooked PhoneStatusBarView")
        } catch (e: Throwable) {
             XposedBridge.log("VolumeTileModule: Error hooking PhoneStatusBarView: $e")
        }
    }

    private fun adjustVolume(context: Context, increase: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
    }
}