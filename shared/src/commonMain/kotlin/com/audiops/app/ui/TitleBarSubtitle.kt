package com.audiops.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * A `LargeFlexibleTopAppBar` subtitle that may wrap to two lines. The bar's expanded height only
 * fits one line, so a second line (narrow screens, large fonts) would spill into the content
 * below; [expandedHeight] grows the bar by the height of the extra line.
 */
@Stable
class TitleBarSubtitle {
    private var extraPx by mutableIntStateOf(0)

    @Composable
    fun Text(text: String) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = ::measured)
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun expandedHeight(): Dp =
        TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight + with(LocalDensity.current) { extraPx.toDp() }

    private fun measured(layout: TextLayoutResult) {
        val lines = layout.lineCount
        extraPx = if (lines > 1) layout.size.height * (lines - 1) / lines else 0
    }
}

@Composable
fun rememberTitleBarSubtitle(): TitleBarSubtitle = remember { TitleBarSubtitle() }
