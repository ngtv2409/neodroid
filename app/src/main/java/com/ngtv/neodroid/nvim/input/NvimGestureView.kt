package com.ngtv.neodroid.nvim.input

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Full-screen transparent view to capture gestures
 *
 * - tap is left click-release
 * - swipe is wheel scroll at starting position
 * - long press is a left-drag
 */
class NvimGestureView(context: Context) : View(context) {

    var columns: Int = 1
    var rows: Int = 1
    var cellWidthPx: Float = 1f
    var cellHeightPx: Float = 1f
    var takeModifiers: () -> String = { "" }
    var onMouse: (NvimMouseEvent) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private enum class TouchMode { Idle, Pending, Dragging, Scrolling }

    private var touchMode = TouchMode.Idle
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var touchModifiers = ""
    private var downX = 0f
    private var downY = 0f
    private var startRow = 0
    private var startCol = 0
    private var lastRow = 0
    private var lastCol = 0
    private var lastPointerCount = 0

    private var scrollRow = 0
    private var scrollCol = 0
    private var scrollAccumulatorY = 0f
    private var scrollAccumulatorX = 0f
    private var lastScrollX = 0f
    private var lastScrollY = 0f

    private var velocityTracker: VelocityTracker? = null

    private var flingVelocityX = 0f
    private var flingVelocityY = 0f
    private var lastFlingTime = 0L

    private var mouseButton: NvimMouseButton? = null
    private var mouseModifiers = ""
    private var mouseRow = 0
    private var mouseCol = 0

    private var hoverRow = -1
    private var hoverCol = -1

