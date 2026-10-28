package com.ansimdrive.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansimdrive.demo.ChildMessage
import com.ansimdrive.demo.DemoData
import com.ansimdrive.demo.DriveViewModel
import com.ansimdrive.demo.RoutePlan
import com.ansimdrive.demo.RouteType
import com.ansimdrive.demo.Screen
import com.ansimdrive.demo.UiState
import com.ansimdrive.demo.safety.SafetyMonitor.Reason
import com.ansimdrive.demo.safety.SafetyMonitor.State
import kotlin.math.hypot

private val Bg = Color(0xFF0F141A)
private val Card = Color(0xFF1B232D)
private val CardHi = Color(0xFF25303C)
private val Ink = Color(0xFFF2F5F8)
private val Muted = Color(0xFF9AA7B4)
private val Safe = Color(0xFF3DDC97)
private val Warn = Color(0xFFFFB020)
private val Danger = Color(0xFFFF5A5F)
private val Highway = Color(0xFF4C8DFF)

@Composable
fun AnsimDriveApp(ui: UiState, vm: DriveViewModel) {
    Row(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (ui.screen) {
                Screen.HOME -> HomeScreen(vm)
                Screen.ROUTES -> RouteScreen(ui, vm)
                Screen.DRIVING -> DrivingScreen(ui, vm)
                Screen.ARRIVED -> ArrivedScreen(ui, vm)
            }
            when (val s = ui.safety) {
                is State.Asking -> AreYouOkOverlay(ui.secondsLeft, vm)
                is State.Alerted -> AlertedOverlay(s.reason, vm)
                else -> Unit
            }
        }
        Spacer(Modifier.width(20.dp))
        ChildPhone(ui.inbox)
    }
}

// ───────────────────────── 홈: 목적지 선택 ─────────────────────────

@Composable
private fun HomeScreen(vm: DriveViewModel) {
    Column {
        Text("안심 드라이브", color = Safe, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("어디로 가실까요?", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DemoData.destinations.forEach { d ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Card)
                        .clickable { vm.pickDestination(d) }.padding(24.dp),
                ) {
                    Text(d.emoji, fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(d.name, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(d.address, color = Muted, fontSize = 18.sp)
                }
            }
        }
    }
}

// ───────────────────────── 경로 선택 ─────────────────────────

@Composable
private fun RouteScreen(ui: UiState, vm: DriveViewModel) {
    val d = ui.destination ?: return
    Column {
        Text("← 처음으로", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { vm.backHome() })
        Text("${d.emoji} ${d.name}", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ui.routes.forEach { r -> RouteCard(r, Modifier.weight(1f)) { vm.start(r) } }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Card).clickable { vm.toggleShare() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (ui.shareWithChild) Safe else CardHi))
            Spacer(Modifier.width(14.dp))
            Text(
                if (ui.shareWithChild) "${DemoData.childName}에게 가는 곳 알려주기 켜짐" else "${DemoData.childName}에게 알려주기 꺼짐",
                color = Ink, fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun RouteCard(r: RoutePlan, modifier: Modifier, onClick: () -> Unit) {
    val easy = r.type == RouteType.EASY
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(Card)
            .border(if (easy) 3.dp else 0.dp, if (easy) Safe else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, color = if (easy) Safe else Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (easy) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "추천", color = Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Safe).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text("${r.minutes}분 · ${"%.1f".format(r.km)}km", color = Ink, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        RouteMap(r.type, progress = -1f, modifier = Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "고속도로 ${r.highwayKm.toInt()}km",
            color = if (r.highwayKm == 0.0) Safe else Highway, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        Text(r.summary, color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "이 길로 출발", color = if (easy) Bg else Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (easy) Safe else CardHi).padding(14.dp),
        )
    }
}

// ───────────────────────── 주행 ─────────────────────────

@Composable
private fun DrivingScreen(ui: UiState, vm: DriveViewModel) {
    val r = ui.route ?: return
    val d = ui.destination ?: return
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.emoji} ${d.name}", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${r.title} · 고속도로 ${r.highwayKm.toInt()}km", color = Safe, fontSize = 20.sp)
            }
            if (ui.shareWithChild) Pill("📍 ${DemoData.childName}에게 공유 중", Safe)
            if (ui.hazardOn) { Spacer(Modifier.width(8.dp)); Pill("⚠ 비상등", Warn) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card)) {
            RouteMap(r.type, ui.progress, Modifier.fillMaxSize().padding(24.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("${ui.speedKmh.toInt()}", color = Ink, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("km/h", color = Muted, fontSize = 18.sp)
            }
            Text(
                "남은 시간 ${(r.minutes * (1f - ui.progress)).toInt()}분",
                color = Ink, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // 시연용 버튼 (실차에서는 차량 신호로 대체)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("시연용", color = Muted, fontSize = 16.sp)
            DemoButton("비상등 정차", Warn) { vm.simulateHazardStop() }
            DemoButton("다시 출발", Safe) { vm.resumeDriving() }
            DemoButton("급정지", Danger) { vm.simulateHardBrake() }
            DemoButton("에어백", Danger) { vm.simulateAirbag() }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun DemoButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 16.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 지도 SDK 없이 그린 단순 경로도. 쉬운 길은 시내 도로, 빠른 길은 파란 고속도로 구간을 포함. */
@Composable
private fun RouteMap(type: RouteType, progress: Float, modifier: Modifier) {
    val pts = if (type == RouteType.EASY) {
        listOf(0f to 0.85f, 0.2f to 0.85f, 0.2f to 0.55f, 0.5f to 0.55f, 0.5f to 0.3f, 0.8f to 0.3f, 0.8f to 0.12f, 0.95f to 0.12f)
    } else {
        listOf(0f to 0.85f, 0.15f to 0.85f, 0.3f to 0.65f, 0.75f to 0.2f, 0.85f to 0.2f, 0.95f to 0.12f)
    }
    Canvas(modifier) {
        val o = pts.map { (x, y) -> Offset(x * size.width, y * size.height) }
        val path = Path().apply { moveTo(o[0].x, o[0].y); o.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, Safe.copy(alpha = 0.9f), style = Stroke(10f, cap = StrokeCap.Round))
        if (type == RouteType.FAST) drawLine(Highway, o[2], o[3], strokeWidth = 14f, cap = StrokeCap.Round)
        drawCircle(Danger, 14f, o.last())
        if (progress >= 0f) drawCircle(Ink, 16f, pointAlong(o, progress))
    }
}

private fun pointAlong(o: List<Offset>, t: Float): Offset {
    val segs = o.zipWithNext()
    val total = segs.sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()
    var remain = total * t.coerceIn(0f, 1f)
    for ((a, b) in segs) {
        val len = hypot(b.x - a.x, b.y - a.y)
        if (remain <= len) return a + (b - a) * (remain / len)
        remain -= len
    }
    return o.last()
}

// ───────────────────────── 도착 ─────────────────────────

@Composable
private fun ArrivedScreen(ui: UiState, vm: DriveViewModel) {
    CenterMessage(
        title = "도착했어요",
        body = "${ui.destination?.name ?: ""}에 안전하게 도착했어요." +
            if (ui.shareWithChild) "\n${DemoData.childName}에게도 알렸어요." else "",
        color = Safe,
    ) { BigButton("처음으로", Safe, Modifier.width(320.dp)) { vm.backHome() } }
}

// ───────────────────────── 안전 확인 ─────────────────────────

@Composable
private fun AreYouOkOverlay(secondsLeft: Int, vm: DriveViewModel) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Bg.copy(alpha = 0.96f))) {
        CenterMessage(
            title = "괜찮으세요?",
            body = "갑자기 멈춘 뒤 움직임이 없어요.\n${secondsLeft}초 안에 답이 없으면 ${DemoData.childName}에게 알릴게요.",
            color = Warn,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("괜찮아요", Safe, Modifier.width(300.dp)) { vm.driverOk() }
                BigButton("도와주세요", Danger, Modifier.width(300.dp)) { vm.driverNeedsHelp() }
            }
        }
    }
}

