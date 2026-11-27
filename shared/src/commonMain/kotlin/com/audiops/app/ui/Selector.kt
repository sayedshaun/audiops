package com.audiops.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.audiops.shared.resources.Res
import com.audiops.shared.resources.action_clear_search
import com.audiops.shared.resources.search_hint
import com.audiops.shared.resources.search_none
import org.jetbrains.compose.resources.stringResource

/**
 * A choice shown as a card: the subject in an expressive shape, a small [label] over the chosen
 * [value], and a chevron. Tapping it opens the choices (usually a [SelectionSheet]); without
 * [onClick] it only shows the value.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectorCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(icon, shape.toShape(), colors.primaryContainer, colors.onPrimaryContainer, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (onClick != null) {
                Spacer(Modifier.width(12.dp))
                Surface(color = colors.surfaceContainerHighest, shape = CircleShape) {
                    Icon(Icons.Rounded.UnfoldMore, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
                }
            }
        }
    }
    val cardColors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().semantics { role = Role.DropdownList },
            shape = MaterialTheme.shapes.extraLarge,
            colors = cardColors,
        ) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = cardColors) { content() }
    }
}

/**
 * Single choice in a bottom sheet: a header, a search field when the list is long, and rows where
 * the chosen one is a tonal pill with a check. Opens scrolled to the current choice.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SelectionSheet(
    title: String,
    icon: ImageVector,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    detail: @Composable (T) -> String? = { null },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = options.size > SEARCH_FROM
    // Labels are resolved in composition, so filter on them here.
    val labelled = options.map { it to label(it) }
    val shown = if (query.isBlank()) labelled else labelled.filter { (_, text) -> text.contains(query.trim(), ignoreCase = true) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOf(selected).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(icon, MaterialShapes.Cookie9Sided.toShape(), colors.primaryContainer, colors.onPrimaryContainer, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.semantics { heading() })
            }
            if (searchable) {
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(Res.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.action_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHighest,
                        unfocusedContainerColor = colors.surfaceContainerHighest,
                        focusedIndicatorColor = colors.surfaceContainerHighest,
                        unfocusedIndicatorColor = colors.surfaceContainerHighest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (shown.isEmpty()) {
            Text(
                stringResource(Res.string.search_none),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shown, key = { (option, _) -> option.hashCode() }) { (option, text) ->
                val chosen = option == selected
                val extra = detail(option)
                Surface(
                    color = if (chosen) colors.secondaryContainer else Color.Transparent,
                    contentColor = if (chosen) colors.onSecondaryContainer else colors.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = chosen, role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    },
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text,
                                style = if (chosen) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.bodyLarge,
                            )
                            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (chosen) Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/** Lists longer than this get a search field. */
private const val SEARCH_FROM = 8
