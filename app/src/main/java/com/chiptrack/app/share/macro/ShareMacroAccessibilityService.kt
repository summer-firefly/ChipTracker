package com.chiptrack.app.share.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.chiptrack.app.R

/**
 * 测试版分享宏：用无障碍浮层捕获真实触摸（含微信「发送」），再手势回放。
 * 脆弱，仅供自测。
 */
class ShareMacroAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recorded = mutableListOf<ShareMacroStep>()
    private var lastEventAt = 0L
    private var phase: ShareMacroPhase = ShareMacroPhase.IDLE
    private var recordingStartedAt = 0L
    private var injectingTouch = false

    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "service connected")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        removeRecordOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 录制改走浮层触摸；事件回调仅作备用日志
        if (event == null || phase != ShareMacroPhase.RECORDING) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d(TAG, "a11y click pkg=${event.packageName} (ignored, using overlay)")
        }
    }

    override fun onInterrupt() = Unit

    fun startRecording() {
        handler.removeCallbacksAndMessages(null)
        recorded.clear()
        lastEventAt = 0L
        injectingTouch = false
        recordingStartedAt = SystemClock.uptimeMillis()
        phase = ShareMacroPhase.RECORDING
        showRecordOverlay()
        Log.i(TAG, "recording started (touch overlay)")
    }

    fun stopRecordingAndSave(store: ShareMacroStore): Int {
        if (phase != ShareMacroPhase.RECORDING) return 0
        phase = ShareMacroPhase.IDLE
        removeRecordOverlay()
        val steps = recorded.toList()
        recorded.clear()
        lastEventAt = 0L
        injectingTouch = false
        if (steps.isEmpty()) {
            Log.i(TAG, "recording stopped, no steps")
            return 0
        }
        store.steps = steps
        Log.i(TAG, "recording saved ${steps.size} steps: ${steps.map { it.label }}")
        return steps.size
    }

    fun isRecording(): Boolean = phase == ShareMacroPhase.RECORDING

    fun isReplaying(): Boolean = phase == ShareMacroPhase.REPLAYING

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        recorded.clear()
        injectingTouch = false
        phase = ShareMacroPhase.IDLE
        removeRecordOverlay()
    }

    fun replay(
        steps: List<ShareMacroStep>,
        initialDelayMs: Long = 1200L,
        returnDelayMs: Long = 900L,
        onFinished: (success: Boolean) -> Unit
    ) {
        if (steps.isEmpty()) {
            onFinished(false)
            return
        }
        handler.removeCallbacksAndMessages(null)
        removeRecordOverlay()
        phase = ShareMacroPhase.REPLAYING
        Log.i(TAG, "replay ${steps.size} steps, initialDelay=$initialDelayMs")

        fun runAt(index: Int) {
            if (phase != ShareMacroPhase.REPLAYING) {
                onFinished(false)
                return
            }
            if (index >= steps.size) {
                handler.postDelayed({
                    bringAppToFront()
                    phase = ShareMacroPhase.IDLE
                    onFinished(true)
                }, returnDelayMs)
                return
            }
            val step = steps[index]
            val wait = if (index == 0) {
                initialDelayMs + step.delayMs.coerceAtLeast(0L)
            } else {
                step.delayMs.coerceIn(MIN_STEP_DELAY, MAX_STEP_DELAY)
            }
            handler.postDelayed({
                if (phase != ShareMacroPhase.REPLAYING) {
                    onFinished(false)
                    return@postDelayed
                }
                Log.i(TAG, "replay #$index click (${step.x},${step.y}) ${step.label}")
                dispatchClick(step.x, step.y) {
                    runAt(index + 1)
                }
            }, wait)
        }
        runAt(0)
    }

    private fun showRecordOverlay() {
        if (overlayRoot != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val banner = TextView(this).apply {
            text = getString(R.string.share_macro_recording_banner)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1B5E20.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        root.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        root.setOnTouchListener { _, event ->
            if (phase != ShareMacroPhase.RECORDING) return@setOnTouchListener false
            if (injectingTouch) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - recordingStartedAt < 350L) return@setOnTouchListener true
                    val x = event.rawX
                    val y = event.rawY
                    // 点到顶部提示条不录、不注入
                    if (y <= banner.height + dp(4)) return@setOnTouchListener true
                    recordTouch(x, y)
                    passThroughClick(x, y)
                    true
                }
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
                else -> true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ShareMacroRecordOverlay"
        }

        runCatching {
            wm.addView(root, params)
            overlayRoot = root
            overlayParams = params
            Log.i(TAG, "record overlay shown")
        }.onFailure {
            Log.e(TAG, "failed to show record overlay", it)
        }
    }

    private fun removeRecordOverlay() {
        val root = overlayRoot ?: return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(root) }
        overlayRoot = null
        overlayParams = null
        injectingTouch = false
        Log.i(TAG, "record overlay removed")
    }

    private fun recordTouch(x: Float, y: Float) {
        if (recorded.size >= MAX_STEPS) return
        val now = SystemClock.uptimeMillis()
        // 去抖：同位置极短时间重复
        recorded.lastOrNull()?.let { last ->
            if (now - lastEventAt < 120L &&
                kotlin.math.abs(last.x - x) < 12f &&
                kotlin.math.abs(last.y - y) < 12f
            ) {
                return
            }
        }
        val delay = if (lastEventAt == 0L) {
            0L
        } else {
            (now - lastEventAt).coerceIn(MIN_STEP_DELAY, MAX_STEP_DELAY)
        }
        lastEventAt = now
        val label = "touch@${x.toInt()},${y.toInt()}"
        recorded.add(ShareMacroStep(delayMs = delay, x = x, y = y, label = label))
        Log.i(TAG, "record #${recorded.size}: ($x,$y) delay=$delay")
    }

    /** 先让浮层不拦截，再把同点点击注入到底层微信 */
    private fun passThroughClick(x: Float, y: Float) {
        val root = overlayRoot
        val params = overlayParams
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (root == null || params == null) return

        injectingTouch = true
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(root, params) }

        // 稍等一帧再注入，确保浮层已不吃触摸
        handler.postDelayed({
            dispatchClick(x, y) {
                handler.postDelayed({
                    restoreOverlayTouchable()
                }, 40L)
            }
        }, 30L)
    }

    private fun restoreOverlayTouchable() {
        val root = overlayRoot
        val params = overlayParams
        if (root == null || params == null) {
            injectingTouch = false
            return
        }
        if (phase != ShareMacroPhase.RECORDING) {
            injectingTouch = false
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(root, params) }
        injectingTouch = false
    }

    private fun dispatchClick(x: Float, y: Float, onDone: () -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, CLICK_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onDone()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "gesture cancelled at ($x,$y)")
                    onDone()
                }
            },
            null
        )
        if (!ok) {
            Log.w(TAG, "dispatchGesture failed at ($x,$y)")
            handler.post(onDone)
        }
    }

    private fun bringAppToFront() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { startActivity(launch) }
            .onFailure { Log.e(TAG, "bringAppToFront failed", it) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    companion object {
        private const val TAG = "ShareMacroA11y"
        const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val MAX_STEPS = 24
        private const val MIN_STEP_DELAY = 180L
        private const val MAX_STEP_DELAY = 8000L
        private const val CLICK_DURATION_MS = 60L

        @Volatile
        var instance: ShareMacroAccessibilityService? = null
            private set
    }
}
