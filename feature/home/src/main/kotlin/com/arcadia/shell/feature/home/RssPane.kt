package com.arcadia.shell.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.arcadia.shell.datastore.NewsOutlet
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraSettingsPanelRule
import com.arcadia.shell.designsystem.XoraSheetScrim
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.xoraFocusHighlight
import com.arcadia.shell.designsystem.xoraSettingsPanelSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(10.dp)
private val BubbleSize = 58.dp
private val FocusRing = Color(0xFF8ED6FF)

/**
 * XOrA NOW: source bubbles across the header, that source's stories as a grid of cards beneath.
 *
 * The grid width lives in [NEWS_GRID_COLUMNS] because the pad model steps by whole rows — if the
 * two disagreed, Down would land somewhere the player is not looking.
 */
@Composable
fun RssPane(
    state: HomeUiState,
    onSelectItem: (Int) -> Unit,
    onOpenItem: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectOutlet: (Int) -> Unit = {},
    onAddOutlet: () -> Unit = {},
    onDismissAddOutlet: () -> Unit = {},
    onSubmitOutlet: (name: String, url: String) -> Unit = { _, _ -> },
    onCloseArticle: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {},
) {
    val rss = state.rss
    val gridState = rememberLazyGridState()
    val bubbleState = rememberLazyListState()

    LaunchedEffect(rss.selectedIndex, rss.items.size, rss.focus) {
        if (rss.items.isEmpty() || rss.focus != NewsFocus.Articles) return@LaunchedEffect
        gridState.scrollCardIntoView(rss.selectedIndex)
    }
    LaunchedEffect(rss.outletIndex, rss.focus) {
        if (rss.focus == NewsFocus.Outlets) {
            bubbleState.animateScrollToItem(rss.outletIndex.coerceAtLeast(0))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .xoraSettingsPanelSurface(),
        ) {
            NewsHeader(
                outlets = rss.outlets,
                outletIndex = rss.outletIndex,
                outletsFocused = rss.focus == NewsFocus.Outlets,
                listState = bubbleState,
                onSelectOutlet = onSelectOutlet,
                onAddOutlet = onAddOutlet,
            )
            XoraSettingsPanelRule(modifier = Modifier.padding(top = 10.dp))

            Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                when {
                    rss.isLoading && rss.items.isEmpty() -> NewsMessage {
                        CircularProgressIndicator()
                    }

                    rss.error != null && rss.items.isEmpty() -> NewsMessage {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            XoraSecondaryText(
                                text = rss.error,
                                fontSize = 15.sp,
                                fillColor = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onRetry) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }

                    rss.items.isEmpty() -> NewsMessage {
                        XoraSecondaryText(
                            text = "Nothing from ${rss.outlet?.name ?: "this source"} yet.",
                            fontSize = 15.sp,
                            fillColor = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(NEWS_GRID_COLUMNS),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(count = rss.items.size, key = { rss.items[it].id }) { index ->
                            ArticleCard(
                                item = rss.items[index],
                                focused = index == rss.selectedIndex &&
                                    rss.focus == NewsFocus.Articles,
                                onClick = {
                                    onSelectItem(index)
                                    onOpenItem(index)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (rss.openArticle != null) {
            ArticleReader(
                item = rss.openArticle,
                onClose = onCloseArticle,
                onOpenInBrowser = onOpenInBrowser,
            )
        }
        if (rss.addOutletOpen) {
            AddOutletPrompt(
                onDismiss = onDismissAddOutlet,
                onSubmit = onSubmitOutlet,
            )
        }
    }
}

@Composable
private fun NewsHeader(
    outlets: List<NewsOutlet>,
    outletIndex: Int,
    outletsFocused: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectOutlet: (Int) -> Unit,
    onAddOutlet: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.padding(end = 20.dp)) {
            Image(
                painter = painterResource(id = R.drawable.ic_xora_logo),
                contentDescription = "XOrA",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(30.dp),
            )
            XoraTitleText(
                text = "NOW",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 2.dp),
            )
            XoraSecondaryText(
                text = remember { todayLabel() },
                fontSize = 13.sp,
                fillColor = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.weight(1f),
        ) {
            items(count = outlets.size, key = { outlets[it].id }) { index ->
                OutletBubble(
                    label = outlets[index].name,
                    iconUrl = outlets[index].iconUrl,
                    selected = index == outletIndex,
                    focused = outletsFocused && index == outletIndex,
                    onClick = { onSelectOutlet(index) },
                )
            }
            item(key = "__add_outlet") {
                OutletBubble(
                    label = "Add",
                    iconUrl = null,
                    selected = false,
                    focused = outletsFocused && outletIndex >= outlets.size,
                    isAdd = true,
                    onClick = onAddOutlet,
                )
            }
        }
    }
}

/**
 * Source bubble. Same circular plate as a Vita shortcut, including the "+" slot, so adding a
 * source reads the same as adding a bubble to the tray.
 */
@Composable
private fun OutletBubble(
    label: String,
    iconUrl: String?,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    isAdd: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(BubbleSize + 24.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(BubbleSize)
                .clip(CircleShape)
                .background(
                    if (isAdd) Color(0x40FFFFFF) else Color.White.copy(alpha = 0.14f),
                    CircleShape,
                )
                .border(
                    width = if (focused) 3.dp else 1.5.dp,
                    color = when {
                        focused -> FocusRing
                        selected -> Color.White.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isAdd -> Text(
                    text = "+",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                !iconUrl.isNullOrBlank() -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .crossfade(120)
                        .build(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                else -> XoraTitleText(
                    text = label.take(2).uppercase(Locale.US),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        XoraSecondaryText(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fillColor = when {
                focused -> FocusRing
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.65f)
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun ArticleCard(
    item: RssFeedItem,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(CardShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFD8D9E6), Color(0xFFB9C2D2)),
                    ),
                    CardShape,
                )
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) FocusRing else Color.White.copy(alpha = 0.55f),
                    shape = CardShape,
                ),
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(140)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CardShape),
                )
            }
        }
        XoraSecondaryText(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            fillColor = if (focused) FocusRing else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

/**
 * Full story in-shell: hero image, then the feed's own paragraphs and images in order.
 *
 * Feeds that only ship a teaser get the teaser plus a way out to the browser, rather than a
 * blank page pretending there was nothing to read.
 */
@Composable
private fun ArticleReader(
    item: RssFeedItem,
    onClose: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        XoraSheetScrim(visible = true, onClick = onClose)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.88f)
                .xoraSettingsPanelSurface()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            XoraTitleText(
                text = item.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                XoraSecondaryText(
                    text = item.source,
                    fontSize = 12.sp,
                    fillColor = Color.White.copy(alpha = 0.7f),
                )
                item.publishedAt?.let {
                    XoraSecondaryText(
                        text = it,
                        fontSize = 12.sp,
                        fillColor = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            XoraSettingsPanelRule(modifier = Modifier.padding(top = 8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .crossfade(140)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                val body = item.blocks.ifEmpty {
                    item.description?.let { listOf(ArticleBlock.Text(it)) }.orEmpty()
                }
                if (body.isEmpty()) {
                    XoraSecondaryText(
                        text = "This source only publishes headlines. Open it in the browser " +
                            "to read the full story.",
                        fontSize = 14.sp,
                        fillColor = Color.White.copy(alpha = 0.65f),
                    )
                }
                body.forEach { block ->
                    when (block) {
                        is ArticleBlock.Text -> XoraSecondaryText(
                            text = block.text,
                            fontSize = 15.sp,
                            fillColor = Color.White.copy(alpha = 0.92f),
                        )
                        is ArticleBlock.Image -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(block.url)
                                .crossfade(140)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                TextButton(onClick = onOpenInBrowser) {
                    Text("Open in browser", color = Color.White)
                }
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AddOutletPrompt(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize()) {
        XoraSheetScrim(visible = true, onClick = onDismiss)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.7f)
                .xoraSettingsPanelSurface()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            XoraTitleText(
                text = "ADD NEWS SOURCE",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            XoraSettingsPanelRule()
            XoraSecondaryText(
                text = "Paste the RSS feed address for any site.",
                fontSize = 13.sp,
                fillColor = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 6.dp),
            )
            OutletField(value = name, placeholder = "Name (optional)") { name = it }
            OutletField(value = url, placeholder = "https://example.com/feed") { url = it }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                TextButton(onClick = { onSubmit(name, url) }) {
                    Text("Add", color = Color.White)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun OutletField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun NewsMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

private fun todayLabel(): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

/** Reveal the focused card without snapping the grid to the top on every sideways step. */
private suspend fun LazyGridState.scrollCardIntoView(index: Int) {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index)
        return
    }
    val top = item.offset.y
    val bottom = top + item.size.height
    val viewportTop = layoutInfo.viewportStartOffset
    val viewportBottom = layoutInfo.viewportEndOffset
    when {
        top < viewportTop -> animateScrollBy((top - viewportTop).toFloat())
        bottom > viewportBottom -> animateScrollBy((bottom - viewportBottom).toFloat())
    }
}
