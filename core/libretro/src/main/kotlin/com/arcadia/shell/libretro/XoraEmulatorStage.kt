package com.arcadia.shell.libretro

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.arcadia.shell.datastore.XoraAspectMode
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Opaque gameplay stage: NSO bezel behind, framebuffer [ImageView] laid out in the game rect
 * only. The game surface is never under a translucent Compose layer.
 */
class XoraEmulatorStage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    val gameView: ImageView = ImageView(context).apply {
        setBackgroundColor(Color.BLACK)
        scaleType = ImageView.ScaleType.FIT_XY
        adjustViewBounds = false
    }
    val bezelView: NsoBezelView = NsoBezelView(context)

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
        bezelView.visibility = if (bezelsEnabled && aspectMode != XoraAspectMode.Stretch) {
            View.VISIBLE
        } else {
            View.GONE
        }
        bezelView.layout(0, 0, w, h)
        bezelView.setGameRect(rect[0], rect[1], rect[2], rect[3])
        gameView.layout(rect[0], rect[1], rect[2], rect[3])
    }

    private fun computeGameRect(viewW: Int, viewH: Int): IntArray {
        if (viewW <= 0 || viewH <= 0) return intArrayOf(0, 0, viewW, viewH)
        if (!bezelsEnabled || aspectMode == XoraAspectMode.Stretch) {
            return intArrayOf(0, 0, viewW, viewH)
        }
        val fw = contentWidthPx.coerceAtLeast(1).toFloat()
        val fh = contentHeightPx.coerceAtLeast(1).toFloat()
        val (gameW, gameH) = when (aspectMode) {
            XoraAspectMode.Stretch -> viewW.toFloat() to viewH.toFloat()
            XoraAspectMode.Core -> {
                val viewAspect = viewW / viewH.toFloat()
                val gameAspect = fw / fh
                if (gameAspect > viewAspect) {
                    viewW.toFloat() to (viewW / gameAspect)
                } else {
                    (viewH * gameAspect) to viewH.toFloat()
                }
            }
            XoraAspectMode.Integer -> {
                val fit = min(viewW / fw, viewH / fh)
                val auto = max(1, floor(fit.toDouble()).toInt())
                val scale = if (integerScaleCap > 0) min(auto, integerScaleCap) else auto
                (fw * scale) to (fh * scale)
            }
        }
        val left = ((viewW - gameW) / 2f).toInt().coerceAtLeast(0)
        val top = ((viewH - gameH) / 2f).toInt().coerceAtLeast(0)
        val right = (left + gameW).toInt().coerceAtMost(viewW)
        val bottom = (top + gameH).toInt().coerceAtMost(viewH)
        return intArrayOf(left, top, right, bottom)
    }
}
