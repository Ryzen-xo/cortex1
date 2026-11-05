package com.cortex.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF05060A)
private val Pearl = Color(0xFFF4F5FA)
private val Cyan = Color(0xFF9BEAFF)
private val Violet = Color(0xFF9B8CFF)
private val SoftWhite = Color(0xFFE7EAF6)
private enum class Tab(val title: String) { HOME("Home"), CHAT("Chat"), SKILLS("Skills"), SETTINGS("Settings") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { CortexApp() }
    }
}

@Composable
private fun CortexApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val app = context.applicationContext as CortexApplication
    val prefs = remember { SecurePrefs(context) }
    val dao = remember { app.database.chatDao() }
    val messages by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var name by remember { mutableStateOf(prefs.getName()) }
    var apiKey by remember { mutableStateOf(prefs.getApiKey()) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var onboarding by remember { mutableStateOf(name.isBlank()) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready when you are") }
    var offline by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "dark")) }
    var motionEnabled by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice", true)) }
    var voiceSpeed by remember { mutableStateOf(prefs.getString("speed", "1.0").toFloatOrNull() ?: 1f) }
    var pendingAction by remember { mutableStateOf<ToolRequest?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }
    val useDark = when (themeMode) { "light" -> false; "system" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
    val textColor = if (useDark) SoftWhite else Color(0xFF171824)
    val bg = if (useDark) Ink else Pearl
    val client = remember { GeminiClient() }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { result -> if (result == TextToSpeech.SUCCESS) tts.value?.language = Locale.getDefault() }
        tts.value = engine
        onDispose { runCatching { engine.stop(); engine.shutdown() }; tts.value = null }
    }

    fun speak(text: String) {
        if (!voiceEnabled) return
        try {
            isSpeaking = true
            tts.value?.setSpeechRate(voiceSpeed)
            tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cortex-${System.currentTimeMillis()}")
            status = "Speaking"
        } catch (_: Exception) { isSpeaking = false; status = "Voice output is unavailable" }
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || isThinking) return
        val previousMessages = messages.takeLast(10)
        scope.launch {
            dao.insert(ChatMessage(role = "user", content = text))
            isThinking = true; isListening = false; status = "Thinking"; offline = false
            try {
                val reply = client.ask(prefs.getApiKey(), text, prefs.getName(), previousMessages)
                offline = reply.offline
                if (reply.action != null) {
                    pendingAction = reply.action
                    status = "Action needs your confirmation"
                    val label = actionDescription(reply.action)
                    dao.insert(ChatMessage(role = "assistant", content = "I can $label. Please review and confirm before I continue."))
                } else {
                    val answer = reply.text ?: "I couldn't create a response this time."
                    dao.insert(ChatMessage(role = "assistant", content = answer))
                    status = if (reply.offline) "Offline mode" else "Ready when you are"
                    speak(answer)
                }
            } catch (_: Exception) {
                val answer = "I couldn't complete that just now. Please check your connection and try again."
                dao.insert(ChatMessage(role = "assistant", content = answer)); status = answer; speak(answer)
            } finally { isThinking = false }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition(context, { isListening = true; status = "Listening" }, { heard -> status = "Heard: $heard"; sendMessage(heard) }, { error -> isListening = false; status = error })
        else status = "Microphone permission denied. You can type instead."
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(bg)) {
            MeshBackground(dark = useDark, enabled = motionEnabled)
            if (onboarding) {
                OnboardingScreen(
                    name = name, onName = { name = it }, apiKey = apiKey, onApiKey = { apiKey = it }, textColor = textColor,
                    onFinish = {
                        prefs.saveName(name); prefs.saveApiKey(apiKey); onboarding = false; status = "Welcome, ${name.ifBlank { "friend" }}"
                    }, onPrivacy = { showPrivacy = true }
                )
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        BottomBar(tab, useDark, textColor) { selected -> tab = selected; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.navigationBars)) {
                        AnimatedContent(targetState = tab, label = "screen") { selected ->
                            when (selected) {
                                Tab.HOME -> HomeScreen(
                                    name, messages.lastOrNull()?.content ?: status, status, isListening, isThinking, isSpeaking, offline, useDark, textColor,
                                    onMic = { if (isListening) { isListening = false; status = "Listening stopped" } else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                    onOpenChat = { tab = Tab.CHAT }
                                )
                                Tab.CHAT -> ChatScreen(messages, chatText, { chatText = it }, isThinking, textColor, useDark, onSend = { val msg = chatText; chatText = ""; sendMessage(msg) }, onMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) })
                                Tab.SKILLS -> SkillsScreen(textColor, useDark) { example -> chatText = example; tab = Tab.CHAT }
                                Tab.SETTINGS -> SettingsScreen(
                                    apiKey, { apiKey = it; prefs.saveApiKey(it) }, name, { name = it; prefs.saveName(it) }, themeMode,
                                    { themeMode = it; prefs.putString("theme", it) }, motionEnabled, { motionEnabled = it; prefs.putBoolean("motion", it) },
                                    voiceEnabled, { voiceEnabled = it; prefs.putBoolean("voice", it) }, voiceSpeed, { voiceSpeed = it; prefs.putString("speed", it.toString()) },
                                    textColor, useDark,
                                    onAccessibility = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure { status = "Couldn't open Accessibility settings." } },
                                    onForget = { scope.launch { dao.clear(); prefs.forgetAll(); apiKey = ""; name = ""; onboarding = true; status = "Local history and preferences cleared" } },
                                    onPrivacy = { showPrivacy = true }, onLog = { tab = Tab.CHAT }
                                )
                            }
                        }
                    }
                }
            }
            if (pendingAction != null) {
                val request = pendingAction!!
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text("Confirm phone action", color = textColor) },
                    text = { Text("Cortex wants to ${actionDescription(request)}. It will not send messages or place calls automatically. Continue?", color = textColor) },
                    confirmButton = { TextButton(onClick = {
                        pendingAction = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { AssistantTools.run(context, request) }
                            dao.insert(ChatMessage(role = "assistant", content = result)); status = result; speak(result)
                        }
                    }) { Text("Continue", color = Cyan) } },
                    dismissButton = { TextButton(onClick = { pendingAction = null; status = "Action cancelled" }) { Text("Cancel", color = textColor) } },
                    containerColor = if (useDark) Color(0xFF171923) else Color.White
                )
            }
            if (showPrivacy) AlertDialog(
                onDismissRequest = { showPrivacy = false },
                title = { Text("Your privacy matters", color = textColor) },
                text = { Text("Chat messages you send to Gemini are transmitted to Google. Google's free-tier terms may allow content to be used to improve its products. Never speak or type passwords, OTPs, banking details, or other secrets. Your local chat history is stored on this phone until you erase it.", color = textColor) },
                confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Understood", color = Cyan) } },
                containerColor = if (useDark) Color(0xFF171923) else Color.White
            )
        }
    }
}

