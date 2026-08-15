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
 * One opaque screen: optional pause menu on the left, NSO bezel + framebuffer in the remaining
 * rect. The menu is a child of this view, not a Compose overlay stacked on top of the game.
 */
class XoraEmulatorStage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    val gameView: ImageView = ImageView(context).apply {
        setBackgroundColor(Color.BLACK)
        scaleType = ImageView.ScaleType.FIT_XY
        adjustViewBounds = false
        setLayerType(View.LAYER_TYPE_NONE, null)
    }
    val bezelView: NsoBezelView = NsoBezelView(context)

    private var menuView: View? = null

    var menuVisible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            menuView?.visibility = if (value) View.VISIBLE else View.GONE
            requestLayout()
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
        setLayerType(View.LAYER_TYPE_NONE, null)
        clipChildren = true
        clipToPadding = true
        addView(
            bezelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(gameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun attachMenu(view: View) {
        if (menuView === view) return
        menuView?.let { removeView(it) }
        menuView = view
        view.visibility = if (menuVisible) View.VISIBLE else View.GONE
        addView(
            view,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun setOverlayFile(file: File?) {
        bezelView.setOverlayFile(file)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val menu = menuView
        if (menuVisible && menu != null) {
            menu.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
            )
        }
        val menuW = menuWidth(w)
        val gameW = (w - menuW).coerceAtLeast(0)
        bezelView.measure(
            MeasureSpec.makeMeasureSpec(gameW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
        gameView.measure(
            MeasureSpec.makeMeasureSpec(gameW, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val menuW = menuWidth(w)
        menuView?.let { menu ->
            if (menuVisible) menu.layout(0, 0, menuW, h) else menu.layout(0, 0, 0, 0)
        }
        val gameW = (w - menuW).coerceAtLeast(0)
        val rect = computeGameRect(gameW, h)
        bezelView.visibility = if (bezelsEnabled) View.VISIBLE else View.GONE
        bezelView.layout(menuW, 0, w, h)
        bezelView.setGameRect(rect[0], rect[1], rect[2], rect[3])
        gameView.layout(
            menuW + rect[0],
            rect[1],
            menuW + rect[2],
            rect[3],
        )
    }

    private fun menuWidth(totalW: Int): Int {
        val menu = menuView ?: return 0
        if (!menuVisible) return 0
        return menu.measuredWidth.coerceIn(0, (totalW * 0.62f).toInt())
    }

    private fun computeGameRect(viewW: Int, viewH: Int): IntArray {
        if (viewW <= 0 || viewH <= 0) return intArrayOf(0, 0, viewW, viewH)
        val layoutMode =
            if (bezelsEnabled && aspectMode == XoraAspectMode.Stretch) XoraAspectMode.Core
            else aspectMode
        if (layoutMode == XoraAspectMode.Stretch) {
            return intArrayOf(0, 0, viewW, viewH)
        }
        val fw = contentWidthPx.coerceAtLeast(1).toFloat()
        val fh = contentHeightPx.coerceAtLeast(1).toFloat()
        val (gameW, gameH) = when (layoutMode) {
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
