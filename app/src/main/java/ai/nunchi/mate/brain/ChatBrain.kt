package ai.nunchi.mate.brain

import ai.nunchi.engine.chat.ChatReply
import ai.nunchi.engine.chat.ChatReplyParser
import ai.nunchi.engine.chat.ChatRequest
import ai.nunchi.engine.chat.ChatTopic
import ai.nunchi.engine.chat.TopicLines
import ai.nunchi.engine.control.CarCommand
import ai.nunchi.mate.demo.DemoContent
import android.content.Context
import android.os.SystemClock

interface ChatBrain {
    val name: String

    fun reply(request: ChatRequest, onResult: (BrainAnswer) -> Unit)

    fun release() = Unit
}

data class BrainAnswer(
    val reply: ChatReply,
    val by: String,
    val latencyMs: Long,
    val topic: ChatTopic,
    val command: CarCommand? = null,
    val confirm: Boolean = false,
)

object Brains {
    fun create(context: Context): ChatBrain {
        val template = TemplateBrain()
        val topics = ModelFile.from(context, TopicBrain.DIR)?.let { TopicBrain(context, it, fallback = template) } ?: template
        return CarCommandBrain(topics)
    }
}

internal object TopicReplies {
    fun compose(topic: ChatTopic, request: ChatRequest): ChatReply {
        val driving = !request.car.parked
        val reply = if (topic == ChatTopic.BORED && !driving) {
            ChatReply(DemoContent.localStory(SystemClock.elapsedRealtime()).text, null, topic.intent)
        } else {
            TopicLines.reply(topic, request.persona, driving)
        }
        return ChatReplyParser.sanitize(reply, driving) ?: TopicLines.reply(ChatTopic.OTHER, request.persona, driving)
    }
}
