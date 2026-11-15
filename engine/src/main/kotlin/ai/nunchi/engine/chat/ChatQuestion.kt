package ai.nunchi.engine.chat

import ai.nunchi.engine.Message

object ChatQuestion {
    private const val PREFIX = "brain-"

    fun messageId(intent: ChatIntent, seq: Int) = "$PREFIX${intent.wire}-$seq"

    fun intentOf(m: Message): ChatIntent? {
        if (!m.id.startsWith(PREFIX)) return null
        val wire = m.id.removePrefix(PREFIX).substringBeforeLast('-')
        return ChatIntent.entries.firstOrNull { it.wire == wire }
    }

    fun needsConfirmation(intent: ChatIntent) =
        intent == ChatIntent.SUGGEST_STOP || intent == ChatIntent.REPLY_MESSAGE || intent == ChatIntent.CAR_CONTROL

    fun asks(m: Message, spoken: String = m.text): Boolean {
        val intent = intentOf(m) ?: return false
        return needsConfirmation(intent) && isQuestion(spoken)
    }

    internal fun isQuestion(text: String): Boolean {
        val t = text.trim()
        return t.endsWith("?") || t.trimEnd('.', '!', '~', ' ').endsWith("까요")
    }
}
