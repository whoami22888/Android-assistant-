# Gateway security model

The gateway is designed as a **paired, user-owned control plane**, not as an unattended remote-access service. The Android client starts only after a device-local pairing action and may automatically reconnect only with the locally stored pairing token. Accessibility, screen capture, app launch, and settings changes remain subject to Android permissions and the client’s local approval policy.

| Boundary | Implementation | Purpose |
| --- | --- | --- |
| Pairing | Long, single-purpose pairing code plus HMAC-signed device token | Prevent unpaired clients from opening a control channel. |
| Device actions | Structured action proposals with risk labels | The server cannot directly tap the phone; the phone decides whether and when to execute. |
| Sensitive actions | High-risk local confirmation | Text entry, coordinate taps, app launches, and settings intents cannot be executed as low-risk automation. |
| Browser | Read-only extraction with private-address blocking | Reduces SSRF risk and prevents autonomous form submission. |
| Analysis runner | AST allowlist, time/memory/file limits, clean environment | Supports limited data analysis without exposing gateway secrets. |
| Container | Non-root user, read-only root filesystem, capability drop, resource limits | Limits impact if the service or a dependency fails. |

Do not expose the service on the public Internet without a TLS reverse proxy, firewall rules, a high-entropy `PAIRING_CODE`, a unique `TOKEN_SECRET`, and regular dependency updates.
