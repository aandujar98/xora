package com.arcadia.shell.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.libretro.netplay.XoraNetplayUiState

/**
 * In-game pause menu drawn as normal Android views on the emulator stage — same layer as the
 * framebuffer, laid out to its left. Not a Compose overlay.
 */
class XoraEmulatorMenuView(context: Context) : LinearLayout(context) {

    var onAction: ((EmulatorMenuAction) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val sidebarWidth = dp(EmulatorMenuSidebarDp)
    private val panelWidth = dp(EmulatorMenuPanelDp)

    private val rootHost = LinearLayout(context)
    private val paneHost = LinearLayout(context)
    private val paneTitleView = TextView(context)
    private val messageView = TextView(context)
    private val paneColumn = LinearLayout(context)

    private var rootIndex = 0
    private var paneIndex = 0
    private var pane = EmulatorMenuPane.None
    private var rootItems: List<MenuRow> = emptyList()
    private var paneItems: List<MenuRow> = emptyList()

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Color.BLACK)
        setLayerType(LAYER_TYPE_NONE, null)
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false

        val sidebar = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(SIDEBAR)
            setPadding(0, dp(28), 0, dp(20))
            layoutParams = LayoutParams(sidebarWidth, LayoutParams.MATCH_PARENT)
        }
        sidebar.addView(
            TextView(context).apply {
                text = "XOrA EMULATOR"
                setTextColor(0x73FFFFFF)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                letterSpacing = 0.12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(22), 0, dp(16), dp(18))
            },
        )
        val rootScroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
            isFocusable = false
        }
        rootHost.orientation = VERTICAL
        rootScroll.addView(
            rootHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        sidebar.addView(rootScroll)
        messageView.apply {
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(22), dp(8), dp(16), dp(8))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = GONE
        }
        sidebar.addView(messageView)
        sidebar.addView(
            TextView(context).apply {
                text = "B back · A confirm"
                setTextColor(0x66FFFFFF)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(22), dp(8), dp(16), 0)
            },
        )
        addView(sidebar)

        paneColumn.orientation = VERTICAL
        paneColumn.setBackgroundColor(PANEL)
        paneColumn.setPadding(0, dp(48), 0, dp(20))
        paneColumn.layoutParams = LayoutParams(panelWidth, LayoutParams.MATCH_PARENT)
        paneColumn.visibility = GONE
        paneTitleView.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), 0, dp(18), dp(10))
        }
        paneColumn.addView(paneTitleView)
        val paneScroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
            isFocusable = false
        }
        paneHost.orientation = VERTICAL
        paneScroll.addView(
            paneHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        paneColumn.addView(paneScroll)
        addView(paneColumn)
    }

    fun bind(
        gameTitle: String,
        paused: Boolean,
        hardcore: Boolean,
        settings: XoraEmulatorSettings,
        saveSlots: List<EmulatorSaveSlotUi>,
        netplay: XoraNetplayUiState,
        joinAddress: String,
        message: String?,
    ) {
        rootItems = emulatorRootRows(gameTitle, paused, hardcore, settings)
        paneItems = paneRows(pane, settings, saveSlots, netplay, joinAddress, hardcore)
        rootIndex = rootIndex.coerceIn(0, (rootItems.size - 1).coerceAtLeast(0))
        paneIndex = paneIndex.coerceIn(0, (paneItems.size - 1).coerceAtLeast(0))
        rebuildRoot()
        rebuildPane()
        if (message.isNullOrBlank()) {
            messageView.visibility = GONE
        } else {
            messageView.visibility = VISIBLE
            messageView.text = message
        }
        requestLayout()
    }

    fun resetPane() {
        pane = EmulatorMenuPane.None
        paneIndex = 0
        paneColumn.visibility = GONE
        rebuildRoot()
        requestLayout()
    }

    fun moveItem(delta: Int) {
        if (pane == EmulatorMenuPane.None) {
            if (rootItems.isEmpty()) return
            rootIndex = (rootIndex + delta).mod(rootItems.size)
            rebuildRoot()
        } else if (paneItems.isNotEmpty()) {
            paneIndex = (paneIndex + delta).mod(paneItems.size)
            rebuildPane()
        }
    }

    fun moveCategory(delta: Int) {
        when {
            delta > 0 && pane == EmulatorMenuPane.None -> confirm()
            delta < 0 && pane != EmulatorMenuPane.None -> cancel()
        }
    }

    fun confirm() {
        if (pane == EmulatorMenuPane.None) activate(rootItems.getOrNull(rootIndex))
        else activate(paneItems.getOrNull(paneIndex))
    }

    fun cancel() {
        when (pane) {
            EmulatorMenuPane.Gamepad,
            EmulatorMenuPane.Graphics,
            EmulatorMenuPane.Audio,
            -> {
                pane = EmulatorMenuPane.Settings
                paneIndex = 0
                rebuildPane()
            }
            EmulatorMenuPane.None -> onDismiss?.invoke()
            else -> {
                pane = EmulatorMenuPane.None
                paneIndex = 0
                paneColumn.visibility = GONE
                rebuildRoot()
            }
        }
        requestLayout()
        (parent as? View)?.requestLayout()
    }

    private fun activate(row: MenuRow?) {
        if (row == null) return
        when {
            row.pane != null -> {
                pane = row.pane
                paneIndex = 0
                rebuildRoot()
                rebuildPane()
                requestLayout()
                (parent as? View)?.requestLayout()
            }
            row.action != null -> onAction?.invoke(row.action)
        }
    }

    private fun rebuildRoot() {
        rootHost.removeAllViews()
        rootItems.forEachIndexed { index, row ->
            rootHost.addView(
                rowView(
                    row = row,
                    selected = index == rootIndex && pane == EmulatorMenuPane.None,
                    dimmed = pane != EmulatorMenuPane.None && index != rootIndex,
                ) {
                    rootIndex = index
                    activate(row)
                },
            )
        }
    }

    private fun rebuildPane() {
        if (pane == EmulatorMenuPane.None) {
            paneColumn.visibility = GONE
            return
        }
        paneColumn.visibility = VISIBLE
        paneTitleView.text = paneTitle(pane)
        paneHost.removeAllViews()
        paneItems.forEachIndexed { index, row ->
            paneHost.addView(
                rowView(row, selected = index == paneIndex, dimmed = false) {
                    paneIndex = index
                    activate(row)
                },
            )
        }
    }

    private fun rowView(
        row: MenuRow,
        selected: Boolean,
        dimmed: Boolean,
        onClick: () -> Unit,
    ): View {
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(16), dp(10))
            setBackgroundColor(if (selected) 0x1FFFFFFF else Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { onClick() }
        }
        val bar = View(context).apply {
            layoutParams = LayoutParams(dp(3), dp(22)).apply { rightMargin = dp(12) }
            setBackgroundColor(if (selected) ACCENT else Color.TRANSPARENT)
        }
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(
            TextView(context).apply {
                text = row.title
                setTextColor(if (dimmed) 0x66FFFFFF else Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (selected) 16f else 15f)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        if (!row.subtitle.isNullOrBlank()) {
            texts.addView(
                TextView(context).apply {
                    text = row.subtitle
                    setTextColor(0x73FFFFFF)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        rowLayout.addView(bar)
        rowLayout.addView(texts)
        if (row.pane != null) {
            rowLayout.addView(
                TextView(context).apply {
                    text = "›"
                    setTextColor(0x59FFFFFF)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setPadding(dp(8), 0, 0, 0)
                },
            )
        }
        return rowLayout
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val SIDEBAR = 0xFF10131A.toInt()
        private const val PANEL = 0xFF1A1F2A.toInt()
        private const val ACCENT = 0xFF3DFFDC.toInt()
    }
}
