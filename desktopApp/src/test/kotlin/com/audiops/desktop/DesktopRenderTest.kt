package com.audiops.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.audiops.app.AudiopsViewModel
import com.audiops.app.HistoryRepository
import com.audiops.app.SettingsRepository
import com.audiops.app.Tab
import com.audiops.app.Tool
import com.audiops.app.createSettingsStore
import com.audiops.app.ui.AudiopsApp
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the real desktop UI off-screen (no display needed) and writes PNGs to
 * build/screenshots, so layout regressions on desktop are visible in CI artifacts.
 */
class DesktopRenderTest {
    private val out = File("build/screenshots").apply { mkdirs() }

    private object ResumedOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private fun render(name: String, width: Int, height: Int, setup: (AudiopsViewModel) -> Unit = {}) {
        val dir = File.createTempFile("audiops-render", "").apply { delete(); mkdirs() }
        try {
            val store = createSettingsStore(File(dir, "settings.preferences_pb").absolutePath)
            val viewModel = AudiopsViewModel(SavedStateHandle(), DesktopAudioEngine(dir), SettingsRepository(store), HistoryRepository(store))
            setup(viewModel)
            val scene = ImageComposeScene(width, height, Density(1.25f)) {
                CompositionLocalProvider(LocalLifecycleOwner provides ResumedOwner) {
                    AudiopsApp(viewModel, appVersion = "test")
                }
            }
            // Render a few frames so animations settle.
            var image = scene.render(0)
            for (t in 1..20) image = scene.render(t * 50_000_000L)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(png)
            scene.close()
            assertTrue(png.size > 1000)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rendersToolsTabAtDesktopWidth() = render("desktop-tools", 1375, 1000)

    @Test fun rendersToolScreen() = render("desktop-tool-trim", 1375, 1000) { it.open(Tool.TRIM) }

    @Test fun rendersSettingsTab() = render("desktop-settings", 1375, 1000) { it.selectTab(Tab.SETTINGS) }

    @Test fun rendersPhoneWidth() = render("desktop-narrow", 520, 1000)
}
