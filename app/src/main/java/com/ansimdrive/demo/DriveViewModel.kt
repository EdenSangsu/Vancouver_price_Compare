package com.ansimdrive.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansimdrive.demo.family.DemoFamilyNotifier
import com.ansimdrive.demo.safety.SafetyMonitor
import com.ansimdrive.demo.safety.SafetyMonitor.Reason
import com.ansimdrive.demo.safety.SafetyMonitor.State
import com.ansimdrive.demo.vehicle.MockVehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Screen { HOME, ROUTES, DRIVING, ARRIVED }

data class UiState(
    val screen: Screen = Screen.HOME,
    val destination: Destination? = null,
    val routes: List<RoutePlan> = emptyList(),
    val route: RoutePlan? = null,
    val shareWithChild: Boolean = true,
    val progress: Float = 0f,
    val speedKmh: Float = 0f,
    val hazardOn: Boolean = false,
    val safety: State = State.Normal,
    val secondsLeft: Int = 0,
    val inbox: List<ChildMessage> = emptyList(),
)

class DriveViewModel : ViewModel() {
    private val vehicle = MockVehicle(viewModelScope)
    private val notifier = DemoFamilyNotifier()
    private val monitor = SafetyMonitor()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private fun now() = LocalTime.now().format(clock)

    init {
        viewModelScope.launch { notifier.inbox.collect { m -> _ui.update { it.copy(inbox = m) } } }
        viewModelScope.launch { vehicle.hazardOn.collect { h -> _ui.update { it.copy(hazardOn = h) } } }
        viewModelScope.launch {
            vehicle.airbagDeployed.collect { deployed ->
                if (deployed) handleSafety(monitor.onAirbagDeployed(System.currentTimeMillis()))
            }
        }
        // 250ms 마다 속도 샘플을 넣고 주행 진행률을 갱신
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                val speed = vehicle.speedKmh.value
                val s = _ui.value
                if (s.screen != Screen.DRIVING) continue
                handleSafety(monitor.onSpeed(System.currentTimeMillis(), speed))
                // 시연용: 60km/h 로 약 70초면 도착
                val progress = (s.progress + speed / 60f / 70f / 4f).coerceAtMost(1f)
                _ui.update { it.copy(speedKmh = speed, progress = progress) }
                if (progress >= 1f && s.safety == State.Normal) arrive()
            }
        }
    }

    fun pickDestination(d: Destination) {
        _ui.update { it.copy(screen = Screen.ROUTES, destination = d, routes = DemoData.routesFor(d)) }
    }

    fun toggleShare() = _ui.update { it.copy(shareWithChild = !it.shareWithChild) }

    fun start(route: RoutePlan) {
        monitor.reset()
        notifier.clear()
        val d = _ui.value.destination ?: return
        _ui.update { it.copy(screen = Screen.DRIVING, route = route, progress = 0f, safety = State.Normal) }
        vehicle.startDriving()
        if (_ui.value.shareWithChild) {
            val eta = LocalTime.now().plusMinutes(route.minutes.toLong()).format(clock)
            notifier.send(
                ChildMessage(
                    now(),
                    "${DemoData.parentName}가 출발하셨어요",
                    "${d.emoji} ${d.name}(으)로 가는 중 · ${route.title}(고속도로 ${route.highwayKm.toInt()}km) · 도착 예정 $eta",
                )
            )
        }
    }

    // ── 시연용 상황 버튼 ──
    fun simulateHazardStop() = vehicle.stopGentlyWithHazard()
    fun simulateHardBrake() = vehicle.hardBrake()
    fun simulateAirbag() = vehicle.deployAirbag()
    fun resumeDriving() = vehicle.startDriving()

    fun driverOk() {
        handleSafety(monitor.onDriverOk())
        vehicle.startDriving()
    }

    fun driverNeedsHelp() = handleSafety(monitor.onDriverNeedsHelp(System.currentTimeMillis()))

    fun backHome() {
        vehicle.park()
        monitor.reset()
        _ui.value = UiState(inbox = _ui.value.inbox)
    }

    private fun arrive() {
        vehicle.park()
        _ui.update { it.copy(screen = Screen.ARRIVED, speedKmh = 0f) }
        val d = _ui.value.destination ?: return
        if (_ui.value.shareWithChild) {
            notifier.send(ChildMessage(now(), "${DemoData.parentName}가 도착하셨어요", "${d.emoji} ${d.name} 에 안전하게 도착"))
        }
    }

    private fun handleSafety(state: State) {
        val before = _ui.value.safety
        _ui.update { it.copy(safety = state, secondsLeft = monitor.secondsLeftToRespond(System.currentTimeMillis())) }
        if (state is State.Alerted && before !is State.Alerted) {
            vehicle.park()
            val why = when (state.reason) {
                Reason.AIRBAG -> "에어백이 터졌어요"
                Reason.NO_RESPONSE -> "급정지 후 30초 동안 응답이 없어요"
                Reason.DRIVER_REQUESTED -> "${DemoData.parentName}가 도움을 요청하셨어요"
            }
            notifier.send(
                ChildMessage(
                    now(),
                    "🚨 ${DemoData.parentName} 차량 사고 의심",
                    "$why. 현재 위치: 성북구 보문로 인근. 지금 바로 전화해 보세요.",
                    urgent = true,
                )
            )
        }
    }
}
