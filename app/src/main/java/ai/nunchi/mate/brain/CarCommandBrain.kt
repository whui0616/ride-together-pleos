package ai.nunchi.mate.brain

import ai.nunchi.engine.chat.ChatIntent
import ai.nunchi.engine.chat.ChatReply
import ai.nunchi.engine.chat.ChatRequest
import ai.nunchi.engine.chat.ChatTopic
import ai.nunchi.engine.control.CarCommandParser
import ai.nunchi.engine.control.CarLines
import ai.nunchi.engine.control.CarParse
import android.os.SystemClock

class CarCommandBrain(private val inner: ChatBrain) : ChatBrain {
    override val name: String get() = inner.name

    override fun reply(request: ChatRequest, onResult: (BrainAnswer) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - started
        when (val p = CarCommandParser.parse(request.utterance)) {
            is CarParse.Command -> {
                val line = if (p.sure) CarLines.done(p.command) else CarLines.ask(p.command)
                onResult(BrainAnswer(ChatReply(line, null, ChatIntent.CAR_CONTROL), BY, elapsed(), ChatTopic.CONTROL, p.command, confirm = !p.sure))
            }
            is CarParse.Refused ->
                onResult(BrainAnswer(ChatReply(CarLines.REFUSED, null, ChatIntent.DECLINE), "$BY(거절: ${p.what})", elapsed(), ChatTopic.CONTROL))
            null -> inner.reply(request, onResult)
        }
    }

    override fun release() = inner.release()

    private companion object {
        const val BY = "차량 명령"
    }
}
