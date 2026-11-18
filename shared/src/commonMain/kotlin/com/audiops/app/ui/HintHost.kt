package com.audiops.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** The hint currently shown by [HintHost], if any. */
class HintController {
    var text: String? by mutableStateOf(null)
}

private val LocalHintController = staticCompositionLocalOf { HintController() }

/**
 * Hosts long-press hints: while one is open the app behind it is blurred and a small window
 * with the hint text is shown in the middle of the screen. Tap outside or press Back to close.
 */
@Composable
fun HintHost(content: @Composable () -> Unit) {
    val controller = remember { HintController() }
    val text = controller.text
    val blur by animateDpAsState(if (text != null) 16.dp else 0.dp, MaterialTheme.motionScheme.defaultEffectsSpec())
    CompositionLocalProvider(LocalHintController provides controller) {
        Box(Modifier.fillMaxSize()) {
            Box(if (blur > 0.dp) Modifier.fillMaxSize().blur(blur) else Modifier.fillMaxSize()) { content() }
            if (text != null) {
                Dialog(onDismissRequest = { controller.text = null }) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                    ) {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Opens a hint window with [text] when the user presses and holds [content] (the press then does
 * not count as a click); without text it is just [content].
 */
@Composable
internal fun HintTip(text: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val controller = LocalHintController.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val gesture = if (text == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(text) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    try {
                        // Letting go, or the press turning into a scroll, before the timeout is not a long press.
                        withTimeout(timeout) { waitForUpOrCancellation(PointerEventPass.Initial) }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        controller.text = text
                        // Swallow the rest of this press so letting go does not select the option.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .semantics {
                onLongClick(label = null) {
                    controller.text = text
                    true
                }
            }
    }
    Box(modifier.then(gesture)) { content() }
}
