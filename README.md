# Remote Agent — Android Thin Client + Docker Gateway

This repository contains a **production-oriented starter** for a Kotlin/Jetpack Compose Android client and a lightweight Docker gateway. The gateway performs bounded agent orchestration, optional read-only web extraction, restricted local data analysis, persistent SQLite event storage, and model-provider routing. The Android app renders the command center, stores a paired token in Android Keystore, and can perform only **locally approved** accessibility actions or explicitly consented screen capture.

> The gateway proposes actions; it does not directly control the phone. Every device action passes through the Android client’s local policy and approval dialog. Password fields, authentication codes, payment flows, destructive actions, and security-setting changes are blocked locally.

## Architecture

| Layer | Components | Responsibility |
| --- | --- | --- |
| Android client | Kotlin, Jetpack Compose, Android Keystore, AccessibilityService, MediaProjection | Pairing, command UI, activity log, explicit action approval, and user-consented frame capture. |
| Gateway | FastAPI, WebSocket status channel, SQLite, HMAC device tokens | Pairing, task state, bounded ReAct loop, event memory, and local-device action proposals. |
| Tools | Playwright and restricted Python runner | Read-only public web extraction and offline data analysis under resource and AST restrictions. |
| Model routing | Gemini, Groq, OpenRouter adapters | Provider fallback in the order **Groq → Gemini → OpenRouter**, using only keys supplied in `.env`. |
| Edge | Optional Caddy profile | Terminates TLS so the Android client pairs over HTTPS/WSS. |

The planner has a hard **four-step** execution limit. It records only concise task events, compacts context deterministically at 80% of the configured context budget, and emits user-facing results rather than hidden chain-of-thought.

## Fast start

Prepare a Docker-capable host and a DNS name that resolves to it. The Android client requires HTTPS, including when the gateway is reachable through a private mesh endpoint.

### Automated Docker setup

Use `setup-docker.sh` to create or preserve `.env`, generate missing pairing secrets, optionally prepare `Caddyfile`, start the selected Compose profile, and wait for the gateway health endpoint.

```bash
# Gateway only; useful for local verification or when a private HTTPS mesh endpoint is supplied separately.
./setup-docker.sh --mode local

# Gateway plus Caddy TLS; DNS for the domain must point to this host and ports 80/443 must be reachable.
./setup-docker.sh --mode tls --domain agent.example.com
```

The script prints the one-time pairing code at the end. It preserves existing non-placeholder values in `.env`; use `--no-start` to generate configuration without starting containers, or `--dry-run` to print the Compose command. Provider keys may be passed as environment variables, for example `GROQ_API_KEY=... ./setup-docker.sh --mode tls --domain agent.example.com`.

```bash
cd Android-assistant-
cp .env.example .env
# Set PAIRING_CODE and TOKEN_SECRET to unique random values; configure at least one model-provider key.
openssl rand -hex 32
```

Set `.env` values similar to the following. Do **not** commit this file.

```dotenv
PAIRING_CODE=<long-random-one-time-pairing-code>
TOKEN_SECRET=<long-random-secret>
GROQ_API_KEY=<optional-provider-key>
# Or configure GOOGLE_API_KEY and/or OPENROUTER_API_KEY.
```

Start the gateway, then confirm its health endpoint locally.

```bash
docker compose up --build -d agent-api
curl http://127.0.0.1:8080/health
```

To expose it securely, copy and edit the TLS template, then enable the optional proxy profile. The configured hostname must point at the host and ports 80/443 must be reachable for Caddy-managed public certificates.

```bash
cp Caddyfile.example Caddyfile
# Edit Caddyfile and replace agent.example.com.
docker compose --profile tls up -d
```

Build the Android debug APK using either a local Android SDK or the included on-demand compiler container.

```bash
# Option A: use the Docker compiler
 docker compose --profile android-build run --rm android-build :app:assembleDebug

# Option B: local SDK at ANDROID_HOME
cd android-agent
./gradlew :app:assembleDebug
```

The resulting APK is `android-agent/app/build/outputs/apk/debug/app-debug.apk`. Install it on a test device, pair using the HTTPS gateway URL and the one-time pairing code, enable the named accessibility service only if needed, and accept Android’s screen-capture dialog only when you request a frame.

## Deployment choices

| Approach | Tradeoffs | Cost | Setup complexity |
| --- | --- | --- | --- |
| Run the included stack on a user-controlled machine with a private HTTPS mesh endpoint | Lowest operational spend and keeps the gateway under your control; the machine must remain online. | Uses existing hardware and provider API quotas. | Moderate. |
| Run the same Docker stack on a managed VM with a DNS name and Caddy | Independent uptime and a standard public HTTPS endpoint; requires server administration and hardening. | VM, domain, and model-provider usage as applicable. | Moderate to high. |

## Android permission and safety behavior

| Capability | What the app does | Local control |
| --- | --- | --- |
| Pairing | Exchanges a one-time pairing code for a device token. | The code is never stored; the token is encrypted by an Android Keystore AES-GCM key. |
| Accessibility | Searches visible nodes by text or view ID, scrolls, applies a gesture, or writes only to a focused non-password field. | The server sends an action proposal; the owner must approve it in the app. |
| Screen capture | Runs a foreground MediaProjection service and captures a one-off JPEG frame. | Android’s consent sheet is required. Frames are not written to disk and are limited to 1 MB. |
| Settings/app launch | Opens a requested app or the general Android Settings screen. | Both are treated as high-risk actions and need local confirmation. |

The project intentionally avoids silent pairing, cleartext transport, keystroke collection, credential entry, payment execution, bypassing Android permissions, or unattended device-control behavior.

## Model providers and cost

The gateway includes adapters for Gemini, Groq, and OpenRouter because their credentials can be configured independently. A provider’s free tier, rate limit, model availability, and safety controls are determined by that provider at runtime; this repository does not bypass them. To minimize external inference cost, configure one provider first, keep task prompts short, and rely on the four-step limit before adding fallback keys.

## Validation completed in this build

| Check | Result |
| --- | --- |
| Backend Python compilation | Passed. |
| Gateway smoke test: health, pairing, token validation, authenticated task route, SQLite events, restricted analysis | Passed. |
| Android `:app:assembleDebug` compilation with Android API 35 | Passed. |

## Repository layout

```text
android-agent/             Native Kotlin / Compose app
  app/src/main/java/       UI, secure token storage, gateway client, accessibility, capture service
backend/                   FastAPI gateway, model router, SQLite store, browser and analysis tools
docs/                      Android and backend security design notes
docker-compose.yml         Gateway, optional TLS proxy, optional Android compiler
setup-docker.sh            Non-interactive Docker configuration, start, and health verification
Caddyfile.example          TLS edge template
.env.example               Non-secret configuration template
```

## References

Android’s accessibility and screen-capture APIs require explicit service or user consent and should be used according to Android’s user-disclosure requirements. [1] [2]

[1]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "Android AccessibilityService reference"
[2]: https://developer.android.com/media/grow/media-projection "Android MediaProjection documentation"
