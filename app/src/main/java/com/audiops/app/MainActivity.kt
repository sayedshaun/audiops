package com.audiops.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val LightColors = lightColorScheme(primary = Color(0xFF5145CD))
private val DarkColors = darkColorScheme(primary = Color(0xFFC5BFFF))

private enum class Operation(val label: String, val description: String, val detail: String) {
    ENHANCE("Enhance audio", "Bring clarity to your recording", "Improve the overall clarity and quality of a recording."),
    DENOISE("Remove noise", "Reduce unwanted background sound", "Reduce background noise while keeping the sound you want."),
    TRANSCRIBE("Transcribe speech", "Turn speech into text", "Transcribe speech in Bangla, English, Hindi and more.")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) DarkColors else LightColors
            MaterialTheme(colorScheme = colors) { AudioOpsApp() }
        }
    }
}

@Composable
private fun AudioOpsApp() {
    val context = LocalContext.current
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var speechLanguage by rememberSaveable { mutableStateOf("") }
    var transcript by remember { mutableStateOf<String?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var operationName by rememberSaveable { mutableStateOf<String?>(null) }
    val operation = Operation.entries.firstOrNull { it.name == operationName }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            outputFile = null
            transcript = null
            message = null
            selectedUri = uri.toString()
            selectedName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "Selected audio"
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val file = outputFile
        if (uri != null && file != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open destination.")
                    output.use { out -> file.inputStream().use { it.copyTo(out) } }
                }
                "Enhanced audio saved."
            } catch (e: Exception) { "Could not save audio: ${e.localizedMessage}" }
        }
    }
    val textSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = transcript
        if (uri != null && text != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open destination.")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                "Transcript saved."
            } catch (e: Exception) { "Could not save transcript: ${e.localizedMessage}" }
        }
    }
    BackHandler(enabled = operation != null) { if (!processing) operationName = null }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("audioops", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp)
                Text("YOUR AUDIO, REIMAGINED", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, letterSpacing = 1.1.sp)
            }
            Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(50)) {
                Text("ON DEVICE", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = colors.onSecondaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        if (operation == null) {
            Text("What would you like to do?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
            Spacer(Modifier.height(8.dp))
            Text("Choose a tool to get started. Your audio stays on your device.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(26.dp), color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", color = colors.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Text("Enhance speech", color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Improve the clarity of a short voice recording.", color = colors.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { operationName = Operation.ENHANCE.name }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Start enhancing   →") }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("More tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Choose another audio tool.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Operation.entries.drop(1).forEachIndexed { index, item ->
                ToolCard(item, index + 1) { operationName = item.name }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            TextButton(onClick = { operationName = null }, enabled = !processing, contentPadding = PaddingValues(0.dp)) {
                Text("‹   Back to tools", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(operation.label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(7.dp))
            Text(operation.detail, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
        }
        if (operation == Operation.ENHANCE) {
            Text("1   Choose a recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            AudioFileCard(selectedName, selectedUri != null, processing) { picker.launch(arrayOf("audio/*")) }
            Spacer(Modifier.height(12.dp))
            Text("Choose an audio file. The app prepares it automatically; longer recordings take more time.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            Text("2   Enhance and save", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("The first run downloads about 360 MB of model files. Processing happens on your device and may take a while.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
            Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = selectedUri != null && !processing, onClick = {
                val uri = selectedUri ?: return@Button
                processing = true
                message = "Preparing audio and model…"
                outputFile = null
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val audio = AudioDecoder.decode(context.applicationContext, Uri.parse(uri))
                            try {
                                val result = File.createTempFile("enhanced-", ".wav", context.cacheDir)
                                try {
                                    ResembleEnhancer(context.applicationContext, "SayedShaun/resemble-enhance-litert", dtype = "int8").use { model ->
                                        WavWriter(result).use { writer ->
                                            var tail: FloatArray? = null
                                            var section = 0
                                            AudioDecoder.forEachEnhancementChunk(audio) { samples, last ->
                                                section++
                                                scope.launch { message = "Enhancing section $section…" }
                                                val enhanced = model.enhance(samples)
                                                val previous = tail
                                                val joined = if (previous == null) enhanced else {
                                                    val overlap = minOf(previous.size, enhanced.size)
                                                    FloatArray(enhanced.size) { index ->
                                                        if (index < overlap) {
                                                            val weight = (index + 1).toFloat() / (overlap + 1)
                                                            previous[index] * (1f - weight) + enhanced[index] * weight
                                                        } else enhanced[index]
                                                    }
                                                }
                                                if (last) {
                                                    writer.append(joined)
                                                    tail = null
                                                } else {
                                                    val keep = minOf(44_100, joined.size)
                                                    writer.append(joined.copyOfRange(0, joined.size - keep))
                                                    tail = joined.copyOfRange(joined.size - keep, joined.size)
                                                }
                                            }
                                        }
                                    }
                                    result
                                } catch (e: Exception) { result.delete(); throw e }
                            } finally { audio.delete() }
                        }
                        outputFile = file
                        message = "Enhancement complete. Save the WAV to keep it."
                    } catch (e: Exception) {
                        message = "Enhancement failed: ${e.localizedMessage ?: "Unknown error"}"
                    } finally { processing = false }
                }
            }) { Text(if (processing) "Processing…" else "Enhance audio") }
                    if (processing) { Spacer(Modifier.height(14.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    if (outputFile != null && !processing) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { saver.launch("enhanced.wav") }) { Text("Save enhanced audio") }
                    }
                    message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        } else if (operation == Operation.TRANSCRIBE) {
            Text("1   Choose a recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            AudioFileCard(selectedName, selectedUri != null, processing) { picker.launch(arrayOf("audio/*")) }
            Spacer(Modifier.height(12.dp))
            Text("Choose an audio file. Longer recordings are transcribed in sections.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Text("Spoken language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("For Bangla, select Bangla to use the dedicated model. Auto uses Whisper, which may misidentify short clips.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Auto", "bn" to "Bangla", "en" to "English", "hi" to "Hindi").forEach { (code, label) ->
                    FilterChip(selected = speechLanguage == code, onClick = { speechLanguage = code }, enabled = !processing, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("2   Transcribe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (speechLanguage == "bn") "The first Bangla transcription downloads about 74 MB. It runs on your device." else "The first multilingual transcription downloads about 103 MB. It runs on your device.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = selectedUri != null && !processing, onClick = {
                        val uri = selectedUri ?: return@Button
                        val language = speechLanguage
                        processing = true
                        transcript = null
                        message = if (language == "bn") "Preparing Bangla model and transcribing…" else "Preparing multilingual model and transcribing…"
                        scope.launch {
                            try {
                                val text = withContext(Dispatchers.IO) {
                                    val audio = AudioDecoder.decode(context.applicationContext, Uri.parse(uri))
                                    try {
                                        if (language == "bn") BanglaTranscriber.transcribe(context.applicationContext, audio)
                                        else WhisperTranscriber.transcribe(context.applicationContext, audio, language)
                                    } finally { audio.delete() }
                                }
                                transcript = text.takeIf { it.isNotBlank() }
                                message = if (text.isBlank()) "No speech was recognized. Try a clearer recording or select its language." else "Transcript ready."
                            } catch (e: Exception) {
                                message = "Transcription failed: ${e.localizedMessage ?: "Unknown error"}"
                            } finally { processing = false }
                        }
                    }) { Text(if (processing) "Transcribing…" else "Transcribe audio") }
                    if (processing) { Spacer(Modifier.height(14.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    message?.let { Spacer(Modifier.height(12.dp)); Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                    transcript?.let { text ->
                        Spacer(Modifier.height(20.dp))
                        Text("Transcript", color = colors.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(text, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = { textSaver.launch("transcript.txt") }) { Text("Save transcript") }
                    }
                }
            }
        } else if (operation != null) {
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("Coming soon", color = colors.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("This tool is still in the works. Check back later.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("✧", color = colors.primary)
            Spacer(Modifier.width(7.dp))
            Text("MADE FOR PRIVATE LISTENING", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BrandMark() {
    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Waveform(Modifier.size(width = 24.dp, height = 20.dp), Color.White, 9)
    }
}

@Composable
private fun HeroCard() {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF3A2A85), Color(0xFF6D58D8), Color(0xFF9281E8))), RoundedCornerShape(30.dp)).padding(24.dp)) {
        Surface(color = Color.White.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
            Text("A LITTLE MORE CLARITY", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text("Great sound,\nright in your hands.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, lineHeight = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text("Give your recordings the attention they deserve.", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Waveform(Modifier.fillMaxWidth().height(42.dp), Color.White.copy(alpha = .72f), 43)
    }
}

@Composable
private fun Waveform(modifier: Modifier, color: Color, bars: Int) {
    Canvas(modifier) {
        val step = size.width / bars
        for (i in 0 until bars) {
            val envelope = .2f + .8f * kotlin.math.sin(Math.PI.toFloat() * (i + 1) / (bars + 1))
            val variation = .25f + .75f * kotlin.math.abs(kotlin.math.sin(i * 2.19f))
            val height = (size.height * envelope * variation).coerceAtLeast(3.dp.toPx())
            val x = (i + .5f) * step
            drawLine(color, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), strokeWidth = (step * .38f).coerceAtLeast(1.dp.toPx()), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AudioFileCard(name: String?, selected: Boolean, busy: Boolean, onChoose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(colors.primaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("♫", color = colors.onPrimaryContainer, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("RECORDING", color = colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(name ?: "No recording selected", maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (selected) "Selected and ready" else "Select an audio file", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onChoose, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (selected) "Change recording" else "Choose audio file")
            }
        }
    }
}

@Composable
private fun ToolCard(item: Operation, index: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tones = listOf(colors.primaryContainer, colors.tertiaryContainer, colors.secondaryContainer)
    val inks = listOf(colors.onPrimaryContainer, colors.onTertiaryContainer, colors.onSecondaryContainer)
    val glyphs = listOf("✦", "≈", "Aa")
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).background(tones[index], RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(glyphs[index], color = inks[index], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    if (item == Operation.DENOISE) {
                        Spacer(Modifier.width(8.dp))
                        Text("SOON", color = colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = colors.primary, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
