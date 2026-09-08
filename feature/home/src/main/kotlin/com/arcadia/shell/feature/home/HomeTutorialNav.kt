package com.arcadia.shell.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.input.NavAction
import kotlin.math.max
import kotlin.math.min

/** First-run Home coach-mark steps, after onboarding and the boot clip. */
enum class HomeTutorialStep {
    Profile,
    Social,
    Xmb,
    Shortcuts,
}

data class HomeTutorialUiState(
    val open: Boolean = false,
    val step: HomeTutorialStep = HomeTutorialStep.Profile,
)

enum class HomeTutorialCommand {
    Next,
    Skip,
}

data class HomeTutorialHole(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
    val corner: Dp,
    val oval: Boolean,
)

fun HomeTutorialStep.nextOrNull(): HomeTutorialStep? = when (this) {
    HomeTutorialStep.Profile -> HomeTutorialStep.Social
    HomeTutorialStep.Social -> HomeTutorialStep.Xmb
    HomeTutorialStep.Xmb -> HomeTutorialStep.Shortcuts
    HomeTutorialStep.Shortcuts -> null
}

fun HomeTutorialStep.isLast(): Boolean = nextOrNull() == null

fun HomeTutorialStep.title(): String = when (this) {
    HomeTutorialStep.Profile -> "Your profile"
    HomeTutorialStep.Social -> "Your social card"
    HomeTutorialStep.Xmb -> "The XMB"
    HomeTutorialStep.Shortcuts -> "Shortcuts"
}

fun HomeTutorialStep.body(): String = when (this) {
    HomeTutorialStep.Profile ->
        "To open the profile card, press RT or tap the profile bubble."
    HomeTutorialStep.Social ->
        "To open the social card, press LT or tap the capsule."
    HomeTutorialStep.Xmb ->
        "To navigate the XMB, use the joystick or swipe with your finger."
    HomeTutorialStep.Shortcuts ->
        "To open shortcuts, press Y. Swipe up or down with two fingers to open and close them."
}

/**
 * A / Right / RB advance. B / Left / LB skip the rest. Everything else is swallowed
 * so LT / RT / Y cannot open chrome underneath the coach marks.
 */
fun homeTutorialPadCommand(action: NavAction): HomeTutorialCommand? = when (action) {
    NavAction.Confirm,
    NavAction.Right,
    NavAction.NextPlatform,
    -> HomeTutorialCommand.Next
    NavAction.Cancel,
    NavAction.Left,
    NavAction.PreviousPlatform,
    -> HomeTutorialCommand.Skip
    else -> null
}

/**
 * Spotlight in the Home pane’s coordinate space (same box as the LT / RT pills and
 * the contain-fit 1920×1080 XMB canvas).
 */
fun homeTutorialHole(step: HomeTutorialStep, viewportW: Dp, viewportH: Dp): HomeTutorialHole {
    val vw = viewportW.value
    val vh = viewportH.value
    return when (step) {
        HomeTutorialStep.Profile -> profileHoleDesign(vw).toDpHole()
        HomeTutorialStep.Social -> socialHoleDesign().toDpHole()
        HomeTutorialStep.Xmb -> mapDesignHole(xmbTutorialHoleDesign(), vw, vh)
        HomeTutorialStep.Shortcuts -> mapDesignHole(shortcutsTutorialHoleDesign(), vw, vh)
    }
}

/**
 * Design-space hole covering the active category tab and the focused recents plate
 * (Make centers: tab 430×282, plate top 420.5 / 462×248).
 */
