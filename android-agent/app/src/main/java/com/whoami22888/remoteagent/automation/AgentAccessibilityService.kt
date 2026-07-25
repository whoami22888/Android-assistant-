package com.whoami22888.remoteagent.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.whoami22888.remoteagent.model.DeviceAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Executes a single, locally approved action. It does not monitor events, collect text, or accept raw remote input.
 */
class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        _available.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: no continuous surveillance or event logging.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        _available.value = false
        super.onDestroy()
    }

    private fun executeInternal(action: DeviceAction, callback: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        val decision = LocalSafetyPolicy.assess(action, root?.packageName?.toString())
        if (!decision.allowed) {
            callback(false, decision.message)
            return
        }
        try {
            when (action.kind) {
                "click_text" -> findByText(root, action.text)?.click(callback)
                    ?: callback(false, "No visible matching text was found.")
                "long_click_text" -> findByText(root, action.text)?.longClick(callback)
                    ?: callback(false, "No visible matching text was found.")
                "click_view_id" -> findByViewId(root, action.view_id)?.click(callback)
                    ?: callback(false, "No visible matching view ID was found.")
                "scroll_forward" -> (root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    .also { callback(it == true, if (it == true) "Scrolled forward." else "The current screen cannot scroll forward.") }
                "scroll_backward" -> (root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    .also { callback(it == true, if (it == true) "Scrolled backward." else "The current screen cannot scroll backward.") }
                "set_text" -> setFocusedText(root, action.text, callback)
                "tap_coordinate" -> tap(action, callback)
                "launch_app" -> launchApp(action.package_name, callback)
                "open_settings" -> openSettings(callback)
                else -> callback(false, "Unsupported action type.")
            }
        } catch (exception: Exception) {
            callback(false, "Action failed locally: ${exception.javaClass.simpleName}")
        }
    }

    private fun findByText(root: AccessibilityNodeInfo?, text: String?): AccessibilityNodeInfo? {
        if (root == null || text.isNullOrBlank()) return null
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { it.isVisibleToUser && it.isEnabled }
    }

    private fun findByViewId(root: AccessibilityNodeInfo?, viewId: String?): AccessibilityNodeInfo? {
        if (root == null || viewId.isNullOrBlank()) return null
        return root.findAccessibilityNodeInfosByViewId(viewId)
            .firstOrNull { it.isVisibleToUser && it.isEnabled }
    }

    private fun AccessibilityNodeInfo.click(callback: (Boolean, String) -> Unit) {
        var candidate: AccessibilityNodeInfo? = this
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        val didClick = candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        callback(didClick, if (didClick) "Clicked the approved element." else "The matching element is not clickable.")
    }

    private fun AccessibilityNodeInfo.longClick(callback: (Boolean, String) -> Unit) {
        var candidate: AccessibilityNodeInfo? = this
        while (candidate != null && !candidate.isLongClickable) candidate = candidate.parent
        val didClick = candidate?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
        callback(didClick, if (didClick) "Long-pressed the approved element." else "The matching element cannot be long-pressed.")
    }

    private fun setFocusedText(root: AccessibilityNodeInfo?, text: String?, callback: (Boolean, String) -> Unit) {
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused == null || !focused.isEditable) {
            callback(false, "No editable focused field is available.")
            return
        }
        if (LocalSafetyPolicy.isSensitiveInputType(focused.inputType)) {
            callback(false, "Text entry into a password field is blocked locally.")
            return
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.orEmpty())
        }
        val didSet = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        callback(didSet, if (didSet) "Entered approved text into the focused non-sensitive field." else "Text entry was rejected by the app.")
    }

    private fun tap(action: DeviceAction, callback: (Boolean, String) -> Unit) {
        val x = action.x ?: run { callback(false, "Missing X coordinate."); return }
        val y = action.y ?: run { callback(false, "Missing Y coordinate."); return }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = callback(true, "Tapped the approved coordinate.")
            override fun onCancelled(gestureDescription: GestureDescription?) = callback(false, "The coordinate gesture was cancelled.")
        }, null)
        if (!accepted) callback(false, "Android rejected the coordinate gesture.")
    }

    private fun launchApp(packageName: String?, callback: (Boolean, String) -> Unit) {
        if (packageName.isNullOrBlank()) {
            callback(false, "Missing app package name.")
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            callback(false, "The requested app is not installed or cannot be launched.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        callback(true, "Opened the approved app.")
    }

    private fun openSettings(callback: (Boolean, String) -> Unit) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        callback(true, "Opened Android Settings for your manual review.")
    }

    companion object {
        private var instance: AgentAccessibilityService? = null
        private val _available = MutableStateFlow(false)
        val available = _available.asStateFlow()

        fun execute(action: DeviceAction, callback: (Boolean, String) -> Unit) {
            val service = instance
            if (service == null) callback(false, "Enable Remote Agent automation in Android Accessibility settings first.")
            else service.executeInternal(action, callback)
        }
    }
}
