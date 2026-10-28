package com.ansimdrive.demo.safety

import com.ansimdrive.demo.safety.SafetyMonitor.Reason
import com.ansimdrive.demo.safety.SafetyMonitor.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyMonitorTest {

    /** from km/h 에서 to km/h 까지 durationMs 동안 100ms 간격으로 선형 감속 */
    private fun SafetyMonitor.drive(startMs: Long, from: Float, to: Float, durationMs: Long): Long {
        var t = startMs
        val steps = (durationMs / 100).toInt()
        for (i in 1..steps) {
            t += 100
            onSpeed(t, from + (to - from) * i / steps)
        }
        return t
    }

    private fun SafetyMonitor.hold(startMs: Long, kmh: Float, durationMs: Long): Long {
        var t = startMs
        while (t < startMs + durationMs) {
            t += 250
            onSpeed(t, kmh)
        }
        return t
    }

    @Test
    fun airbagAlertsImmediately() {
        val m = SafetyMonitor()
        m.hold(0, 60f, 2_000)
        assertEquals(State.Alerted(Reason.AIRBAG, 2_000), m.onAirbagDeployed(2_000))
    }

    @Test
    fun gentleStopWithHazardLightsDoesNothing() {
        val m = SafetyMonitor()
        var t = m.hold(0, 60f, 3_000)
        t = m.drive(t, 60f, 0f, 10_000) // 초당 6km/h 감속
        m.hold(t, 0f, 120_000)
        assertEquals(State.Normal, m.state)
    }

    @Test
    fun hardBrakeThenStillAsksThenAlertsAfterTimeout() {
        val m = SafetyMonitor()
        var t = m.hold(0, 60f, 3_000)
        t = m.drive(t, 60f, 0f, 1_500) // 초당 40km/h 감속
        assertTrue(m.state is State.HardBrakeDetected)
        t = m.hold(t, 0f, 5_000)
        assertTrue(m.state is State.Asking)
        t = m.hold(t, 0f, 30_000)
        assertTrue(m.state is State.Alerted)
        assertEquals(Reason.NO_RESPONSE, (m.state as State.Alerted).reason)
    }

    @Test
    fun hardBrakeThenDrivingOnIsNormal() {
        val m = SafetyMonitor()
        var t = m.hold(0, 60f, 3_000)
        t = m.drive(t, 60f, 0f, 1_500)
        t = m.hold(t, 0f, 2_000)
        m.drive(t, 0f, 30f, 3_000)
        assertEquals(State.Normal, m.state)
    }

    @Test
    fun driverSaysOkCancelsQuestion() {
        val m = SafetyMonitor()
        var t = m.hold(0, 60f, 3_000)
        t = m.drive(t, 60f, 0f, 1_500)
        t = m.hold(t, 0f, 6_000)
        assertTrue(m.state is State.Asking)
        assertEquals(State.Normal, m.onDriverOk())
        m.hold(t, 0f, 60_000)
        assertEquals(State.Normal, m.state)
    }

    @Test
    fun hardBrakeFromLowSpeedIsIgnored() {
        val m = SafetyMonitor()
        var t = m.hold(0, 30f, 3_000)
        t = m.drive(t, 30f, 0f, 800)
        m.hold(t, 0f, 10_000)
        assertEquals(State.Normal, m.state)
    }
}
