package ai.nunchi.engine.suggest

import ai.nunchi.engine.Reply

import ai.nunchi.engine.Gear

class SuggestionManager {
    var replyTimeoutMs: Long = REPLY_TIMEOUT_MS
    var disabledKinds: Set<SuggestionKind> = emptySet()
    var noReplyIsDecline: Boolean = false
    private var prefsForTimeout: UserPrefs? = null
    private class Entry(val s: Suggestion) {
        var state = SuggestionState.QUEUED
        var laterCount = 0
        var lastDistanceM: Double? = null
        var increaseStreak = 0

        val alive: Boolean
            get() = state == SuggestionState.QUEUED ||
                state == SuggestionState.LATER ||
                state == SuggestionState.AWAITING_REPLY
    }

    private val entries = LinkedHashMap<String, Entry>()

    var awaitingId: String? = null
        private set
    private var awaitingSinceMs = 0L

    var repliesSpoken = 0
        private set
    var storiesSpoken = 0
        private set
    var lastSpeechMs: Long? = null
        private set
    var noReplyStreak = 0
        private set

    var quietMode = false
        private set

    fun offer(s: Suggestion) {
        if (entries.containsKey(s.id)) return
        entries[s.id] = Entry(s)
    }

    fun states(): Map<String, SuggestionState> = entries.mapValues { it.value.state }
    fun stateOf(id: String): SuggestionState? = entries[id]?.state
    fun laterCountOf(id: String): Int = entries[id]?.laterCount ?: 0

    fun onLocationSample(nowMs: Long, distanceToPlaceM: (Suggestion) -> Double?) {
        expireOld(nowMs)
        checkReplyTimeout(nowMs)
        for (e in entries.values) {
            if (!e.alive) continue
            val d = distanceToPlaceM(e.s) ?: continue
            val prev = e.lastDistanceM
            if (prev != null && d > prev) e.increaseStreak++ else e.increaseStreak = 0
            e.lastDistanceM = d
            if (e.increaseStreak >= PASS_SAMPLES && d > PASS_MARGIN_M) discard(e)
        }
    }

    fun onLocationSample(nowMs: Long, distances: Map<String, Double>) =
        onLocationSample(nowMs) { distances[it.id] }

    fun onRouteChanged(onRouteIds: Set<String>) {
        for (e in entries.values) if (e.alive && e.s.id !in onRouteIds) discard(e)
    }

    fun onDestinationArrived() {
        for (e in entries.values) if (e.alive) discard(e)
    }

    fun onGear(gear: Gear) {
        if (gear != Gear.PARK) return
        entries.clear()
        awaitingId = null
        repliesSpoken = 0
        storiesSpoken = 0
        lastSpeechMs = null
        quietMode = false
    }

    fun tick(
        ctx: DriveContext,
        prefs: UserPrefs,
        scorer: SuggestionScorer,
        window: SuggestGate = SuggestGate,
    ): Suggestion? {
        val now = ctx.nowMs
        expireOld(now)
        prefsForTimeout = prefs
        checkReplyTimeout(now)
        if (awaitingId != null) return null

        val effCtx = ctx.copy(
            repliesSpokenThisTrip = maxOf(ctx.repliesSpokenThisTrip, repliesSpoken),
            storiesSpokenThisTrip = maxOf(ctx.storiesSpokenThisTrip, storiesSpoken),
            msSinceLastSpeech = lastSpeechMs?.let { minOf(ctx.msSinceLastSpeech, now - it) }
                ?: ctx.msSinceLastSpeech,
        )

        val best = entries.values.asSequence()
            .filter { it.alive && it.state != SuggestionState.AWAITING_REPLY }
            .filter { it.s.kind !in disabledKinds }
            .filter { !(quietMode && it.s.requiresReply) }
            .filter { window.isOpen(effCtx, it.s) }
            .map { it to scorer.score(it.s, effCtx, prefs) }
            .filter { it.second >= SuggestionScorer.THRESHOLD }
            .sortedWith(
                compareByDescending<Pair<Entry, Double>> { it.second }
                    .thenBy { it.first.s.distanceAheadM },
            )
            .firstOrNull()?.first ?: return null

        best.state = SuggestionState.SPOKEN
        lastSpeechMs = now
        if (best.s.kind == SuggestionKind.STORY) storiesSpoken++
        if (best.s.requiresReply) {
            repliesSpoken++
            best.state = SuggestionState.AWAITING_REPLY
            awaitingId = best.s.id
            awaitingSinceMs = now
        }
        return best.s
    }

    fun onTtsFinished(nowMs: Long) {
        if (awaitingId != null) awaitingSinceMs = nowMs
    }

    fun onReplyTimeout(nowMs: Long) = checkReplyTimeout(nowMs)

    fun cancelAwaiting() {
        val id = awaitingId ?: return
        awaitingId = null
        entries[id]?.let { discard(it) }
    }

    fun onReply(reply: Reply, prefs: UserPrefs? = null) {
        val id = awaitingId ?: return
        awaitingId = null
        val e = entries[id] ?: return
        when (reply) {
            Reply.YES -> {
                e.state = SuggestionState.ACCEPTED
                prefs?.recordAccept(e.s.kind)
                noReplyStreak = 0
            }
            Reply.NO -> {
                e.state = SuggestionState.REJECTED
                prefs?.recordReject(e.s.kind)
                noReplyStreak = 0
            }
            Reply.LATER -> {
                noReplyStreak = 0
                if (e.laterCount < MAX_LATER) {
                    e.laterCount++
                    e.state = SuggestionState.LATER
                } else {
                    discard(e)
                }
            }
            Reply.UNKNOWN -> expireNoReply(e)
        }
    }

    private fun checkReplyTimeout(nowMs: Long) {
        val id = awaitingId ?: return
        if (nowMs - awaitingSinceMs < replyTimeoutMs) return
        awaitingId = null
        entries[id]?.let { e ->
            if (noReplyIsDecline) { e.state = SuggestionState.REJECTED; prefsForTimeout?.recordReject(e.s.kind) } else expireNoReply(e)
        }
    }

    private fun expireNoReply(e: Entry) {
        e.state = SuggestionState.EXPIRED_NO_REPLY
        noReplyStreak++
        if (noReplyStreak >= QUIET_AFTER_NO_REPLY) quietMode = true
    }

    private fun expireOld(nowMs: Long) {
        for (e in entries.values) {
            if (e.alive && nowMs - e.s.createdAtMs >= MAX_AGE_MS) discard(e)
        }
    }

    private fun discard(e: Entry) {
        if (awaitingId == e.s.id) awaitingId = null
        e.state = SuggestionState.DISCARDED
    }

    companion object {
        const val REPLY_TIMEOUT_MS = 5_000L
        const val MAX_AGE_MS = 10 * 60_000L
        const val PASS_MARGIN_M = 200.0
        const val PASS_SAMPLES = 2
        const val MAX_LATER = 1
        const val QUIET_AFTER_NO_REPLY = 3
    }
}
