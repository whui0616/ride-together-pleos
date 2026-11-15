package ai.nunchi.mate.brain

import ai.nunchi.engine.chat.ChatRequest
import ai.nunchi.engine.chat.ChatTopic
import ai.nunchi.engine.chat.TopicClassifier
import android.os.SystemClock

class TemplateBrain : ChatBrain {
    override val name = "낱말 분류"

    override fun reply(request: ChatRequest, onResult: (BrainAnswer) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val topic = TopicClassifier.byKeyword(request.utterance) ?: ChatTopic.OTHER
        onResult(BrainAnswer(TopicReplies.compose(topic, request), name, SystemClock.elapsedRealtime() - started, topic))
    }
}