private fun actionDescription(request: ToolRequest): String {
    val detail = request.arguments.values.firstOrNull().orEmpty()
    return when (request.name) {
        "send_sms" -> "prepare an SMS draft${if (detail.isNotBlank()) " for $detail" else ""}"
        "call_contact" -> "open the dialer for ${request.arguments["name"] ?: detail}"
        "open_whatsapp_chat" -> "open a WhatsApp message draft"
        "open_app" -> "open ${request.arguments["name"] ?: "an app"}"
        "set_timer" -> "open a timer"
        "set_alarm" -> "open an alarm editor"
        else -> "run ${request.name.replace('_', ' ')}"
    }
}

private fun startRecognition(context: android.content.Context, onStart: () -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
    try {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { onError("Speech recognition is unavailable on this device."); return }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Cortex")
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onStart() }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onError("I didn't catch that. Try again or type your message.") else onResult(text)
                recognizer.destroy()
            }
            override fun onError(error: Int) { onError("Speech recognition stopped. Please try again or type instead."); recognizer.destroy() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    } catch (_: Exception) { onError("Couldn't start the microphone. Check permission and try again.") }
}

@Composable
private fun MeshBackground(dark: Boolean, enabled: Boolean) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val p by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(if (enabled) 26000 else 1, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    Canvas(Modifier.fillMaxSize()) {
        val base = if (dark) Ink else Pearl
        drawRect(base)
        val colors = if (dark) listOf(Color(0xFF263C95), Color(0xFF482A85), Color(0xFF12658A), Color(0xFF352D76)) else listOf(Color(0xFFC6D9FF), Color(0xFFE1D4FF), Color(0xFFC9F1FA), Color(0xFFF7DDEB))
        val points = listOf(Offset(size.width * (0.18f + 0.12f * p), size.height * .2f), Offset(size.width * (0.82f - 0.1f * p), size.height * .36f), Offset(size.width * (0.25f + 0.12f * (1f - p)), size.height * .76f), Offset(size.width * .78f, size.height * (.85f - .15f * p)))
        points.forEachIndexed { index, point ->
            drawCircle(Brush.radialGradient(listOf(colors[index].copy(alpha = if (dark) .33f else .42f), colors[index].copy(alpha = .08f), Color.Transparent), center = point, radius = size.maxDimension * .48f), radius = size.maxDimension * .48f, center = point)
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(if (dark) listOf(Color.White.copy(alpha = .105f), Color.White.copy(alpha = .035f)) else listOf(Color.White.copy(alpha = .88f), Color.White.copy(alpha = .58f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .42f else .9f), Color.White.copy(alpha = .06f), Violet.copy(alpha = .18f))), shape)
            .padding(18.dp), content = content
    )
}

