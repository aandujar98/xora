package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.launcher.photos.DevicePhoto
import com.arcadia.shell.launcher.photos.PhotoAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Concept geometry (1920x1080 frame, node 447:1741 "HOME - PHOTOS").
private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f

// Upper-left information panel.
private const val INFO_LEFT = 79f
private const val INFO_TOP = 17f
private const val INFO_WIDTH = 468f
private const val INFO_HEIGHT = 334f
private const val INFO_RADIUS = 30f

// Large selected-photo preview.
private const val PREVIEW_LEFT = 714f
private const val PREVIEW_TOP = 11f
private const val PREVIEW_WIDTH = 976f
private const val PREVIEW_HEIGHT = 545f
private const val PREVIEW_BORDER = 5f

// Bottom thumbnail tray.
private const val TRAY_LEFT = 77f
private const val TRAY_TOP = 599f
private const val TRAY_WIDTH = 1775f
private const val TRAY_HEIGHT = 468f
private const val TRAY_RADIUS = 30f
private const val THUMB_RADIUS = 18f

// Options popup (floats over the tray; never reflows the layout underneath).
private const val OPTIONS_LEFT = 634f
private const val OPTIONS_TOP = 609f
private const val OPTIONS_WIDTH = 321f
private const val OPTIONS_HEIGHT = 234f

private val PhotoInk = Color.White
private val PhotoInkMuted = Color.White.copy(alpha = 0.72f)
private val CardEdge = Color.White.copy(alpha = 0.25f)
private val CaptionEdge = Color(0xFFFFF5F5)
private val OptionHighlight = Color(0xD9D9D9D9).copy(alpha = 0.57f)

/**
 * Media → Photos: PSP-style photo gallery over the shell wallpaper.
 *
 * Everything shown here — preview, thumbnails, dates, captions, counts — comes from the user's
 * real MediaStore library through [PhotosUiState]. Only the chrome (glass, borders, legend)
 * comes from the Figma concept.
 */
