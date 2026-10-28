package com.ansimdrive.demo.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 앱이 필요로 하는 차량 신호. */
interface VehicleSignals {
    val speedKmh: StateFlow<Float>
    val airbagDeployed: StateFlow<Boolean>
    val hazardOn: StateFlow<Boolean>
}

/*
 * 실차(Pleos Connect) 연동 시 구현 메모
 * - 속도: AAOS Car API CarPropertyManager 의 VehicleSpeed(PERF_VEHICLE_SPEED), 권한 CAR_SPEED
 * - 에어백: Pleos Vehicle SDK Safety 의 registerSeatAirbagDeployed 콜백, 권한 CAR_SAFETY
 * - 위치: Pleos FusedLocation SDK
 * Virtual Console 로 값을 넣을 수 있다면 PleosVehicleSignals 를 만들어 MockVehicle 대신 주입한다.
 */

/** 에뮬레이터에서 시연하기 위한 가상 차량. 화면의 "시연용" 버튼으로 상황을 만든다. */
class MockVehicle(private val scope: CoroutineScope) : VehicleSignals {
    private val _speed = MutableStateFlow(0f)
    private val _airbag = MutableStateFlow(false)
    private val _hazard = MutableStateFlow(false)
    override val speedKmh: StateFlow<Float> = _speed
    override val airbagDeployed: StateFlow<Boolean> = _airbag
    override val hazardOn: StateFlow<Boolean> = _hazard

    private var job: Job? = null

    /** 출발해서 50~60km/h 로 시내 주행 */
    fun startDriving() {
        _hazard.value = false
        _airbag.value = false
        scenario {
            rampTo(55f, perSecond = 12f)
            while (isActive) {
                _speed.value = (_speed.value + Random.nextFloat() * 4f - 2f).coerceIn(48f, 60f)
                delay(500)
            }
        }
    }

    /** 장보기처럼 비상등 켜고 천천히 정차 (알림이 가면 안 되는 상황) */
    fun stopGentlyWithHazard() {
        _hazard.value = true
        scenario { rampTo(0f, perSecond = 6f) }
    }

    /** 급정지 후 움직임 없음 (사고 의심) */
    fun hardBrake() = scenario { rampTo(0f, perSecond = 40f) }

    fun deployAirbag() {
        scenario { rampTo(0f, perSecond = 60f) }
        _airbag.value = true
    }

    fun park() {
        job?.cancel()
        _speed.value = 0f
        _hazard.value = false
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    private suspend fun rampTo(target: Float, perSecond: Float) {
        val stepMs = 100L
        val step = perSecond * stepMs / 1000f
        while (_speed.value != target) {
            val v = _speed.value
            _speed.value = if (v < target) minOf(target, v + step) else maxOf(target, v - step)
            delay(stepMs)
        }
    }
}
