package com.hy.wristnav.bridge

import com.hy.wristnav.bridge.ConnectionGate.Stage
import org.junit.Assert.*
import org.junit.Test

class ConnectionGateTest {
    @Test fun cannotRegisterBeforePermissionCheck() {
        val gate = ConnectionGate()
        val attempt = gate.start(true)!!
        assertFalse(gate.advance(attempt, Stage.DISCOVERING, Stage.REGISTERING))
        assertFalse(gate.advance(attempt, Stage.CHECKING, Stage.REGISTERING))
        assertFalse(gate.advance(attempt, Stage.DISCOVERING, Stage.READY))
    }

    @Test fun firstUseWaitsForAuthorizationBeforeBecomingReady() {
        val gate = ConnectionGate()
        val attempt = gate.start(true)!!
        assertTrue(gate.advance(attempt, Stage.DISCOVERING, Stage.CHECKING))
        assertTrue(gate.advance(attempt, Stage.CHECKING, Stage.AUTHORIZING))
        assertNull(gate.start(true)) // Repeated taps must not create another consent request.
        assertFalse(gate.advance(attempt, Stage.AUTHORIZING, Stage.READY))
        assertTrue(gate.advance(attempt, Stage.AUTHORIZING, Stage.REGISTERING))
        assertTrue(gate.advance(attempt, Stage.REGISTERING, Stage.READY))
    }

    @Test fun deniedPermissionRequiresExplicitRetryAndRejectsLateCallbacks() {
        val gate = ConnectionGate()
        val first = gate.start(true)!!
        gate.advance(first, Stage.DISCOVERING, Stage.CHECKING)
        gate.advance(first, Stage.CHECKING, Stage.AUTHORIZING)
        assertTrue(gate.fail(first))
        assertNull(gate.start(false)) // Navigation updates cannot repeat failed authorization.
        assertFalse(gate.advance(first, Stage.AUTHORIZING, Stage.REGISTERING))
        val second = gate.start(true)!!
        assertFalse(gate.fail(first)) // A late failure cannot cancel the new attempt.
        assertTrue(gate.matches(second, Stage.DISCOVERING))
    }

    @Test fun reconnectInvalidatesPendingCallbacksAndRechecksPermission() {
        val gate = ConnectionGate()
        val first = gate.start(true)!!
        gate.advance(first, Stage.DISCOVERING, Stage.CHECKING)
        gate.advance(first, Stage.CHECKING, Stage.REGISTERING)
        gate.disconnected()
        assertFalse(gate.advance(first, Stage.REGISTERING, Stage.READY))
        val second = gate.start(false)!!
        assertTrue(gate.advance(second, Stage.DISCOVERING, Stage.CHECKING))
        assertTrue(gate.advance(second, Stage.CHECKING, Stage.REGISTERING))
        assertTrue(gate.advance(second, Stage.REGISTERING, Stage.READY))
        assertNull(gate.start(false))
    }
}
