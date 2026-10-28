package com.ansimdrive.demo.family

import com.ansimdrive.demo.ChildMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 자녀 폰으로 메시지를 보낸다.
 * 실서비스: 차량 앱 → 안심 드라이브 서버 → FCM 푸시/문자로 자녀 앱에 전달.
 * (차량에서 직접 전화·문자 발신은 제한될 수 있어 서버를 거친다.)
 */
interface FamilyNotifier {
    fun send(message: ChildMessage)
}

/** 데모용: 보낸 메시지를 화면 오른쪽 "자녀 폰" 미리보기에 띄운다. */
class DemoFamilyNotifier : FamilyNotifier {
    private val _inbox = MutableStateFlow<List<ChildMessage>>(emptyList())
    val inbox: StateFlow<List<ChildMessage>> = _inbox

    override fun send(message: ChildMessage) {
        _inbox.value = listOf(message) + _inbox.value
    }

    fun clear() {
        _inbox.value = emptyList()
    }
}
