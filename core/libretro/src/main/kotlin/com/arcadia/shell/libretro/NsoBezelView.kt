package com.arcadia.shell.libretro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.File
import kotlin.math.min

/**
 * Opaque NSO-style side mattes. Drawn *behind* the game [android.widget.ImageView] so nothing
 * translucent is ever composited over live pixels (that was the post-overlay white wash).
 */
class NsoBezelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val mattePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 180, 180, 190)
        style = Paint.Style.FILL
    }
    private val avatarRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val avatarFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 196, 180) }
    private val avatarText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val gameRect = Rect()
    private var overlayBitmap: Bitmap? = null
    private var avatarBitmap: Bitmap? = null
    private var avatarInitial: String = "P"
    private var showAvatar = true
    private var overlayPath: String? = null
    /** Fired when the top-left profile disc is tapped. */
    var onAvatarClick: (() -> Unit)? = null
    private val avatarRect = RectF()

    fun setGameRect(left: Int, top: Int, right: Int, bottom: Int) {
        if (gameRect.left == left && gameRect.top == top &&
            gameRect.right == right && gameRect.bottom == bottom
        ) {
            return
        }
        gameRect.set(left, top, right, bottom)
        invalidate()
    }

    fun setOverlayFile(file: File?) {
        val path = file?.takeIf { it.isFile && it.length() > 0L }?.absolutePath
        if (path == overlayPath) return
        overlayPath = path
        overlayBitmap?.takeIf { !it.isRecycled }?.recycle()
        overlayBitmap = path?.let { BitmapFactory.decodeFile(it) }
        invalidate()
    }

    fun setAvatar(bitmap: Bitmap?, initial: String, fillColor: Int) {
        avatarBitmap = bitmap?.takeIf { !it.isRecycled }
        avatarInitial = initial.trim().uppercase().take(1).ifBlank { "?" }
        avatarFill.color = fillColor
        invalidate()
    }

    fun setAvatarDrawn(drawn: Boolean) {
        if (showAvatar == drawn) return
        showAvatar = drawn
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showAvatar || avatarRect.isEmpty || onAvatarClick == null) {
            return super.onTouchEvent(event)
        }
        val inside = avatarRect.contains(event.x, event.y)
        if (event.actionMasked == MotionEvent.ACTION_UP && inside) {
            onAvatarClick?.invoke()
            performClick()
            return true
        }
        return inside || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        canvas.drawColor(Color.BLACK)
        val overlay = overlayBitmap
        if (overlay != null && !overlay.isRecycled) {
            canvas.drawBitmap(overlay, null, Rect(0, 0, w, h), overlayPaint)
        } else {
            drawHalftonePillars(canvas, w, h)
        }
        // NSO PNGs fill the LCD hole with white. Keep that rect black under the ImageView.
        if (!gameRect.isEmpty) {
            mattePaint.color = Color.BLACK
            canvas.drawRect(gameRect, mattePaint)
        }

        if (showAvatar) drawAvatar(canvas)
    }

    private fun drawHalftonePillars(canvas: Canvas, w: Int, h: Int) {
        val leftW = gameRect.left.coerceIn(0, w)
        val rightL = gameRect.right.coerceIn(0, w)
        if (leftW > 0) {
            mattePaint.color = Color.rgb(28, 28, 32)
            canvas.drawRect(0f, 0f, leftW.toFloat(), h.toFloat(), mattePaint)
            drawDots(canvas, 0, leftW, h, fromLeft = true)
        }
        if (rightL < w) {
            mattePaint.color = Color.rgb(28, 28, 32)
            canvas.drawRect(rightL.toFloat(), 0f, w.toFloat(), h.toFloat(), mattePaint)
            drawDots(canvas, rightL, w, h, fromLeft = false)
        }
        // Letterbox above/below the game, still opaque black so ImageView never sits on a hole.
        if (gameRect.top > 0) {
            mattePaint.color = Color.BLACK
            canvas.drawRect(leftW.toFloat(), 0f, rightL.toFloat(), gameRect.top.toFloat(), mattePaint)
        }
        if (gameRect.bottom in 1 until h) {
            mattePaint.color = Color.BLACK
            canvas.drawRect(
                leftW.toFloat(),
                gameRect.bottom.toFloat(),
                rightL.toFloat(),
                h.toFloat(),
                mattePaint,
            )
        }
    }

    private fun drawDots(canvas: Canvas, fromX: Int, toX: Int, h: Int, fromLeft: Boolean) {
        val density = resources.displayMetrics.density
        val step = (7f * density).coerceAtLeast(6f)
        val pillarW = (toX - fromX).toFloat().coerceAtLeast(1f)
        var y = step
        while (y < h) {
            var x = fromX + step
            while (x < toX) {
                val t = if (fromLeft) {
                    (x - fromX) / pillarW
                } else {
                    (toX - x) / pillarW
                }
                val radius = step * 0.22f * (1f - t).coerceIn(0.15f, 1f)
                if (radius > 0.6f) {
                    canvas.drawCircle(x, y, radius, dotPaint)
                }
                x += step
            }
            y += step
        }
    }

    private fun drawAvatar(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val size = min(56f * density, gameRect.left.coerceAtLeast(48) * 0.55f)
        if (size < 18f) {
            avatarRect.setEmpty()
            return
        }
        val cx = 18f * density + size / 2f
        val cy = 18f * density + size / 2f
        if (cx + size / 2f > width) {
            avatarRect.setEmpty()
            return
        }
        val rect = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        avatarRect.set(rect)
        canvas.drawOval(rect, avatarFill)
        val bmp = avatarBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.save()
            canvas.clipPath(android.graphics.Path().apply { addOval(rect, android.graphics.Path.Direction.CW) })
            canvas.drawBitmap(bmp, null, rect, overlayPaint)
            canvas.restore()
        } else {
            avatarText.textSize = size * 0.42f
            val textY = cy - (avatarText.descent() + avatarText.ascent()) / 2f
            canvas.drawText(avatarInitial, cx, textY, avatarText)
        }
        canvas.drawOval(rect, avatarRing)
    }
}
