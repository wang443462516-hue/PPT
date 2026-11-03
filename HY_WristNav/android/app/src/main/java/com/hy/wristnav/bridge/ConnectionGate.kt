package com.hy.wristnav.bridge

/** Serializes SDK callbacks and prevents background retry loops after an authorization failure. */
class ConnectionGate {
    enum class Stage { IDLE, DISCOVERING, CHECKING, AUTHORIZING, REGISTERING, READY, FAILED, DISCONNECTED }
    var stage = Stage.IDLE
        private set
    var generation = 0
        private set
    val busy: Boolean get() = stage in setOf(Stage.DISCOVERING, Stage.CHECKING, Stage.AUTHORIZING, Stage.REGISTERING)

    fun start(userInitiated: Boolean): Int? {
        if (busy || (!userInitiated && stage in setOf(Stage.READY, Stage.FAILED))) return null
        stage = Stage.DISCOVERING
        return ++generation
    }

    fun matches(attempt: Int, expected: Stage): Boolean = generation == attempt && stage == expected

    fun advance(attempt: Int, from: Stage, to: Stage): Boolean {
        val allowed = when (from) {
            Stage.DISCOVERING -> to == Stage.CHECKING
            Stage.CHECKING -> to == Stage.AUTHORIZING || to == Stage.REGISTERING
            Stage.AUTHORIZING -> to == Stage.REGISTERING
            Stage.REGISTERING -> to == Stage.READY
            else -> false
        }
        if (!allowed || !matches(attempt, from)) return false
        stage = to
        return true
    }

    fun fail(attempt: Int): Boolean {
        if (attempt != generation) return false
        stage = Stage.FAILED
        generation++
        return true
    }

    fun disconnected() {
        generation++
        stage = Stage.DISCONNECTED
    }
}