@Composable
private fun HomeScreen(name: String, lastMessage: String, status: String, listening: Boolean, thinking: Boolean, speaking: Boolean, offline: Boolean, dark: Boolean, text: Color, onMic: () -> Unit, onOpenChat: () -> Unit) {
    val state = when { listening -> "listening"; thinking -> "thinking"; speaking -> "speaking"; else -> "idle" }
    val title = when { listening -> "I'm listening"; thinking -> "One moment"; speaking -> "Here for you"; else -> "What’s on your mind?" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("CORTEX", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = Cyan); Text(if (name.isBlank()) "Welcome back" else "Hi, $name", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = text) }
            if (offline) Pill("OFFLINE MODE", dark, Color(0xFFB5E3CF))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = text.copy(alpha = .85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(status, color = text.copy(alpha = .6f), fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Orb(state = state, dark = dark, onClick = onMic)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth().clickable { onOpenChat() }, dark) {
            Text("RECENTLY", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(lastMessage.take(150), color = text, fontSize = 15.sp, maxLines = 3)
            Spacer(Modifier.height(6.dp))
            Text("Open conversation  →", color = Violet, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniAction("Talk", Icons.Rounded.Mic, dark, Modifier.weight(1f), onMic)
            MiniAction("Chat", Icons.Rounded.ChatBubbleOutline, dark, Modifier.weight(1f), onOpenChat)
        }
        Spacer(Modifier.height(10.dp))
        Text("Tap the orb to talk. Your conversations stay on this device.", color = text.copy(alpha = .52f), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Orb(state: String, dark: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(0.97f, 1.035f, infiniteRepeatable(tween(if (state == "thinking") 850 else 2500), RepeatMode.Reverse), label = "breathe")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(if (state == "thinking") 1800 else 8000, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "flow")
    val scale by animateFloatAsState(if (state == "listening") 1.06f else breathe, spring(dampingRatio = .76f), label = "orb scale")
    val tint = if (state == "error") Color(0xFFFF7F91) else Cyan
    Box(Modifier.size(272.dp).scale(scale).pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .22f), Violet.copy(alpha = .09f), Color.Transparent), center, size.minDimension * .5f), size.minDimension * .5f, center)
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Cyan.copy(alpha = .65f), Violet.copy(alpha = .5f), Color.Transparent), center), size.minDimension * .42f, center, style = Stroke(width = 1.2.dp.toPx()))
            val radius = size.minDimension * .32f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCAE9FF), Color(0xFF6479B5), Color(0xFF161B31)), Offset(center.x - radius * .28f, center.y - radius * .38f), radius * 1.6f), radius, center)
            val sheenPoint = Offset(center.x + kotlin.math.cos(rotation * Math.PI / 180).toFloat() * radius * .56f, center.y - radius * .72f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f), Color.White.copy(alpha = .08f), Color.Transparent), sheenPoint, radius * .65f), radius * .65f, sheenPoint)
            if (state == "listening" || state == "speaking") {
                drawCircle(Color.White.copy(alpha = .28f), radius * 1.13f, center, style = Stroke(width = 2.dp.toPx()))
                drawCircle(Cyan.copy(alpha = .22f), radius * 1.23f, center, style = Stroke(width = 1.dp.toPx()))
            }
        }
        Text(if (state == "listening") "•••" else "", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun ChatScreen(messages: List<ChatMessage>, value: String, onValue: (String) -> Unit, thinking: Boolean, text: Color, dark: Boolean, onSend: () -> Unit, onMic: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Your conversation", color = text, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(.88f).clip(RoundedCornerShape(22.dp)).background(if (message.role == "user") Brush.linearGradient(listOf(Color(0xFF5968AF).copy(alpha = .72f), Color(0xFF536A8C).copy(alpha = .6f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .12f else .82f), Color.White.copy(alpha = if (dark) .05f else .55f)))).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(22.dp)).padding(14.dp)) {
                        Text(if (message.role == "user") "YOU" else "CORTEX", color = if (message.role == "user") Color.White.copy(alpha = .7f) else Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp)); Text(message.content, color = if (message.role == "user") Color.White else text, fontSize = 15.sp)
                    }
                }
            }
            if (thinking) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)); Text("Cortex is thinking…", color = text.copy(alpha = .7f)) } }
        }
        Row(Modifier.fillMaxWidth().imePadding().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, onValue, Modifier.weight(1f), placeholder = { Text("Message Cortex", color = text.copy(alpha = .48f)) }, shape = RoundedCornerShape(24.dp), maxLines = 4)
            GlassIconButton(Icons.Rounded.Mic, dark, onMic)
            Button(onClick = onSend, enabled = value.isNotBlank() && !thinking, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667CCB))) { Text("Send") }
        }
    }
}