    private var wheelModifiers = ""
    private var lastWheelTime = 0L

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(true)
    }

    private val longPressRunnable = Runnable {
        if (touchMode == TouchMode.Pending) {
            touchMode = TouchMode.Dragging
            send(
                NvimMouseButton.Left,
                NvimMouseAction.Press,
                touchModifiers,
                startRow,
                startCol,
            )
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (touchMode != TouchMode.Idle) return
            if (abs(flingVelocityX) < 20f && abs(flingVelocityY) < 20f) return

            val now = SystemClock.uptimeMillis()
            val dt = if (lastFlingTime == 0L) {
                0.016f
            } else {
                ((now - lastFlingTime) / 1000f).coerceAtMost(0.05f)
            }
            lastFlingTime = now

            addScrollDelta(flingVelocityX * dt, flingVelocityY * dt)

            flingVelocityX *= FLING_DECAY
            flingVelocityY *= FLING_DECAY
            handler.postDelayed(this, 16L)
        }
    }

    fun resetHover() {
        hoverRow = -1
        hoverCol = -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouseTouch(event)
        } else {
            handleTouch(event)
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return super.onGenericMotionEvent(event)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> handleWheel(event)

            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                handleHover(event)
                true
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                resetHover()
                true
            }

            else -> super.onGenericMotionEvent(event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return super.onHoverEvent(event)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                handleHover(event)
                true
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                resetHover()
                true
            }

            else -> super.onHoverEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        stopFling()
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }

    // Touch
    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startTouch(event)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(event)
            MotionEvent.ACTION_MOVE -> onTouchMove(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
            MotionEvent.ACTION_UP -> endTouch(event)
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
    }

    private fun startTouch(event: MotionEvent) {
        stopFling()
        cancelLongPressCallback()
        recycleVelocityTracker()
        resetWheelBurst()

        activePointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        lastPointerCount = event.pointerCount

        val cell = cellFor(event.x, event.y)
        startRow = cell.first
        startCol = cell.second
        lastRow = startRow
        lastCol = startCol

        scrollAccumulatorX = 0f
        scrollAccumulatorY = 0f
        lastScrollX = event.x
        lastScrollY = event.y

        touchModifiers = takeModifiers()
        touchMode = TouchMode.Pending

        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
        handler.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun onPointerDown(event: MotionEvent) {
        if (touchMode == TouchMode.Pending) {
            cancelLongPressCallback()
            beginScrolling(event)
        } else {
            lastPointerCount = event.pointerCount
            updateScrollAnchor(event)
        }
    }

    private fun onTouchMove(event: MotionEvent) {
        velocityTracker?.addMovement(event)

        when (touchMode) {
            TouchMode.Pending -> {
                val distance = hypot(event.x - downX, event.y - downY)
                if (distance > touchSlop) {
                    cancelLongPressCallback()
                    beginScrolling(event)
                }
            }

            TouchMode.Scrolling -> {
                if (event.pointerCount != lastPointerCount) {
                    lastPointerCount = event.pointerCount
                    updateScrollAnchor(event)
                    return
                }

                val (x, y) = centroid(event)
                val dx = x - lastScrollX
                val dy = y - lastScrollY
                lastScrollX = x
                lastScrollY = y
                addScrollDelta(dx, dy)
            }

            TouchMode.Dragging -> {
                val (x, y) = activePosition(event)
                val cell = cellFor(x, y)
                if (cell.first != lastRow || cell.second != lastCol) {
                    lastRow = cell.first
                    lastCol = cell.second
                    send(
                        NvimMouseButton.Left,
                        NvimMouseAction.Drag,
                        touchModifiers,
                        lastRow,
                        lastCol,
                    )
                }
            }

            TouchMode.Idle -> Unit
        }
    }

    private fun onPointerUp(event: MotionEvent) {
        val pointerId = event.getPointerId(event.actionIndex)
        if (touchMode == TouchMode.Dragging && pointerId == activePointerId) {
            send(
                NvimMouseButton.Left,
                NvimMouseAction.Release,
                touchModifiers,
                lastRow,
                lastCol,
            )
            touchMode = TouchMode.Idle
            cancelLongPressCallback()
            recycleVelocityTracker()
        } else {
            lastPointerCount = event.pointerCount
        }
    }

    private fun endTouch(event: MotionEvent) {
        cancelLongPressCallback()
        velocityTracker?.addMovement(event)

        when (touchMode) {
            TouchMode.Pending -> {
                val cell = cellFor(event.x, event.y)
                send(
                    NvimMouseButton.Left,
                    NvimMouseAction.Press,
                    touchModifiers,
                    cell.first,
                    cell.second,
                )
                send(
                    NvimMouseButton.Left,
                    NvimMouseAction.Release,
                    touchModifiers,
                    cell.first,
                    cell.second,
                )
            }

            TouchMode.Dragging -> {
                send(
                    NvimMouseButton.Left,
                    NvimMouseAction.Release,
                    touchModifiers,
                    lastRow,
                    lastCol,
                )
            }

            TouchMode.Scrolling -> {
                startFlingFromVelocity(event)
            }

            TouchMode.Idle -> Unit
        }

        touchMode = TouchMode.Idle
        activePointerId = MotionEvent.INVALID_POINTER_ID
        recycleVelocityTracker()
    }

    private fun cancelTouch() {
        cancelLongPressCallback()
        if (touchMode == TouchMode.Dragging) {
            send(
                NvimMouseButton.Left,
                NvimMouseAction.Release,
                touchModifiers,
                lastRow,
                lastCol,
            )
        }
        stopFling()
        touchMode = TouchMode.Idle
        activePointerId = MotionEvent.INVALID_POINTER_ID
        recycleVelocityTracker()
    }

    private fun beginScrolling(event: MotionEvent) {
        touchMode = TouchMode.Scrolling
        scrollAccumulatorX = 0f
        scrollAccumulatorY = 0f

        val (x, y) = if (event.pointerCount > 1) centroid(event) else (downX to downY)
        val cell = cellFor(x, y)
        scrollRow = cell.first
        scrollCol = cell.second
        lastScrollX = x
        lastScrollY = y
        lastPointerCount = event.pointerCount
    }

    private fun updateScrollAnchor(event: MotionEvent) {
        val (x, y) = if (event.pointerCount > 1) centroid(event) else activePosition(event)
        lastScrollX = x
        lastScrollY = y
    }

    private fun addScrollDelta(dx: Float, dy: Float) {
        scrollAccumulatorY -= dy / cellHeightPx
        scrollAccumulatorX += dx / cellWidthPx

        while (scrollAccumulatorY >= 1f) {
            scrollAccumulatorY -= 1f
            send(
                NvimMouseButton.Wheel,
                NvimMouseAction.ScrollDown,
                touchModifiers,
                scrollRow,
                scrollCol,
            )
        }
        while (scrollAccumulatorY <= -1f) {
            scrollAccumulatorY += 1f
            send(
                NvimMouseButton.Wheel,
                NvimMouseAction.ScrollUp,
                touchModifiers,
                scrollRow,
                scrollCol,
            )
        }
        while (scrollAccumulatorX >= 1f) {
            scrollAccumulatorX -= 1f
            send(
                NvimMouseButton.Wheel,
                NvimMouseAction.ScrollLeft,
                touchModifiers,
                scrollRow,
                scrollCol,
            )
        }
        while (scrollAccumulatorX <= -1f) {
            scrollAccumulatorX += 1f
            send(
                NvimMouseButton.Wheel,
                NvimMouseAction.ScrollRight,
                touchModifiers,
                scrollRow,
                scrollCol,
            )
        }
    }

    private fun startFlingFromVelocity(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, maxFlingVelocity)
        val vx = tracker.getXVelocity(activePointerId)
        val vy = tracker.getYVelocity(activePointerId)

        if (abs(vx) < minFlingVelocity && abs(vy) < minFlingVelocity) return

        flingVelocityX = vx
        flingVelocityY = vy
        lastFlingTime = 0L
        handler.post(flingRunnable)
    }

    private fun stopFling() {
        handler.removeCallbacks(flingRunnable)
        flingVelocityX = 0f
        flingVelocityY = 0f
        lastFlingTime = 0L
    }

    private fun cancelLongPressCallback() {
        handler.removeCallbacks(longPressRunnable)
    }

    private fun resetWheelBurst() {
        wheelModifiers = ""
        lastWheelTime = 0L
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    // Hardware mouse
    private fun handleMouseTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_BUTTON_PRESS,
            -> {
                val button = buttonForActionButton(event.actionButton)
                    ?: buttonForState(event.buttonState)
                if (button != null) pressMouse(event, button)
            }

            MotionEvent.ACTION_MOVE -> {
                if (mouseButton != null) {
                    dragMouse(event)
                } else {
                    handleHover(event)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_BUTTON_RELEASE,
            -> {
                releaseMouse(event, buttonForActionButton(event.actionButton))
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseMouse(event, null)
            }

            MotionEvent.ACTION_SCROLL -> {
                handleWheel(event)
            }
        }
    }

    private fun pressMouse(event: MotionEvent, button: NvimMouseButton) {
        if (mouseButton == button) return
        if (mouseButton != null) releaseMouse(event, null)

        stopFling()
        resetWheelBurst()
        mouseButton = button
        mouseModifiers = takeModifiers()

        val cell = cellFor(event.x, event.y)
        mouseRow = cell.first
        mouseCol = cell.second
        send(button, NvimMouseAction.Press, modifiersFor(event, mouseModifiers), mouseRow, mouseCol)
    }

    private fun dragMouse(event: MotionEvent) {
        val button = mouseButton ?: return
        val cell = cellFor(event.x, event.y)
        if (cell.first == mouseRow && cell.second == mouseCol) return

        mouseRow = cell.first
        mouseCol = cell.second
        send(button, NvimMouseAction.Drag, modifiersFor(event, mouseModifiers), mouseRow, mouseCol)
    }

    private fun releaseMouse(event: MotionEvent, button: NvimMouseButton?) {
        val active = mouseButton ?: return
        if (button != null && button != active) return

        val cell = cellFor(event.x, event.y)
        send(
            active,
            NvimMouseAction.Release,
            modifiersFor(event, mouseModifiers),
            cell.first,
            cell.second,
        )
        mouseButton = null
        mouseModifiers = ""
    }

    private fun handleWheel(event: MotionEvent): Boolean {
        val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (abs(vertical) < 0.01f && abs(horizontal) < 0.01f) return true

        val now = SystemClock.uptimeMillis()
        if (now - lastWheelTime > WHEEL_BURST_TIMEOUT_MS) {
            wheelModifiers = takeModifiers()
        }
        lastWheelTime = now

        val cell = cellFor(event.x, event.y)
        val modifier = modifiersFor(event, wheelModifiers)

        if (abs(vertical) >= 0.01f) {
            val action = if (vertical > 0f) {
                NvimMouseAction.ScrollUp
            } else {
                NvimMouseAction.ScrollDown
            }
            repeat(abs(vertical).roundToInt().coerceAtLeast(1)) {
                send(NvimMouseButton.Wheel, action, modifier, cell.first, cell.second)
            }
        }

        if (abs(horizontal) >= 0.01f) {
            val action = if (horizontal > 0f) {
                NvimMouseAction.ScrollRight
            } else {
                NvimMouseAction.ScrollLeft
            }
            repeat(abs(horizontal).roundToInt().coerceAtLeast(1)) {
                send(NvimMouseButton.Wheel, action, modifier, cell.first, cell.second)
            }
        }

        return true
    }

    private fun handleHover(event: MotionEvent) {
        val cell = cellFor(event.x, event.y)
        if (cell.first == hoverRow && cell.second == hoverCol) return

        hoverRow = cell.first
        hoverCol = cell.second
        send(NvimMouseButton.Move, NvimMouseAction.None, "", hoverRow, hoverCol)
    }

    private fun buttonForState(state: Int): NvimMouseButton? = when {
        state and MotionEvent.BUTTON_PRIMARY != 0 -> NvimMouseButton.Left
        state and MotionEvent.BUTTON_SECONDARY != 0 -> NvimMouseButton.Right
        state and MotionEvent.BUTTON_TERTIARY != 0 -> NvimMouseButton.Middle
        state and MotionEvent.BUTTON_BACK != 0 -> NvimMouseButton.X1
        state and MotionEvent.BUTTON_FORWARD != 0 -> NvimMouseButton.X2
        else -> null
    }

    private fun buttonForActionButton(button: Int): NvimMouseButton? = when (button) {
        MotionEvent.BUTTON_PRIMARY -> NvimMouseButton.Left
        MotionEvent.BUTTON_SECONDARY -> NvimMouseButton.Right
        MotionEvent.BUTTON_TERTIARY -> NvimMouseButton.Middle
        MotionEvent.BUTTON_BACK -> NvimMouseButton.X1
        MotionEvent.BUTTON_FORWARD -> NvimMouseButton.X2
        else -> null
    }

    private fun send(
        button: NvimMouseButton,
        action: NvimMouseAction,
        modifier: String,
        row: Int,
        col: Int,
    ) {
        onMouse(
            NvimMouseEvent(
                button = button,
                action = action,
                modifier = modifier,
                row = row,
                col = col,
            ),
        )
    }

    private fun cellFor(x: Float, y: Float): Pair<Int, Int> {
        val col = (x / cellWidthPx).toInt().coerceIn(0, (columns - 1).coerceAtLeast(0))
        val row = (y / cellHeightPx).toInt().coerceIn(0, (rows - 1).coerceAtLeast(0))
        return row to col
    }

    private fun centroid(event: MotionEvent): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        for (index in 0 until event.pointerCount) {
            x += event.getX(index)
            y += event.getY(index)
        }
        val count = event.pointerCount.coerceAtLeast(1)
        return (x / count) to (y / count)
    }

    private fun activePosition(event: MotionEvent): Pair<Float, Float> {
        val index = event.findPointerIndex(activePointerId)
        return if (index >= 0) {
            event.getX(index) to event.getY(index)
        } else {
            val fallback = event.actionIndex.coerceIn(0, (event.pointerCount - 1).coerceAtLeast(0))
            event.getX(fallback) to event.getY(fallback)
        }
    }

    private fun modifiersFor(event: MotionEvent?, sticky: String): String {
        val modifiers = linkedSetOf<String>()
        val metaState = event?.metaState ?: 0
        if (metaState and KeyEvent.META_CTRL_ON != 0) modifiers += "C-"
        if (metaState and KeyEvent.META_ALT_ON != 0) modifiers += "A-"
        if (metaState and KeyEvent.META_SHIFT_ON != 0) modifiers += "S-"

        for (prefix in MODIFIER_PREFIXES) {
            if (sticky.contains(prefix)) modifiers += prefix
        }

        return modifiers.joinToString(separator = "")
    }

    private companion object {
        private const val WHEEL_BURST_TIMEOUT_MS = 500L
        private const val FLING_DECAY = 0.92f
        private val MODIFIER_PREFIXES = listOf("C-", "A-", "S-")
    }
}
