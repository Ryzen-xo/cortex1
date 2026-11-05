package com.cortex.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class CortexAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun safeRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString()?.lowercase().orEmpty()
        if (listOf("bank", "wallet", "pay", "otp", "authenticator").any { pkg.contains(it) }) return null
        fun sensitive(node: AccessibilityNodeInfo): Boolean {
            if (node.isPassword) return true
            val value = (node.text ?: node.contentDescription)?.toString().orEmpty()
            if (SENSITIVE_TERMS.containsMatchIn(value)) return true
            for (i in 0 until node.childCount) if (node.getChild(i)?.let(::sensitive) == true) return true
            return false
        }
        if (sensitive(root)) return null
        return root
    }

    private fun visibleText(): String {
        val root = safeRoot() ?: return "Screen text is unavailable or blocked for safety."
        val result = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isPassword) return
            val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && !text.matches(Regex("\\d{4,8}"))) result.add(text)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        val combined = result.distinct().take(40).joinToString(". ")
        if (SENSITIVE_TERMS.containsMatchIn(combined)) return "Screen reading is blocked because this page may contain banking or verification information."
        return combined.ifBlank { "There is no readable text on this screen." }
    }

    companion object {
        private val SENSITIVE_TERMS = Regex("\\b(bank|banking|wallet|otp|one[- ]time code|verification code|routing number|account balance|credit card|debit card|cvv|security code)\\b", RegexOption.IGNORE_CASE)
        @Volatile private var instance: CortexAccessibilityService? = null
        fun perform(request: ToolRequest): String? {
            val service = instance ?: return null
            return try {
                Toast.makeText(service, "Cortex is acting on this screen", Toast.LENGTH_SHORT).show()
                when (request.name) {
                    "read_screen", "summarize_screen" -> service.visibleText()
                    "go_back" -> { service.performGlobalAction(GLOBAL_ACTION_BACK); "Went back." }
                    "go_home" -> { service.performGlobalAction(GLOBAL_ACTION_HOME); "Returned to Home." }
                    "scroll" -> {
                        val root = service.safeRoot() ?: return "Screen action blocked for safety."
                        val direction = request.arguments["direction"]?.lowercase()
                        val node = root.findAccessibilityNodeInfosByText("").firstOrNull { it.isScrollable }
                        val action = if (direction == "up") AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
                        if (node?.performAction(action) == true) "Scrolled." else "Couldn't scroll this screen."
                    }
                    "tap_text" -> {
                        val target = request.arguments["text"].orEmpty()
                        if (target.isBlank()) return "No label was provided."
                        val root = service.safeRoot() ?: return "Screen action blocked for safety."
                        val node = root.findAccessibilityNodeInfosByText(target).firstOrNull { !it.isPassword && it.isClickable }
                        if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "Tapped $target." else "Couldn't find a safe, clickable label named $target."
                    }
                    "type_text" -> {
                        val root = service.safeRoot() ?: return "Screen action blocked for safety."
                        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return "No text field is focused."
                        if (focused.isPassword) return "I won't type into a password or secure field."
                        val text = request.arguments["text"].orEmpty()
                        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
                        "Entered text into the focused field. Please review it."
                    }
                    else -> "This accessibility action isn't implemented."
                }
            } catch (_: Exception) { "The accessibility action failed safely." }
        }
    }
}
