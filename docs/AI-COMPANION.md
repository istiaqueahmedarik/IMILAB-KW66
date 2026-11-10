# Gemma 4 companion: verified facts and limits

Research checked 2026-11-11. These sources confirm model/runtime availability, not that a particular phone or this APK works.

| Question | Evidence / decision |
|---|---|
| Is Gemma 4 available? | Yes. Google's [model overview](https://ai.google.dev/gemma/docs/core), [launch article](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/) and [LiteRT-LM model guide](https://developers.google.com/edge/litert-lm/models/gemma-4) identify E2B and E4B mobile/edge models. |
| Which model first? | E2B, not E4B or a large MoE. [LiteRT Community E2B distribution](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) provides the intended `.litertlm` format. This app does not promise every quantization/backend combination works. |
| License? | The inspected Google model guide and E2B model card identify Apache-2.0. The [LiteRT-LM repository](https://github.com/google-ai-edge/LiteRT-LM) is Apache-2.0 too. Weights are imported by the user; none are redistributed here. The app's protocol attribution is separate; see NOTICE. |
| RAM? | Google's [QAT announcement](https://blog.google/innovation-and-ai/technology/developers-tools/quantization-aware-training-gemma-4/) reports ~1 GB E2B mobile model memory and less than 1 GB for a text-only configuration without per-layer embeddings. Other E2B encodings are larger. Context cache, runtime, native buffers and Android need additional RAM. That claim is not a measured memory budget for our CPU configuration or an arbitrary `.litertlm` file. |
| Phone compatibility? | Model inference on the target phone remains unverified. OS/ABI/RAM eligibility alone does not establish model compatibility. This project's Gemma variant conservatively requires Android 12+ and an arm64 app process. CPU model/quantization support, native-library page sizes and runtime loading still need a real phone test. The AI screen displays ABI and RAM. A modern 8 GB phone is a reasonable engineering starting point, not a proven minimum or guarantee; a smaller/older phone should start with core. |
| Power and heat? | Not measured. On-demand inference, two CPU threads, no continuous inference, short output and immediate unload are design choices, not a battery result. A persistent alarm intentionally keeps the CPU awake while ringing and has a separate power cost. |
| Voice? | Tap-to-talk and optional TTS use Android providers. An offline preference is requested, but neither offline ASR availability nor the provider's network behavior is guaranteed. The app itself declares no internet permission and no cloud model endpoint. No microphone background service is present. |

## Scoped context and execution

- Health: at most 24 recent decoded app records with watch-local times and format caveats.
- Todos: at most 20 active app tasks.
- Watch: connection status and reported battery.
- Each scope defaults off. Notification bodies, contacts and other app databases are not included.
- Output is plain text unless it parses as the expected JSON envelope. A proposed action is restricted to `send_message`, `sync_history` or `add_todo`; the app validates fields/scopes. An action is applied only by the in-app button. No arbitrary command/action dispatch or model-registered automatic tools are used.
- A single request uses an engine/conversation inside `use{}` blocks. `maxNumTokens=4096`, `maxOutputToken=384`, thinking disabled, CPU threads=2. Context overflow or incompatible models produce an error and no action.
- `onStop` and leaving the AI tab request `cancelProcess()` under a short synchronization lock. The worker owns native cleanup. Loading has no cancellation API, so cleanup after cancellation can wait for initialization. No idle resident engine is retained.

The adapter was checked against [pinned v0.18.1 configuration source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt), [message source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Message.kt), and [conversation cancellation source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt). The Gemma APK now resolves dependencies, compiles the Kotlin adapter and packages the runtime libraries successfully; Android lint has zero errors. Model loading/inference and native resource reclamation remain untested. Kotlin 2.3 is selected because recent runtime packages carry newer metadata; [AGP 8.13.2 documents Kotlin 2.3 support](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

## Alternatives if E2B does not fit

1. Keep the core APK. All watch/reminder features remain available without an AI runtime or model.
2. Try a smaller compatible text-only `.litertlm` model, such as the Gemma 3 1B distribution linked in the [official Kotlin getting-started guide](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md). Its license and compatibility must be assessed separately; Gemma 4's Apache license must not be assumed for older Gemma families. The generic adapter can import another model, but its output quality/JSON behavior are untested.
3. Use Google AI Edge Gallery to check a model on the phone before integrating it. Gallery success demonstrates that configuration only; it does not benchmark this app's runtime or grant our app access to Gallery's model/context. There is no undocumented Gallery bridge here.

A remote model endpoint would be a separate design requiring explicit configuration and scoped data transfer. It has not been silently substituted for on-device inference.

## Measure before enabling daily use

Use `scripts/device_report.py PHONE_SERIAL --label before` to collect model/API/ABI/RAM and the app's `dumpsys meminfo`, then repeat with the model loaded and after leaving AI. Run a fixed prompt several times and record cold load time, reply time, peak PSS, crash/OOM behavior and whether PSS falls after completion/cancellation. Phone/OS memory caching can delay visible reclamation. Compare 30-minute equal-condition runs with/without a fixed number of queries, screen state and BLE state held constant. Record battery level, temperature and Android battery usage. One battery percentage point is too coarse to prove efficiency. No such measurements were made here.
