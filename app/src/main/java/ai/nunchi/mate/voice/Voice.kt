package ai.nunchi.mate.voice

interface Voice {
    val name: String
    val ready: Boolean
    fun configure(style: VoiceStyle) = Unit
    fun setVolume(v: Float) = Unit
    fun setSpeaker(id: String) = Unit
    fun speak(text: String)
    fun stop()
    fun release()
}

interface VoiceInput {
    fun listen(onResult: (String) -> Unit, onEnd: () -> Unit, onReady: () -> Unit = {}): Boolean
    fun stop()
    fun release()
}
