package com.audiops.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.audiops.app.AiModel
import com.audiops.app.Tool
import com.audiops.app.ToolCategory

val Tool.icon: ImageVector
    get() = when (this) {
        Tool.ENHANCE -> Icons.Rounded.AutoFixHigh
        Tool.TRANSCRIBE -> Icons.Rounded.RecordVoiceOver
        Tool.DENOISE -> Icons.Rounded.Waves
        Tool.SILENCE -> Icons.Rounded.Compress
        Tool.TRIM -> Icons.Rounded.ContentCut
        Tool.FADE -> Icons.Rounded.Gradient
        Tool.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
        Tool.SPEED -> Icons.Rounded.Speed
        Tool.EQUALIZER -> Icons.Rounded.Equalizer
        Tool.CONVERT -> Icons.Rounded.SwapHoriz
        Tool.ANALYZE -> Icons.Rounded.Insights
    }

/** Each tool has its own Material 3 Expressive shape, so tools are recognisable at a glance. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Tool.shape: RoundedPolygon
    get() = when (this) {
        Tool.ENHANCE -> MaterialShapes.Cookie9Sided
        Tool.TRANSCRIBE -> MaterialShapes.Sunny
        Tool.DENOISE -> MaterialShapes.SoftBurst
        Tool.SILENCE -> MaterialShapes.Pill
        Tool.TRIM -> MaterialShapes.Cookie4Sided
        Tool.FADE -> MaterialShapes.Clover4Leaf
        Tool.VOLUME -> MaterialShapes.Cookie6Sided
        Tool.SPEED -> MaterialShapes.Arrow
        Tool.EQUALIZER -> MaterialShapes.Flower
        Tool.CONVERT -> MaterialShapes.Pentagon
        Tool.ANALYZE -> MaterialShapes.Gem
    }

/** Container and content colours per category, from the theme's tonal roles. */
fun ToolCategory.colors(scheme: ColorScheme): Pair<Color, Color> = when (this) {
    ToolCategory.AI -> scheme.primaryContainer to scheme.onPrimaryContainer
    ToolCategory.CLEAN_UP -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    ToolCategory.EDIT -> scheme.secondaryContainer to scheme.onSecondaryContainer
    ToolCategory.EFFECTS -> scheme.primaryFixedDim to scheme.onPrimaryFixed
    ToolCategory.UTILITIES -> scheme.surfaceContainerHighest to scheme.onSurface
}

/** Icon for a downloadable model: the icon of the tool that uses it. */
val AiModel.icon: ImageVector
    get() = when (this) {
        AiModel.ENHANCE -> Tool.ENHANCE.icon
        AiModel.WHISPER, AiModel.BANGLA -> Tool.TRANSCRIBE.icon
    }
