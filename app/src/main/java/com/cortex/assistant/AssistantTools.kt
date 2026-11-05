package com.cortex.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioManager.FLAG_SHOW_UI
import android.media.AudioManager.ADJUST_SAME
import android.media.AudioManager.ADJUST_MUTE
import android.media.AudioManager.ADJUST_UNMUTE
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast

object AssistantTools {
    fun run(context: Context, request: ToolRequest): String {
        return try {
            val a = request.arguments
            when (request.name) {
                "open_app" -> {
                    val wanted = a["name"].orEmpty()
                    val pm = context.packageManager
                    val match = pm.getInstalledApplications(0).mapNotNull { info ->
                        val label = pm.getApplicationLabel(info).toString()
                        val score = when {
                            label.equals(wanted, true) -> 3
                            label.contains(wanted, true) || wanted.contains(label, true) -> 2
                            else -> 0
                        }
                        if (score > 0) Triple(score, label, info.packageName) else null
                    }.maxByOrNull { it.first }
                    val launch = match?.let { pm.getLaunchIntentForPackage(it.third) }
                    if (launch == null) "I couldn't find an installed app named $wanted." else {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); "Opening ${match.second}."
                    }
                }
                "call_contact" -> {
                    val number = a["phone"] ?: a["number"]
                    if (number.isNullOrBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        "Opened your Contacts app. Choose the person and place the call yourself."
                    } else { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opened the dialer. Review the number and tap Call yourself." }
                }
                "send_sms", "open_whatsapp_chat" -> {
                    val number = a["phone"] ?: a["number"] ?: a["name"].orEmpty()
                    val message = a["message"].orEmpty()
                    if (request.name == "open_whatsapp_chat") {
                        val uri = Uri.parse("https://wa.me/${Uri.encode(number)}?text=${Uri.encode(message)}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opened a WhatsApp draft. Review it and press Send yourself."
                    } else {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", message).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent); "Opened an SMS draft. Review it and press Send yourself."
                    }
                }
                "set_alarm" -> {
                    val hour = a["hour"]?.toIntOrNull() ?: 7
                    val minute = a["minute"]?.toIntOrNull() ?: 0
                    context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23)).putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59)).putExtra(AlarmClock.EXTRA_SKIP_UI, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opened the clock alarm editor. Confirm the alarm there."
                }
                "set_timer" -> {
                    val seconds = a["seconds"]?.toIntOrNull() ?: 60
                    context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceIn(1, 86400)).putExtra(AlarmClock.EXTRA_MESSAGE, a["label"] ?: "Cortex timer").putExtra(AlarmClock.EXTRA_SKIP_UI, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opened the timer in Clock. Confirm to start it."
                }
                "open_url" -> {
                    val raw = a["url"].orEmpty().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening the link."
                }
                "web_search" -> {
                    val uri = Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q", a["query"].orEmpty()).build()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening search results."
                }
                "navigate_to" -> {
                    val uri = Uri.parse("google.navigation:q=${Uri.encode(a["destination"].orEmpty())}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opening navigation. Review the route before starting."
                }
                "set_volume" -> {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = audio.getStreamMaxVolume(STREAM_MUSIC)
                    val level = a["level"]?.toIntOrNull()?.coerceIn(0, 100) ?: return "Tell me a media volume from zero to one hundred."
                    audio.setStreamVolume(STREAM_MUSIC, (max * level / 100f).toInt(), FLAG_SHOW_UI)
                    "Media volume adjusted to about $level percent."
                }
                "media_control" -> {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val code = when (a["command"]?.lowercase()) {
                        "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
                        "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                        "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
                        "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    }
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); "Sent the media control."
                }
                "add_reminder" -> {
                    val title = a["title"].orEmpty().ifBlank { "Cortex reminder" }
                    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3_600_000L).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent); "Opened a calendar reminder draft. Review its date and save it yourself."
                }
                "set_brightness" -> { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "Opened display settings so you can adjust brightness." }
                "toggle_flashlight" -> "Flashlight control is not available on this device build. Use Quick Settings instead."
                "read_screen", "tap_text", "tap_coordinates", "type_text", "scroll", "go_back", "go_home", "summarize_screen" -> CortexAccessibilityService.perform(request) ?: "That action needs the optional Cortex accessibility service. Enable it in Settings first."
                else -> "I don't support that phone action yet."
            }
        } catch (_: Exception) {
            "Android couldn't complete that action. Check the required app or permission and try again."
        }
    }
}
