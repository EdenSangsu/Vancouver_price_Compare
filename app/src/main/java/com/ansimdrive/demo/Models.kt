package com.ansimdrive.demo

enum class RouteType { EASY, FAST }

data class Destination(val name: String, val address: String, val emoji: String)

data class RoutePlan(
    val type: RouteType,
    val title: String,
    val minutes: Int,
    val km: Double,
    val highwayKm: Double,
    val turns: Int,
    val summary: String,
)

data class ChildMessage(
    val time: String,
    val title: String,
    val body: String,
    val urgent: Boolean = false,
)

/** 데모용 고정 데이터. 실차에서는 Pleos NaviHelper 경로 탐색 결과로 대체한다. */
object DemoData {
    const val parentName = "어머니"
    const val childName = "지은"

    val destinations = listOf(
        Destination("서울대학교병원", "종로구 대학로 101", "🏥"),
        Destination("동네 마트", "성북구 보문로 12", "🛒"),
        Destination("지은이네 집", "마포구 월드컵로 34", "🏠"),
    )

    /**
     * Pleos RouteOption 에는 "고속도로 회피"가 없다. 쉬운 길은 COMFORTABLE(편안한 길) 옵션으로
     * 탐색한 뒤 고속도로·자동차전용도로 구간 길이를 따로 계산해 0km 경로를 우선 보여준다.
     */
    fun routesFor(destination: Destination): List<RoutePlan> {
        val base = when (destination.name) {
            "동네 마트" -> 9
            "지은이네 집" -> 31
            else -> 24
        }
        return listOf(
            RoutePlan(
                type = RouteType.EASY,
                title = "쉬운 길",
                minutes = base + 7,
                km = base * 0.55,
                highwayKm = 0.0,
                turns = 4,
                summary = "고속도로 없음 · 시내 도로 · 회전 적음",
            ),
            RoutePlan(
                type = RouteType.FAST,
                title = "빠른 길",
                minutes = base,
                km = base * 0.75,
                highwayKm = base * 0.45,
                turns = 9,
                summary = "도시고속도로 포함 · 합류 구간 3곳",
            ),
        )
    }
}
