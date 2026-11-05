package com.cortex.assistant

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// Change this single constant to switch supported Gemini models.
const val GEMINI_MODEL = "gemini-2.0-flash"
private const val COMPANION_PROMPT = """You are Cortex, a warm, concise, supportive AI companion. Talk like a real person. Give practical guidance, ask a follow-up question only when useful, reply in the user's language, and keep spoken answers short. Remember details only when the user shares them; memory is stored locally. Daily check-ins happen only when enabled. Be honest that you are an AI and encourage real human connections and professional help for serious personal issues. Never request or act on passwords, OTPs, banking details, or credentials. For phone actions use the available functions. Actions involving calls, messages, or destructive changes must be confirmed by the user before execution. open_whatsapp_chat only opens a draft."""

data class ToolRequest(val name: String, val arguments: Map<String, String>)
data class AssistantReply(val text: String? = null, val action: ToolRequest? = null, val offline: Boolean = false)

class GeminiClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(35, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun ask(apiKey: String, userText: String, userName: String, history: List<ChatMessage> = emptyList()): AssistantReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext AssistantReply(
            text = "I don't have an AI key yet. Add your free Gemini API key in Settings, or I can still help with simple offline guidance.", offline = true
        )
        val context = if (userName.isBlank()) COMPANION_PROMPT else "$COMPANION_PROMPT The user's name is $userName."
        val contents = buildJsonArray {
            history.takeLast(10).forEach { message ->
                add(buildJsonObject {
                    put("role", if (message.role == "assistant") "model" else "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", message.content.take(700)) }) })
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray { add(buildJsonObject { put("text", userText.take(1200)) }) })
            })
        }
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", context) }) }) })
            put("contents", contents)
            put("tools", toolDefinitions())
            put("generationConfig", buildJsonObject { put("temperature", 0.7); put("maxOutputTokens", 240) })
        }.toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
        var lastError = "Network unavailable"
        for (attempt in 0..2) {
            try {
                val req = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA)).build()
                client.newCall(req).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.code == 429 || text.contains("RESOURCE_EXHAUSTED", true)) {
                        lastError = "The free Gemini quota is temporarily exhausted."
                        if (attempt < 2) { delay(650L * (1L shl attempt)); return@use }
                        return@withContext offlineFallback(userText, true)
                    }
                    if (!response.isSuccessful) {
                        lastError = "Gemini returned HTTP ${response.code}."
                        if (response.code >= 500 && attempt < 2) { delay(500L * (1L shl attempt)); return@use }
                        return@withContext offlineFallback(userText, false)
                    }
                    return@withContext decode(text)
                }
            } catch (_: IOException) {
                lastError = "I can't reach Gemini right now."
                if (attempt < 2) delay(500L * (1L shl attempt))
            } catch (_: Exception) {
                lastError = "I couldn't understand the AI response."
                break
            }
        }
        offlineFallback(userText, lastError.contains("quota", true))
    }

    private fun decode(response: String): AssistantReply {
        val root = Json.parseToJsonElement(response).jsonObject
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
        parts.forEach { element ->
            val part = element.jsonObject
            val call = part["functionCall"]?.jsonObject
            if (call != null) {
                val name = call["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val args = call["args"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: v.toString() }.orEmpty()
                return AssistantReply(action = ToolRequest(name, args))
            }
            val answer = part["text"]?.jsonPrimitive?.contentOrNull
            if (!answer.isNullOrBlank()) return AssistantReply(text = answer.trim())
        }
        return AssistantReply(text = "I didn't get a clear response. Could you say that another way?")
    }

    private fun offlineFallback(input: String, quota: Boolean): AssistantReply {
        val prefix = if (quota) "The free Gemini limit is reached for now. " else "I'm offline at the moment. "
        val local = when {
            input.contains("hello", true) || input.contains("hi", true) -> "Hi! I'm here with you. What would be useful right now?"
            input.contains("timer", true) -> "I can help set a timer when you give me a duration, or you can use your phone's Clock app."
            input.contains("sad", true) || input.contains("stress", true) -> "I'm sorry it's feeling heavy. Try one slow breath and tell someone you trust what’s going on. What feels most pressing?"
            else -> "I can offer simple offline help, but I need Gemini for a full answer. Check your connection or API quota in Settings."
        }
        return AssistantReply(text = prefix + local, offline = true)
    }

    private fun toolDefinitions(): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("functionDeclarations", buildJsonArray {
                val specs = listOf(
                    Triple("open_app", "Open an installed app by its visible name", listOf("name")),
                    Triple("call_contact", "Open dialer for a contact; never place call without explicit confirmation", listOf("name")),
                    Triple("send_sms", "Prepare an SMS draft; never send it directly", listOf("name", "message")),
                    Triple("open_whatsapp_chat", "Open WhatsApp conversation as a draft only", listOf("phone", "message")),
                    Triple("set_alarm", "Open the clock app to set an alarm", listOf("hour", "minute")),
                    Triple("set_timer", "Set a device countdown timer", listOf("seconds", "label")),
                    Triple("add_reminder", "Draft a calendar reminder", listOf("title")),
                    Triple("open_url", "Open a web address", listOf("url")),
                    Triple("web_search", "Search the web for a phrase", listOf("query")),
                    Triple("navigate_to", "Open navigation to a place", listOf("destination")),
                    Triple("toggle_flashlight", "Toggle the device flashlight", emptyList()),
                    Triple("set_volume", "Adjust media volume from 0 to 100", listOf("level")),
                    Triple("set_brightness", "Open display settings to adjust brightness", emptyList()),
                    Triple("media_control", "Control media playback", listOf("command")),
                    Triple("read_screen", "Read visible non-sensitive screen text only", emptyList()),
                    Triple("tap_text", "Tap a visible non-sensitive label", listOf("text")),
                    Triple("type_text", "Type ordinary text into a focused field", listOf("text")),
                    Triple("scroll", "Scroll the current screen", listOf("direction")),
                    Triple("go_back", "Navigate back", emptyList()),
                    Triple("go_home", "Return to the home screen", emptyList()),
                    Triple("summarize_screen", "Summarize visible non-sensitive screen content", emptyList())
                )
                specs.forEach { (name, description, required) ->
                    add(buildJsonObject {
                        put("name", name); put("description", description)
                        put("parameters", buildJsonObject {
                            put("type", "OBJECT")
                            put("properties", buildJsonObject { required.forEach { put(it, buildJsonObject { put("type", "STRING") }) } })
                            put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                        })
                    })
                }
            })
        })
    }

    companion object { private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}