@Composable
private fun SkillsScreen(text: Color, dark: Boolean, onExample: (String) -> Unit) {
    val skills = listOf(
        Triple("Talk it through", "Get concise, supportive help with everyday questions.", "I feel overwhelmed. Help me choose one next step."),
        Triple("Open apps", "Ask Cortex to find an installed app by name.", "Open Maps"),
        Triple("Timers and alarms", "Set up a timer or open the Clock alarm editor.", "Set a 10 minute timer"),
        Triple("Web and navigation", "Search the web or open directions; review before acting.", "Navigate to the nearest park"),
        Triple("Draft messages", "Prepare SMS or WhatsApp drafts for you to review and send.", "Draft a message saying I'll be five minutes late"),
        Triple("Screen assistance", "Optional accessibility actions can read safe screen text and scroll.", "Summarize this screen")
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Skills", color = text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("A little help, when you ask.", color = text.copy(alpha = .65f), modifier = Modifier.padding(bottom = 18.dp))
        skills.forEach { (title, description, example) ->
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onExample(example) }, dark) {
                Text(title, color = text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp)); Text(description, color = text.copy(alpha = .72f), fontSize = 14.sp)
                Spacer(Modifier.height(7.dp)); Text("Try: “$example”", color = Cyan, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    apiKey: String, onApiKey: (String) -> Unit, name: String, onName: (String) -> Unit, theme: String, onTheme: (String) -> Unit,
    motion: Boolean, onMotion: (Boolean) -> Unit, voice: Boolean, onVoice: (Boolean) -> Unit, speed: Float, onSpeed: (Float) -> Unit,
    text: Color, dark: Boolean, onAccessibility: () -> Unit, onForget: () -> Unit, onPrivacy: () -> Unit, onLog: () -> Unit
) {
    var keyShown by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Settings", color = text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Your companion, your choices.", color = text.copy(alpha = .65f), modifier = Modifier.padding(bottom = 16.dp))
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), dark) {
            Text("YOUR PROFILE", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            OutlinedTextField(name, onName, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Your name") }, singleLine = true)
        }
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), dark) {
            Text("GEMINI API KEY", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            OutlinedTextField(apiKey, onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Paste your key") }, singleLine = true, visualTransformation = if (keyShown) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { keyShown = !keyShown }) { Text(if (keyShown) "Hide" else "Show") } })
            Text("Key is encrypted on this device. Get a free key at aistudio.google.com/app/apikey.", color = text.copy(alpha = .68f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Text("Model: $GEMINI_MODEL", color = text.copy(alpha = .72f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), dark) {
            Text("VOICE & MOTION", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            SettingSwitch("Speak responses", voice, text, onVoice)
            Text("Speech speed  ${"%.1f".format(speed)}×", color = text, modifier = Modifier.padding(top = 5.dp))
            Slider(value = speed, onValueChange = onSpeed, valueRange = .75f..1.25f)
            SettingSwitch("Ambient motion", motion, text, onMotion)
        }
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), dark) {
            Text("APPEARANCE", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (value, label) ->
                    Text(label, color = if (theme == value) Color.White else text, modifier = Modifier.clip(CircleShape).background(if (theme == value) Brush.linearGradient(listOf(Color(0xFF586FC4), Color(0xFF8B6DB6))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .1f), Color.Transparent))).clickable { onTheme(value) }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), dark) {
            Text("PERMISSIONS & PRIVACY", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            ChecklistRow("Microphone", "Ask when you first tap the talk button", text)
            ChecklistRow("Accessibility", "Optional. Enable only if you want screen actions.", text)
            TextButton(onClick = onAccessibility) { Text("Open accessibility settings", color = Violet) }
            ChecklistRow("Wake word", "Off. Vosk model is not bundled; tap-to-talk works.", text)
            ChecklistRow("Offline AI", "Basic offline fallback only; no Gemma model is bundled.", text)
            TextButton(onClick = onPrivacy) { Text("Read privacy notice", color = Violet) }
            TextButton(onClick = onLog) { Text("Local conversation log", color = Violet) }
        }
        GlassCard(Modifier.fillMaxWidth().padding(bottom = 20.dp), dark) {
            Text("LOCAL DATA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text("Forget everything removes saved name, encrypted key, settings, and local chat history.", color = text.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            TextButton(onClick = { confirmForget = true }) { Text("Forget everything", color = Color(0xFFFFA4B0)) }
        }
    }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("Erase local data?") }, text = { Text("This permanently deletes your local conversation history and saved preferences on this device.") }, confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Erase", color = Color(0xFFFF8295)) } }, dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } })
}

@Composable
private fun OnboardingScreen(name: String, onName: (String) -> Unit, apiKey: String, onApiKey: (String) -> Unit, textColor: Color, onFinish: () -> Unit, onPrivacy: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("C O R T E X", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("A calmer kind of\nconversation.", color = textColor, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Orb("idle", true, onClick = {})
        Text("A private, voice-first companion. Your conversations are saved locally on this phone.", color = textColor.copy(alpha = .75f), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 18.dp))
        GlassCard(Modifier.fillMaxWidth(), true) {
            Text("LET’S MAKE IT YOURS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            OutlinedTextField(name, onName, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("What should I call you?") }, singleLine = true)
            OutlinedTextField(apiKey, onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Gemini API key (optional for now)") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Text("Create a free key at aistudio.google.com/app/apikey. You can add it later in Settings.", color = textColor.copy(alpha = .68f), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6076C8))) { Text("Continue") }
        }
        TextButton(onClick = onPrivacy) { Text("Privacy notice", color = textColor.copy(alpha = .78f)) }
        Text("Microphone permission is requested only when you choose to speak.", color = textColor.copy(alpha = .55f), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BottomBar(selected: Tab, dark: Boolean, text: Color, onSelect: (Tab) -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), dark) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Tab.values().forEach { tab ->
                val chosen = tab == selected
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(if (chosen) Brush.linearGradient(listOf(Cyan.copy(alpha = .28f), Violet.copy(alpha = .16f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))).clickable { onSelect(tab) }.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when (tab) { Tab.HOME -> Icons.Rounded.Home; Tab.CHAT -> Icons.Rounded.ChatBubbleOutline; Tab.SKILLS -> Icons.Rounded.Star; Tab.SETTINGS -> Icons.Rounded.Settings }
                    androidx.compose.material3.Icon(icon, contentDescription = tab.title, tint = if (chosen) Cyan else text.copy(alpha = .62f), modifier = Modifier.size(20.dp))
                    Text(tab.title, fontSize = 10.sp, color = if (chosen) Cyan else text.copy(alpha = .66f))
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dark: Boolean, modifier: Modifier, action: () -> Unit) {
    GlassCard(modifier.clickable { action() }, dark) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, label, tint = Cyan, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, dark: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if (dark) Color.White.copy(alpha = .1f) else Color.White.copy(alpha = .65f)).border(1.dp, Color.White.copy(alpha = .25f), CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = Cyan)
    }
}

@Composable
private fun Pill(label: String, dark: Boolean, color: Color) {
    Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .1f)).border(1.dp, Color.White.copy(alpha = .12f), CircleShape).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, text: Color, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = text, fontSize = 14.sp)
        Switch(checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ChecklistRow(title: String, description: String, text: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF9ED9C1), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column { Text(title, color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(description, color = text.copy(alpha = .62f), fontSize = 12.sp) }
    }
}
