package com.audiops.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.audiops.app.Work

/** Light and dark previews with the brand palette (no wallpaper colours in previews). */
@Preview(name = "Light", showBackground = true, widthDp = 400)
@Preview(name = "Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class ThemePreviews

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    AudiopsTheme(dynamicColor = false, darkTheme = androidx.compose.foundation.isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }
}

@ThemePreviews
@Composable
private fun HeroPreview() = PreviewSurface {
    HeroBanner(title = "Clearer audio, without the cloud.", body = "Recordings are processed on this device.")
}

@ThemePreviews
@Composable
private fun RecordingPreview() = PreviewSurface {
    StepTitle(1, "Recording")
    RecordingCard(name = "interview.m4a", enabled = true, onChoose = {})
}

@ThemePreviews
@Composable
private fun ProgressPreview() = PreviewSurface {
    WorkStatus(Work.Running("Enhancing section 3 of 8…", 0.3f))
    WorkStatus(Work.Running("Preparing audio…", null))
}

@ThemePreviews
@Composable
private fun ResultPreview() = PreviewSurface {
    ResultCard(title = "Enhanced audio is ready", body = "Save it as a WAV file to keep it.") {
        Text("Actions")
    }
    ErrorCard("Processing failed: This file does not contain a supported audio track.")
}
