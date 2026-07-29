package com.whoami22888.remoteagent.automation

import com.whoami22888.remoteagent.model.DeviceAction

object LocalSafetyPolicy {
    data class Decision(val allowed: Boolean, val message: String)

    fun assess(action: DeviceAction, activePackage: String? = null): Decision {
        val evidence = listOfNotNull(action.reason, action.text, action.package_name, activePackage)
            .joinToString(" ")
            .lowercase()
        val blockedTerms = listOf(
            "password", "passcode", "one-time", "otp", "verification code", "2fa",
            "payment", "pay now", "purchase", "checkout", "transfer", "wire", "bank",
            "wallet", "crypto", "delete account", "factory reset", "accessibility settings",
        )
        if (blockedTerms.any(evidence::contains)) {
            return Decision(false, "This action targets sensitive authentication, financial, destructive, or security content and is blocked locally.")
        }
        if (action.kind !in ALLOWED_KINDS) {
            return Decision(false, "This action type is not supported by the local automation policy.")
        }
        return Decision(true, "Local approval is required before this action can run.")
    }

    fun isSensitiveInputType(inputType: Int): Boolean {
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private val ALLOWED_KINDS = setOf(
        "click_text",
        "click_view_id",
        "scroll_forward",
        "scroll_backward",
        "set_text",
        "long_click_text",
        "tap_coordinate",
        "launch_app",
        "open_settings",
    )
}
