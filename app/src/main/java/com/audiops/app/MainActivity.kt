package com.audiops.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF171B2D)
private val Accent = Color(0xFF5145CD)
private val Muted = Color(0xFF626980)

private enum class Operation(val label: String, val description: String, val detail: String) {
    ENHANCE("Enhance audio", "Bring clarity to your recording", "Improve the overall clarity and quality of a recording."),
    DENOISE("Remove noise", "Reduce unwanted background sound", "Reduce background noise while keeping the sound you want."),
    TRANSCRIBE("Transcribe speech", "Turn spoken words into text", "Convert spoken audio into a readable transcript.")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Accent)) { AudioOpsApp() } }
    }
}

@Composable
private fun AudioOpsApp() {
    val context = LocalContext.current
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var operationName by rememberSaveable { mutableStateOf<String?>(null) }
    val operation = Operation.entries.firstOrNull { it.name == operationName }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri.toString()
            selectedName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "Selected audio"
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F7FC)).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        if (operation == null) {
            Text("AUDIOOPS", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(22.dp))
            Text("Make audio sound better.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Choose an audio file and select what you’d like to do.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("‹  All operations", color = Accent, modifier = Modifier.clickable { operationName = null }.padding(vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(operation.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(operation.detail, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(28.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("YOUR AUDIO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Text(selectedName ?: "No file selected", color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(if (selectedUri == null) "Choose an audio file from your device." else "Ready for an operation", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text(if (selectedUri == null) "Choose audio" else "Change audio") }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (operation == null) {
            Text("What would you like to do?", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Operation.entries.forEach { item ->
                Surface(onClick = { operationName = item.name }, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(item.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("›", color = Accent, fontSize = 24.sp)
                    }
                }
            }
        } else {
            Text("Coming soon", color = Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("On-device processing for this operation is not connected yet. You can select a file now and return when it’s available.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(32.dp))
        Text("Designed for on-device processing", color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}