@Composable
fun XoraPhotoViewerPane(
    state: PhotosUiState,
    onCommand: (PhotoPaneCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val unit = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
        val originX = (maxWidth.value - (DESIGN_WIDTH * unit)) / 2f
        val originY = (maxHeight.value - (DESIGN_HEIGHT * unit)) / 2f

        PhotoInfoPanel(state = state, unit = unit, originX = originX, originY = originY)
        PhotoPreview(state = state, unit = unit, originX = originX, originY = originY)
        PhotoTray(state = state, unit = unit, originX = originX, originY = originY, onCommand = onCommand)

        if (state.optionsOpen) {
            PhotoOptionsPopup(
                state = state,
                unit = unit,
                originX = originX,
                originY = originY,
                onCommand = onCommand,
                modifier = Modifier.zIndex(4f),
            )
        }

        if (state.fullscreenOpen) {
            PhotoFullscreenViewer(
                state = state,
                unit = unit,
                onCommand = onCommand,
                modifier = Modifier.zIndex(6f),
            )
        }

        state.edit?.let { edit ->
            PhotoEditOverlay(
                edit = edit,
                unit = unit,
                onCommand = onCommand,
                modifier = Modifier.zIndex(8f),
            )
        }

        if (state.deleteConfirmOpen) {
            PhotoDeleteConfirm(
                state = state,
                unit = unit,
                onCommand = onCommand,
                modifier = Modifier.zIndex(10f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Upper-left information panel
// ---------------------------------------------------------------------------

@Composable
private fun PhotoInfoPanel(state: PhotosUiState, unit: Float, originX: Float, originY: Float) {
    val photo = state.focusedPhoto
    Box(
        modifier = Modifier
            .offset(x = (originX + INFO_LEFT * unit).dp, y = (originY + INFO_TOP * unit).dp)
            .size(width = (INFO_WIDTH * unit).dp, height = (INFO_HEIGHT * unit).dp)
            .xoraForegroundShadow(RoundedCornerShape((INFO_RADIUS * unit).dp))
            .liquidGlass(
                shape = RoundedCornerShape((INFO_RADIUS * unit).dp),
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
                shimmer = true,
            )
            .border(1.5.dp, CardEdge, RoundedCornerShape((INFO_RADIUS * unit).dp))
            .padding((24f * unit).dp),
    ) {
        // Album chip, top-right — real bucket name, never a hardcoded platform.
        photo?.album?.takeIf { it.isNotBlank() }?.let { album ->
            Text(
                text = album,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = XoraFonts.Title,
                    fontSize = (17f * unit).sp,
                ),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .widthIn(max = (200f * unit).dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.55f))
                    .border(1.dp, CardEdge, RoundedCornerShape(percent = 50))
                    .padding(horizontal = (16f * unit).dp, vertical = (4f * unit).dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy((10f * unit).dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((16f * unit).dp),
            ) {
                Box(
                    modifier = Modifier
                        .size((72f * unit).dp)
                        .clip(CircleShape)
                        .border(2.dp, CardEdge, CircleShape),
                ) {
                    if (photo != null) {
                        ArtworkImage(
                            path = photo.contentUri,
                            contentDescription = null,
                            fallbackText = "",
                            contentScale = ContentScale.Crop,
                            decodeMaxEdgePx = 128,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.08f)),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8f * unit).dp),
                    modifier = Modifier.padding(end = (120f * unit).dp),
                ) {
                    Text(
                        text = photo?.displayName?.substringBeforeLast('.')
                            ?: "No photo selected",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = XoraFonts.Title,
                            fontWeight = FontWeight.Medium,
                            fontSize = (19f * unit).sp,
                            lineHeight = (23f * unit).sp,
                        ),
                        color = PhotoInk,
                    )
                    if (state.focusedIsFavorite) {
                        XmbVectorIcon(
                            icon = XmbIcon.Favorite,
                            tint = Color(0xFFFFD75E),
                            size = (22f * unit).dp,
                            outlined = false,
                        )
                    }
                }
            }

            if (photo != null) {
                Text(
                    text = "Date: ${formatPhotoDate(photo.dateTakenMs)}",
                    style = photoBodyStyle(unit),
                    color = PhotoInk,
                )
                Text(
                    text = "Time: ${formatPhotoTime(photo.dateTakenMs)}",
                    style = photoBodyStyle(unit),
                    color = PhotoInk,
                )
            } else {
                Text(
                    text = "Pick a photo from the gallery below.",
                    style = photoBodyStyle(unit),
                    color = PhotoInkMuted,
                )
            }

            // Caption block — shown only when the photo actually carries one.
            val caption = photo?.caption
            if (!caption.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy((10f * unit).dp),
                ) {
                    Text(
                        text = "…",
                        style = photoBodyStyle(unit),
                        color = PhotoInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape((8f * unit).dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, CardEdge, RoundedCornerShape((8f * unit).dp))
                            .padding(horizontal = (8f * unit).dp),
                    )
                    Text(
                        text = caption,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = photoBodyStyle(unit),
                        color = PhotoInk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, CaptionEdge, RoundedCornerShape((4f * unit).dp))
                            .padding((10f * unit).dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Large preview
// ---------------------------------------------------------------------------

@Composable
private fun PhotoPreview(state: PhotosUiState, unit: Float, originX: Float, originY: Float) {
    val photo = state.focusedPhoto
    Box(
        modifier = Modifier
            .offset(x = (originX + PREVIEW_LEFT * unit).dp, y = (originY + PREVIEW_TOP * unit).dp)
            .size(width = (PREVIEW_WIDTH * unit).dp, height = (PREVIEW_HEIGHT * unit).dp)
            .background(Color.Black.copy(alpha = 0.45f))
            .border((PREVIEW_BORDER * unit).coerceAtLeast(2f).dp, Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Crossfade(targetState = photo, label = "photoPreview") { current ->
                ArtworkImage(
                    path = current.contentUri,
                    contentDescription = current.displayName,
                    fallbackText = current.displayName,
                    contentScale = ContentScale.Fit,
                    cacheInMemory = false,
                    decodeMaxEdgePx = 1280,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            XmbVectorIcon(
                icon = XmbIcon.Photo,
                tint = PhotoInkMuted,
                size = (96f * unit).dp,
                outlined = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom tray: thumbnails, counter, page dots, legend, empty / loading states
// ---------------------------------------------------------------------------

@Composable
private fun PhotoTray(
    state: PhotosUiState,
    unit: Float,
    originX: Float,
    originY: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
) {
    val trayShape = RoundedCornerShape((TRAY_RADIUS * unit).dp)
    Box(
        modifier = Modifier
            .offset(x = (originX + TRAY_LEFT * unit).dp, y = (originY + TRAY_TOP * unit).dp)
            .size(width = (TRAY_WIDTH * unit).dp, height = (TRAY_HEIGHT * unit).dp)
            .xoraForegroundShadow(trayShape)
            .liquidGlass(
                shape = trayShape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
                shimmer = true,
            )
            .border(1.5.dp, CardEdge, trayShape),
    ) {
        when {
            state.isLoading -> TrayMessage(unit) {
                CircularProgressIndicator(color = PhotoInk, modifier = Modifier.size((40f * unit).dp))
                Spacer(modifier = Modifier.height((14f * unit).dp))
                TrayText("Loading your photo library…", unit)
            }
            state.access == PhotoAccess.Denied -> TrayMessage(unit) {
                TrayTitle("Photo access needed", unit)
                TrayText(
                    "Grant XOrA access to your photo library to browse pictures here.",
                    unit,
                )
                TrayButton("Grant access", unit) { onCommand(PhotoPaneCommand.RequestAccess) }
            }
            state.loadError != null -> TrayMessage(unit) {
                TrayTitle("Couldn't open the gallery", unit)
                TrayText(state.loadError, unit)
                TrayButton("Try again", unit) { onCommand(PhotoPaneCommand.Retry) }
            }
            state.photos.isEmpty() && state.access != null -> TrayMessage(unit) {
                TrayTitle("No photos found", unit)
                TrayText(
                    if (state.access == PhotoAccess.Partial) {
                        "XOrA has limited photo access and none of the selected photos are " +
                            "available. Allow more photos or add pictures to your device."
                    } else {
                        "Add photos to your device or grant XOrA access to your photo library."
                    },
                    unit,
                )
                if (state.access == PhotoAccess.Partial) {
                    TrayButton("Choose photos", unit) { onCommand(PhotoPaneCommand.RequestAccess) }
                }
            }
            else -> PhotoThumbGrid(state = state, unit = unit, onCommand = onCommand)
        }

        // Counter, bottom-left — real numbers from the library.
        Column(modifier = Modifier.align(Alignment.BottomStart).padding((26f * unit).dp)) {
            if (state.photos.isNotEmpty()) {
                Text(
                    text = "Photo: ${state.focusedIndex + 1}/${state.photos.size}",
                    style = photoBodyStyle(unit),
                    color = PhotoInk,
                )
            }
            if (state.access == PhotoAccess.Partial && state.photos.isNotEmpty()) {
                Text(
                    text = "Limited access — showing selected photos only",
                    style = photoBodyStyle(unit, size = 12f),
                    color = PhotoInkMuted,
                )
            }
        }

        // Page indicators, bottom-center — computed from the actual photo count.
        if (state.photos.isNotEmpty() && state.pageCount > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((22f * unit).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (22f * unit).dp),
            ) {
                repeat(state.pageCount.coerceAtMost(MAX_PAGE_DOTS)) { dotIndex ->
                    val page = pageForDot(dotIndex, state.currentPage, state.pageCount)
                    val active = page == state.currentPage
                    Box(
                        modifier = Modifier
                            .size((if (active) 15f else 13f).times(unit).dp)
                            .clip(CircleShape)
                            .background(
                                if (active) PhotoInk else Color.White.copy(alpha = 0.35f),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onCommand(PhotoPaneCommand.Focus(page * PHOTO_PAGE_SIZE))
                            },
                    )
                }
            }
        }

        // Controller legend, bottom-right (PS-style glyphs from the concept).
        Row(
            horizontalArrangement = Arrangement.spacedBy((24f * unit).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding((26f * unit).dp),
        ) {
            LegendEntry("✕", "View", unit) {
                onCommand(PhotoPaneCommand.Open(state.focusedIndex))
            }
            LegendEntry("□", "Options", unit) { onCommand(PhotoPaneCommand.OpenOptions) }
            LegendEntry("△", "Slideshow", unit) { onCommand(PhotoPaneCommand.StartSlideshow) }
            LegendEntry("○", "Back", unit) { onCommand(PhotoPaneCommand.Back) }
        }
    }
}

@Composable
private fun PhotoThumbGrid(
    state: PhotosUiState,
    unit: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
) {
    Crossfade(
        targetState = state.currentPage,
        label = "photoPage",
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = (26f * unit).dp,
                end = (26f * unit).dp,
                top = (24f * unit).dp,
                bottom = (68f * unit).dp,
            ),
    ) { page ->
        val pageStart = page * PHOTO_PAGE_SIZE
        val pagePhotos = state.photos.drop(pageStart).take(PHOTO_PAGE_SIZE)
        LazyVerticalGrid(
            columns = GridCells.Fixed(PHOTO_GRID_COLUMNS),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy((18f * unit).dp),
            verticalArrangement = Arrangement.spacedBy((20f * unit).dp),
        ) {
            itemsIndexed(pagePhotos, key = { _, photo -> photo.id }) { localIndex, photo ->
                val index = pageStart + localIndex
                val focused = index == state.focusedIndex
                Box(
                    modifier = Modifier
                        .aspectRatio(THUMB_ASPECT)
                        .clip(RoundedCornerShape((THUMB_RADIUS * unit).dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .then(
                            if (focused) {
                                Modifier.border(
                                    (4f * unit).coerceAtLeast(2f).dp,
                                    Color.White,
                                    RoundedCornerShape((THUMB_RADIUS * unit).dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button,
                        ) {
                            if (focused) {
                                onCommand(PhotoPaneCommand.Open(index))
                            } else {
                                onCommand(PhotoPaneCommand.Focus(index))
                            }
                        },
                ) {
                    ArtworkImage(
                        path = photo.contentUri,
                        contentDescription = photo.displayName,
                        fallbackText = photo.displayName,
                        contentScale = ContentScale.Crop,
                        decodeMaxEdgePx = 384,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (photo.id in state.favoriteIds) {
                        XmbVectorIcon(
                            icon = XmbIcon.Favorite,
                            tint = Color(0xFFFFD75E),
                            size = (20f * unit).dp,
                            outlined = false,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding((8f * unit).dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Options popup
// ---------------------------------------------------------------------------

@Composable
private fun PhotoOptionsPopup(
    state: PhotosUiState,
    unit: Float,
    originX: Float,
    originY: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((TRAY_RADIUS * unit).dp)
    Column(
        verticalArrangement = Arrangement.spacedBy((2f * unit).dp),
        modifier = modifier
            .offset(x = (originX + OPTIONS_LEFT * unit).dp, y = (originY + OPTIONS_TOP * unit).dp)
            .width((OPTIONS_WIDTH * unit).dp)
            .heightIn(min = (OPTIONS_HEIGHT * unit).dp)
            .xoraForegroundShadow(shape)
            .liquidGlass(
                shape = shape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Strong,
            )
            .border(1.5.dp, CardEdge, shape)
            .padding((12f * unit).dp),
    ) {
        PhotoOption.entries.forEachIndexed { index, option ->
            val focused = index == state.optionIndex
            val enabled = option != PhotoOption.ShareToNetwork
            val label = when {
                option == PhotoOption.MarkFavorite && state.focusedIsFavorite ->
                    "Remove from Favorites"
                option == PhotoOption.View -> "View Photo"
                option == PhotoOption.Edit -> "Edit Photo"
                else -> option.label
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape((16f * unit).dp))
                    .then(
                        if (focused) {
                            Modifier.background(OptionHighlight)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                    ) {
                        onCommand(PhotoPaneCommand.FocusOption(index))
                        onCommand(PhotoPaneCommand.ActivateOption(index))
                    }
                    .padding(horizontal = (10f * unit).dp, vertical = (6f * unit).dp)
                    .alpha(if (enabled) 1f else 0.45f),
            ) {
                Text(
                    text = label,
                    style = photoBodyStyle(unit),
                    color = PhotoInk,
                )
                if (!enabled) {
                    Text(
                        text = "Coming Soon",
                        style = photoBodyStyle(unit, size = 11f),
                        color = PhotoInkMuted,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fullscreen viewer (View action + slideshow)
// ---------------------------------------------------------------------------

@Composable
private fun PhotoFullscreenViewer(
    state: PhotosUiState,
    unit: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.focusedPhoto ?: return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCommand(PhotoPaneCommand.RevealControls) },
    ) {
        Crossfade(targetState = photo, label = "fullscreenPhoto") { current ->
            ArtworkImage(
                path = current.contentUri,
                contentDescription = current.displayName,
                fallbackText = current.displayName,
                contentScale = ContentScale.Fit,
                cacheInMemory = false,
                decodeMaxEdgePx = 1920,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = state.fullscreenControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Header: name + real date, counter.
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = (28f * unit).dp, vertical = (14f * unit).dp),
                ) {
                    Column {
                        Text(
                            text = photo.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = photoBodyStyle(unit),
                            color = PhotoInk,
                        )
                        Text(
                            text = "${formatPhotoDate(photo.dateTakenMs)} · " +
                                formatPhotoTime(photo.dateTakenMs),
                            style = photoBodyStyle(unit, size = 13f),
                            color = PhotoInkMuted,
                        )
                    }
                    Text(
                        text = "${state.focusedIndex + 1}/${state.photos.size}" +
                            if (state.slideshowActive) "  ·  Slideshow" else "",
                        style = photoBodyStyle(unit),
                        color = PhotoInk,
                    )
                }

                // Prev / next tap zones with chevrons.
                ViewerNavChevron(
                    label = "‹",
                    unit = unit,
                    alignment = Alignment.CenterStart,
                ) { onCommand(PhotoPaneCommand.PreviousPhoto) }
                ViewerNavChevron(
                    label = "›",
                    unit = unit,
                    alignment = Alignment.CenterEnd,
                ) { onCommand(PhotoPaneCommand.NextPhoto) }

                Row(
                    horizontalArrangement = Arrangement.spacedBy((24f * unit).dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding((26f * unit).dp),
                ) {
                    LegendEntry("◁ ▷", "Prev / Next", unit) {}
                    LegendEntry("△", "Slideshow", unit) {
                        onCommand(PhotoPaneCommand.StartSlideshow)
                    }
                    LegendEntry("○", "Back", unit) { onCommand(PhotoPaneCommand.CloseViewer) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ViewerNavChevron(
    label: String,
    unit: Float,
    alignment: Alignment,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.displayMedium.copy(fontSize = (56f * unit).sp),
        color = PhotoInk.copy(alpha = 0.8f),
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = (18f * unit).dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = (18f * unit).dp, vertical = (8f * unit).dp),
    )
}

// ---------------------------------------------------------------------------
// Edit overlay (non-destructive; nothing is written until Save)
// ---------------------------------------------------------------------------

@Composable
private fun PhotoEditOverlay(
    edit: PhotoEditUiState,
    unit: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding((24f * unit).dp),
    ) {
        Text(
            text = "Edit Photo",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = XoraFonts.Title,
                fontSize = (26f * unit).sp,
            ),
            color = PhotoInk,
        )
        Text(
            text = "Saving writes an edited copy to Pictures/XOrA — the original is untouched.",
            style = photoBodyStyle(unit, size = 13f),
            color = PhotoInkMuted,
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = (18f * unit).dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(0.86f),
            ) {
                ArtworkImage(
                    path = edit.photo.contentUri,
                    contentDescription = edit.photo.displayName,
                    fallbackText = edit.photo.displayName,
                    contentScale = ContentScale.Fit,
                    cacheInMemory = false,
                    decodeMaxEdgePx = 1280,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(edit.rotationDeg.toFloat()),
                )
                // Crop preview frame: the saved copy is center-cropped to this aspect.
                edit.cropAspect?.let { aspect ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .aspectRatio(aspect)
                            .border(2.dp, Color.White, RoundedCornerShape(2.dp)),
                    )
                }
            }
            if (edit.saving) {
                CircularProgressIndicator(color = PhotoInk)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy((14f * unit).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoEditTool.entries.forEachIndexed { index, tool ->
                val focused = index == edit.toolIndex
                val label = if (tool == PhotoEditTool.Crop) {
                    "Crop: ${edit.cropLabel}"
                } else {
                    tool.label
                }
                Text(
                    text = label,
                    style = photoBodyStyle(unit),
                    color = if (focused) Color.Black else PhotoInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (focused) Color.White else Color.White.copy(alpha = 0.14f),
                        )
                        .border(
                            1.dp,
                            if (focused) Color.White else CardEdge,
                            RoundedCornerShape(percent = 50),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            role = Role.Button,
                        ) {
                            onCommand(PhotoPaneCommand.FocusEditTool(index))
                            onCommand(PhotoPaneCommand.ActivateEditTool(index))
                        }
                        .padding(horizontal = (18f * unit).dp, vertical = (8f * unit).dp),
                )
            }
        }
        Text(
            text = "L/R move · A apply · B cancel",
            style = photoBodyStyle(unit, size = 12f),
            color = PhotoInkMuted,
            modifier = Modifier.padding(top = (12f * unit).dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Delete confirmation
// ---------------------------------------------------------------------------

@Composable
private fun PhotoDeleteConfirm(
    state: PhotosUiState,
    unit: Float,
    onCommand: (PhotoPaneCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape((24f * unit).dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((14f * unit).dp),
            modifier = Modifier
                .width((520f * unit).dp)
                .xoraForegroundShadow(shape)
                .liquidGlass(shape = shape, tone = GlassTone.OverMedia, intensity = GlassIntensity.Strong)
                .border(1.5.dp, CardEdge, shape)
                .padding((28f * unit).dp),
        ) {
            Text(
                text = "Delete this photo?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = XoraFonts.Title,
                    fontSize = (24f * unit).sp,
                ),
                color = PhotoInk,
            )
            Text(
                text = "This action cannot be undone.",
                style = photoBodyStyle(unit),
                color = PhotoInkMuted,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy((18f * unit).dp)) {
                DialogButton(
                    label = "Cancel",
                    focused = !state.deleteConfirmDeleteFocused,
                    unit = unit,
                ) { onCommand(PhotoPaneCommand.CancelDelete) }
                DialogButton(
                    label = "Delete",
                    focused = state.deleteConfirmDeleteFocused,
                    unit = unit,
                    destructive = true,
                ) { onCommand(PhotoPaneCommand.ConfirmDelete) }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    focused: Boolean,
    unit: Float,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val fill = when {
        focused && destructive -> Color(0xFFC94B4B)
        focused -> Color.White
        else -> Color.White.copy(alpha = 0.14f)
    }
    val ink = when {
        focused && destructive -> Color.White
        focused -> Color.Black
        else -> PhotoInk
    }
    Text(
        text = label,
        style = photoBodyStyle(unit),
        color = ink,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .border(1.dp, if (focused) Color.White else CardEdge, RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = (30f * unit).dp, vertical = (10f * unit).dp),
    )
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

@Composable
private fun TrayMessage(unit: Float, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((12f * unit).dp),
        modifier = Modifier
            .fillMaxSize()
            .padding((40f * unit).dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        content()
        Spacer(modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun TrayTitle(text: String, unit: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = XoraFonts.Title,
            fontSize = (26f * unit).sp,
        ),
        color = PhotoInk,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TrayText(text: String, unit: Float) {
    Text(
        text = text,
        style = photoBodyStyle(unit),
        color = PhotoInkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = (760f * unit).dp),
    )
}

@Composable
private fun TrayButton(label: String, unit: Float, onClick: () -> Unit) {
    Text(
        text = label,
        style = photoBodyStyle(unit),
        color = Color.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = (26f * unit).dp, vertical = (10f * unit).dp),
    )
}

@Composable
private fun LegendEntry(glyph: String, label: String, unit: Float, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((7f * unit).dp),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = (6f * unit).dp, vertical = (2f * unit).dp),
    ) {
        Text(text = glyph, style = photoBodyStyle(unit), color = PhotoInk)
        Text(text = label, style = photoBodyStyle(unit), color = PhotoInk)
    }
}

@Composable
private fun photoBodyStyle(unit: Float, size: Float = 15f) =
    MaterialTheme.typography.bodyMedium.copy(
        fontFamily = XoraFonts.Secondary,
        fontSize = (size * unit).sp,
        lineHeight = ((size + 6f) * unit).sp,
    )

private fun formatPhotoDate(epochMs: Long): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

private fun formatPhotoTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

/**
 * When more pages exist than dots, slide the dot window around the current page so the highlight
 * always maps to a real page.
 */
private const val MAX_PAGE_DOTS = 8

private fun pageForDot(dotIndex: Int, currentPage: Int, pageCount: Int): Int {
    if (pageCount <= MAX_PAGE_DOTS) return dotIndex
    val windowStart = (currentPage - MAX_PAGE_DOTS / 2)
        .coerceIn(0, pageCount - MAX_PAGE_DOTS)
    return windowStart + dotIndex
}

private const val THUMB_ASPECT = 327f / 171f

// ---------------------------------------------------------------------------
// Preview — neutral generated sample data only. Never bundled into production
// behavior and never sourced from the Figma concept's game screenshots.
// ---------------------------------------------------------------------------

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun XoraPhotoViewerPanePreview() {
    val sample = PhotosUiState(
        photos = List(17) { index ->
            DevicePhoto(
                id = "sample-$index",
                contentUri = "",
                displayName = "Sample photo ${index + 1}.jpg",
                dateTakenMs = 1_755_000_000_000L + index * 86_400_000L,
                width = 1920,
                height = 1080,
                mimeType = "image/jpeg",
                album = "Sample album",
                caption = if (index == 3) "Sample caption" else null,
            )
        },
        focusedIndex = 3,
        access = PhotoAccess.Full,
        favoriteIds = setOf("sample-3"),
    )
    ArcadiaTheme {
        XoraPhotoViewerPane(state = sample, onCommand = {})
    }
}
