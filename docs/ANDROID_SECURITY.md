# Android client behavior and permissions

The Android application is deliberately a **thin, paired client**. Its persistent state consists only of the gateway address, a generated device identifier, and a gateway token encrypted using an Android Keystore AES-GCM key. The one-time pairing code is passed to the gateway and never saved locally.

| Capability | Android mechanism | Local constraint |
| --- | --- | --- |
| Gateway connection | HTTPS REST plus a paired WebSocket status channel | HTTPS is required for every gateway endpoint, including private mesh endpoints. |
| UI automation | Accessibility service with node-text, view-ID, scroll, focused-field, and gesture primitives | The service does not observe events for logging. Each server proposal must be confirmed in the app before execution. |
| Screen capture | MediaProjection foreground service | Android displays the system capture consent flow. The service captures only a user-requested single frame and never writes frames to disk. |
| Text entry | Accessibility `ACTION_SET_TEXT` into the focused editable field | Password field variants and action requests that mention credentials, payment, destructive actions, or security settings are blocked locally. |
| Settings | Opens Android Settings for manual review | The client does not attempt to change secure settings or bypass Android permission controls. |

> The gateway proposes actions; it cannot directly operate the phone. Every proposal reaches the local approval dialog, and the Android service applies its own policy before it touches the active window.

For production use, set the gateway behind TLS, limit inbound network access, use a long random pairing code, and review Android accessibility disclosure requirements before distribution.
