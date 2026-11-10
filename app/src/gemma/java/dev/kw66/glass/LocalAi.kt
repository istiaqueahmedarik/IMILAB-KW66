package dev.kw66.glass

import com.google.ai.edge.litertlm.*
import java.io.File
import java.util.concurrent.CancellationException

/** One-shot allocation. There is no resident model, autonomous loop, or notification access here. */
class LocalAi : AiEngine {
    private val guard = Any()
    @Volatile private var cancelled = false
    private var active: Conversation? = null
    override fun available() = true
    override fun begin() { cancelled = false }
    override fun reply(model: File, instruction: String, prompt: String): String {
        if (cancelled) throw CancellationException("App inactive")
        Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(threadCount = 2), maxNumTokens = 4096)).use { engine ->
            engine.initialize()
            if (cancelled) throw CancellationException("App inactive during model loading")
            engine.createConversation(ConversationConfig(systemInstruction = Contents.of(instruction), maxOutputToken = 384, thinkingConfig = ThinkingConfig(enableThinking = false))).use { conversation ->
                synchronized(guard) {
                    if (cancelled) throw CancellationException("App inactive")
                    active = conversation
                }
                try { return conversation.sendMessage(prompt).toString() }
                finally { synchronized(guard) { active = null } }
            }
        }
    }
    // Cancel generation; reply() releases native resources on its worker thread, never during a JNI call.
    // Model initialization itself cannot be interrupted by this API; release follows initialization.
    override fun close() { synchronized(guard) { cancelled = true; active?.let { runCatching { it.cancelProcess() } } } }
}
