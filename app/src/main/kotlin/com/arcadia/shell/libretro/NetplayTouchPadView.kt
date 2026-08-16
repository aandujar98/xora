package com.arcadia.shell.libretro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * On-screen SNES-style pad so a joiner phone without a Bluetooth controller can still
 * drive Player 2. Not focusable — it must not steal KeyEvents from a real pad.
 */
class NetplayTouchPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onButtonsChanged: (Int) -> Unit = {}

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = 0x66FFFFFF
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 16f * resources.displayMetrics.density
        isFakeBoldText = true
    }

    private val dpad = RectF()
    private val bBtn = RectF()
    private val aBtn = RectF()
    private val yBtn = RectF()
    private val xBtn = RectF()
    private val startBtn = RectF()
    private val selectBtn = RectF()

    private var buttons = 0

    init {
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val p = h * 0.08f
        val dpadSize = h * 0.84f
        dpad.set(p, (h - dpadSize) / 2f, p + dpadSize, (h + dpadSize) / 2f)
        val face = h * 0.28f
        val right = w - p
        val midY = h / 2f
        aBtn.set(right - face, midY, right, midY + face)
        bBtn.set(right - face * 2.15f, midY, right - face * 1.15f, midY + face)
        xBtn.set(right - face, midY - face * 1.15f, right, midY - face * 0.15f)
        yBtn.set(right - face * 2.15f, midY - face * 1.15f, right - face * 1.15f, midY - face * 0.15f)
        val sysW = w * 0.16f
        val sysH = h * 0.22f
        val sysTop = h * 0.12f
        selectBtn.set(w / 2f - sysW * 1.15f, sysTop, w / 2f - sysW * 0.15f, sysTop + sysH)
        startBtn.set(w / 2f + sysW * 0.15f, sysTop, w / 2f + sysW * 1.15f, sysTop + sysH)
    }

    override fun onDraw(canvas: Canvas) {
        fill.color = 0x99000000.toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 18f, 18f, fill)
        drawCircleButton(canvas, dpad, if (buttons and DPAD_MASK != 0) 0x44FFFFFF else 0x22FFFFFF, "+")
        drawCircleButton(canvas, yBtn, lit(LibretroPad.Y), "Y")
        drawCircleButton(canvas, xBtn, lit(LibretroPad.X), "X")
        drawCircleButton(canvas, bBtn, lit(LibretroPad.B), "B")
        drawCircleButton(canvas, aBtn, lit(LibretroPad.A), "A")
        drawPill(canvas, selectBtn, lit(LibretroPad.SELECT), "SELECT")
        drawPill(canvas, startBtn, lit(LibretroPad.START), "START")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE,
            -> {
                var next = 0
                for (i in 0 until event.pointerCount) {
                    next = next or hit(event.getX(i), event.getY(i))
                }
                publish(next)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                if (event.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                    publish(0)
                } else {
                    var next = 0
                    val skip = event.actionIndex
                    for (i in 0 until event.pointerCount) {
                        if (i == skip) continue
                        next = next or hit(event.getX(i), event.getY(i))
                    }
                    publish(next)
                }
            }
        }
        return true
    }

    private fun publish(next: Int) {
        if (next == buttons) return
        buttons = next
        onButtonsChanged(next)
        invalidate()
    }

    private fun hit(x: Float, y: Float): Int {
        if (aBtn.contains(x, y)) return 1 shl LibretroPad.A
        if (bBtn.contains(x, y)) return 1 shl LibretroPad.B
        if (xBtn.contains(x, y)) return 1 shl LibretroPad.X
        if (yBtn.contains(x, y)) return 1 shl LibretroPad.Y
        if (startBtn.contains(x, y)) return 1 shl LibretroPad.START
        if (selectBtn.contains(x, y)) return 1 shl LibretroPad.SELECT
        if (!dpad.contains(x, y)) return 0
        val cx = dpad.centerX()
        val cy = dpad.centerY()
        val dx = x - cx
        val dy = y - cy
        val dead = dpad.width() * 0.12f
        if (dx * dx + dy * dy < dead * dead) return 0
        var out = 0
        if (kotlin.math.abs(dx) >= dead) {
            out = out or if (dx < 0) (1 shl LibretroPad.LEFT) else (1 shl LibretroPad.RIGHT)
        }
        if (kotlin.math.abs(dy) >= dead) {
            out = out or if (dy < 0) (1 shl LibretroPad.UP) else (1 shl LibretroPad.DOWN)
        }
        return out
    }

    private fun lit(bit: Int): Int =
        if (buttons and (1 shl bit) != 0) 0x66FFFFFF else 0x22FFFFFF

    private fun drawCircleButton(canvas: Canvas, rect: RectF, color: Int, text: String) {
        fill.color = color
        canvas.drawOval(rect, fill)
        canvas.drawOval(rect, stroke)
        canvas.drawText(text, rect.centerX(), rect.centerY() + label.textSize * 0.35f, label)
    }

    private fun drawPill(canvas: Canvas, rect: RectF, color: Int, text: String) {
        fill.color = color
        val r = rect.height() / 2f
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, stroke)
        val saved = label.textSize
        label.textSize = saved * 0.7f
        canvas.drawText(text, rect.centerX(), rect.centerY() + label.textSize * 0.35f, label)
        label.textSize = saved
    }

    companion object {
        const val DEVICE_ID: Int = -101
        private val DPAD_MASK =
            (1 shl LibretroPad.UP) or (1 shl LibretroPad.DOWN) or
                (1 shl LibretroPad.LEFT) or (1 shl LibretroPad.RIGHT)
    }
}