@Composable
private fun AlertedOverlay(reason: Reason, vm: DriveViewModel) {
    val why = when (reason) {
        Reason.AIRBAG -> "에어백이 터진 것을 감지했어요."
        Reason.NO_RESPONSE -> "응답이 없어서 걱정돼요."
        Reason.DRIVER_REQUESTED -> "도움을 요청하셨어요."
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Bg.copy(alpha = 0.96f))) {
        CenterMessage(
            title = "${DemoData.childName}에게 알렸어요",
            body = "$why\n현재 위치와 함께 문자를 보냈어요. 곧 연락이 올 거예요.",
            color = Danger,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("📞 119 전화", Danger, Modifier.width(300.dp)) { }
                BigButton("처음으로", CardHi, Modifier.width(300.dp)) { vm.backHome() }
            }
        }
    }
}

@Composable
private fun CenterMessage(title: String, body: String, color: Color, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = color, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(body, color = Ink, fontSize = 26.sp, textAlign = TextAlign.Center, lineHeight = 36.sp)
        Spacer(Modifier.height(32.dp))
        actions()
    }
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text, color = if (color == CardHi) Ink else Bg, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick).padding(22.dp),
    )
}

// ───────────────────────── 자녀 폰 미리보기 ─────────────────────────

@Composable
private fun ChildPhone(inbox: List<ChildMessage>, width: Dp = 300.dp) {
    Column(
        Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(Color(0xFFF4F6F8))
            .border(6.dp, Color(0xFF2A2F36), RoundedCornerShape(32.dp)).padding(16.dp),
    ) {
        Text("${DemoData.childName}의 폰", color = Color(0xFF5B6673), fontSize = 14.sp)
        Text("안심 드라이브 알림", color = Color(0xFF111418), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (inbox.isEmpty()) {
            Text("아직 알림이 없어요", color = Color(0xFF8A95A1), fontSize = 16.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(inbox) { m ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (m.urgent) Color(0xFFFFE3E4) else Color.White).padding(12.dp),
                ) {
                    Text(m.time, color = Color(0xFF8A95A1), fontSize = 12.sp)
                    Text(
                        m.title, color = if (m.urgent) Color(0xFFC62828) else Color(0xFF111418),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(m.body, color = Color(0xFF3A4450), fontSize = 14.sp)
                    if (m.urgent) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "📞 어머니께 전화", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFC62828)).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