internal fun xmbTutorialHoleDesign(): FloatHole {
    val tabHalfW = (TAB_BOX_W * TAB_ACTIVE_SCALE) / 2f
    val tabHalfH = (TAB_BOX_H * TAB_ACTIVE_SCALE) / 2f
    val plateLeft = TAB_CENTER_X - PLATE_W_FOCUS / 2f
    val left = min(TAB_CENTER_X - tabHalfW, plateLeft) - HOLE_BLEED
    val top = (TAB_CENTER_Y - tabHalfH) - HOLE_BLEED
    val right = max(TAB_CENTER_X + tabHalfW, plateLeft + PLATE_W_FOCUS) + HOLE_BLEED
    val bottom = ITEM_FOCUS_TOP + PLATE_H_FOCUS + HOLE_BLEED
    return FloatHole(left, top, right - left, bottom - top, corner = 28f, oval = false)
}

internal fun shortcutsTutorialHoleDesign(): FloatHole =
    FloatHole(
        left = 560f,
        top = 70f,
        width = 800f,
        height = 540f,
        corner = 40f,
        oval = false,
    )

internal data class FloatHole(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val corner: Float,
    val oval: Boolean,
)

internal fun profileHoleDesign(viewportW: Float): FloatHole {
    val diameter = PROFILE_AVATAR + PROFILE_HOLE_PAD * 2f
    val left = viewportW - PROFILE_PAD_END + PROFILE_BLEED - PROFILE_AVATAR - PROFILE_HOLE_PAD
    val top = PROFILE_PAD_TOP - PROFILE_BLEED - PROFILE_HOLE_PAD
    return FloatHole(
        left = left,
        top = top,
        width = diameter,
        height = diameter,
        corner = diameter / 2f,
        oval = true,
    )
}

internal fun socialHoleDesign(): FloatHole =
    FloatHole(
        left = SOCIAL_PAD_START - SOCIAL_HOLE_INSET,
        top = SOCIAL_PAD_TOP - SOCIAL_HOLE_INSET,
        width = SOCIAL_HOLE_W,
        height = SOCIAL_HOLE_H,
        corner = SOCIAL_HOLE_H / 2f,
        oval = false,
    )

private fun FloatHole.toDpHole(): HomeTutorialHole =
    HomeTutorialHole(
        left = left.dp,
        top = top.dp,
        width = width.dp,
        height = height.dp,
        corner = corner.dp,
        oval = oval,
    )

private fun mapDesignHole(hole: FloatHole, viewportW: Float, viewportH: Float): HomeTutorialHole {
    val scale = min(viewportW / XORA_DESIGN_WIDTH, viewportH / XORA_DESIGN_HEIGHT)
    val originX = (viewportW - XORA_DESIGN_WIDTH * scale) / 2f
    val originY = (viewportH - XORA_DESIGN_HEIGHT * scale) / 2f
    return HomeTutorialHole(
        left = (originX + hole.left * scale).dp,
        top = (originY + hole.top * scale).dp,
        width = (hole.width * scale).dp,
        height = (hole.height * scale).dp,
        corner = (hole.corner * scale).dp,
        oval = hole.oval,
    )
}

private const val TAB_CENTER_X = 430f
private const val TAB_CENTER_Y = 282f
private const val TAB_BOX_W = 178f
private const val TAB_BOX_H = 106f
private const val TAB_ACTIVE_SCALE = 1.25f
private const val ITEM_FOCUS_TOP = 420.5f
private const val PLATE_W_FOCUS = 462f
private const val PLATE_H_FOCUS = 248f
private const val HOLE_BLEED = 18f

/** Matches [com.arcadia.shell.feature.home.component] SystemPill + XoraHomeXmbPane padding. */
private const val PROFILE_PAD_END = 16f
private const val PROFILE_PAD_TOP = 12f
private const val PROFILE_AVATAR = 105.6f
private const val PROFILE_BLEED = 24f
private const val PROFILE_HOLE_PAD = 14f

private const val SOCIAL_PAD_START = 20f
private const val SOCIAL_PAD_TOP = 21f
private const val SOCIAL_HOLE_INSET = 8f
private const val SOCIAL_HOLE_W = 196f
private const val SOCIAL_HOLE_H = 76f
