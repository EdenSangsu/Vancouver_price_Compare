package com.ansimdrive.demo.safety

/**
 * 사고 의심 상황을 판단하는 상태 기계. 안드로이드 의존성이 없어 JVM 단위 테스트가 가능하다.
 *
 * 규칙
 * 1. 에어백 전개 → 즉시 자녀 알림.
 * 2. 고속(40km/h 이상)에서 급감속(초당 25km/h 이상, 약 0.7G) 후 멈춰서 5초간 움직임이 없으면
 *    "괜찮으세요?"라고 묻는다. 30초 안에 응답이 없으면 자녀 알림.
 * 3. 비상등을 켜고 천천히 서는 정차(장보기 등)는 급감속이 아니므로 아무 일도 일어나지 않는다.
 */
class SafetyMonitor(
    private val minSpeedBeforeBrakeKmh: Float = 40f,
    private val hardBrakeKmhPerSec: Float = 25f,
    private val stoppedKmh: Float = 5f,
    private val stillAfterBrakeMs: Long = 5_000,
    val responseTimeoutMs: Long = 30_000,
    private val windowMs: Long = 3_000,
) {
    enum class Reason { AIRBAG, NO_RESPONSE, DRIVER_REQUESTED }

    sealed interface State {
        data object Normal : State
        data class HardBrakeDetected(val atMs: Long) : State
        data class Asking(val sinceMs: Long) : State
        data class Alerted(val reason: Reason, val atMs: Long) : State
    }

    private data class Sample(val atMs: Long, val kmh: Float)

    private val samples = ArrayDeque<Sample>()

    var state: State = State.Normal
        private set

    fun onSpeed(nowMs: Long, kmh: Float): State {
        samples.addLast(Sample(nowMs, kmh))
        while (samples.isNotEmpty() && nowMs - samples.first().atMs > windowMs) samples.removeFirst()

        when (val s = state) {
            State.Normal -> if (kmh <= stoppedKmh && wasHardBrake(nowMs, kmh)) {
                state = State.HardBrakeDetected(nowMs)
            }
            is State.HardBrakeDetected -> if (kmh > stoppedKmh) {
                // 다시 출발했으면 정상 주행으로 본다
                state = State.Normal
            }
            else -> Unit
        }
        return onTick(nowMs)
    }

    fun onAirbagDeployed(nowMs: Long): State {
        if (state !is State.Alerted) state = State.Alerted(Reason.AIRBAG, nowMs)
        return state
    }

    fun onTick(nowMs: Long): State {
        when (val s = state) {
            is State.HardBrakeDetected -> if (nowMs - s.atMs >= stillAfterBrakeMs) {
                state = State.Asking(nowMs)
            }
            is State.Asking -> if (nowMs - s.sinceMs >= responseTimeoutMs) {
                state = State.Alerted(Reason.NO_RESPONSE, nowMs)
            }
            else -> Unit
        }
        return state
    }

    fun secondsLeftToRespond(nowMs: Long): Int {
        val s = state as? State.Asking ?: return 0
        return ((responseTimeoutMs - (nowMs - s.sinceMs) + 999) / 1000).toInt().coerceAtLeast(0)
    }

    /** 운전자가 "괜찮아요"를 누르거나 말함 */
    fun onDriverOk(): State {
        if (state is State.Asking || state is State.HardBrakeDetected) state = State.Normal
        return state
    }

    /** 운전자가 "도와주세요"를 누르거나 말함 */
    fun onDriverNeedsHelp(nowMs: Long): State {
        if (state !is State.Alerted) state = State.Alerted(Reason.DRIVER_REQUESTED, nowMs)
        return state
    }

    fun reset() {
        samples.clear()
        state = State.Normal
    }

    private fun wasHardBrake(nowMs: Long, kmh: Float): Boolean = samples.any { s ->
        val dtSec = (nowMs - s.atMs) / 1000f
        s.kmh >= minSpeedBeforeBrakeKmh && dtSec > 0f && (s.kmh - kmh) / dtSec >= hardBrakeKmhPerSec
    }
}
