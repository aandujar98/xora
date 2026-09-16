package com.arcadia.shell.libretro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.arcadia.shell.datastore.XoraAspectMode
import java.io.File

/**
 * Opaque gameplay stage: NSO bezel behind, framebuffer [ImageView] laid out in the game rect
 * only. The game surface is never under a translucent Compose layer.
 */
class XoraEmulatorStage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    val gameView: ImageView = OpaqueGameImageView(context).apply {
        setBackgroundColor(Color.BLACK)
        scaleType = ImageView.ScaleType.FIT_XY
        adjustViewBounds = false
        isFocusable = false
        isFocusableInTouchMode = false
        isForceDarkAllowed = false
    }
    val bezelView: NsoBezelView = NsoBezelView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        isForceDarkAllowed = false
    }

    var contentWidthPx: Int = 4
        set(value) {
            if (field != value) {
                field = value.coerceAtLeast(1)
                requestLayout()
            }
        }
    var contentHeightPx: Int = 3
        set(value) {
            if (field != value) {
                field = value.coerceAtLeast(1)
                requestLayout()
            }
        }
    var aspectMode: XoraAspectMode = XoraAspectMode.Core
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var integerScaleCap: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var bezelsEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        setBackgroundColor(Color.BLACK)
        isForceDarkAllowed = false
        addView(
            bezelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(gameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setOverlayFile(file: File?) {
        bezelView.setOverlayFile(file)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val rect = computeGameRect(w, h)
        bezelView.visibility = if (bezelsEnabled) View.VISIBLE else View.GONE
        bezelView.layout(0, 0, w, h)
        bezelView.setGameRect(rect[0], rect[1], rect[2], rect[3])
        gameView.layout(rect[0], rect[1], rect[2], rect[3])
    }

    private fun computeGameRect(viewW: Int, viewH: Int): IntArray =
        computeXoraGameRect(
            viewW = viewW,
            viewH = viewH,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            aspectMode = aspectMode,
            integerScaleCap = integerScaleCap,
            bezelsEnabled = bezelsEnabled,
        )
}

/**
 * HWUI treats a default ARGB ImageView as non-opaque even when every pixel is 0xFF000000, and
 * then blends it with whatever is behind — the NSO overlay's white LCD hole. SRC + isOpaque
 * makes black stay black.
 */
private class OpaqueGameImageView(context: Context) : ImageView(context) {
    override fun isOpaque(): Boolean = true

    override fun hasOverlappingRendering(): Boolean = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        super.onDraw(canvas)
    }
}
