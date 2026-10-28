package com.ansimdrive.demo

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansimdrive.demo.safety.SafetyMonitor.State
import com.ansimdrive.demo.ui.AnsimDriveApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: DriveViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val asking = ui.safety is State.Asking
            val alerted = ui.safety is State.Alerted
            LaunchedEffect(asking) {
                if (asking) speak("괜찮으세요? 괜찮으시면 괜찮아요 버튼을 눌러 주세요.")
            }
            LaunchedEffect(alerted) {
                if (alerted) speak("${DemoData.childName}님에게 알렸어요. 곧 연락이 올 거예요.")
            }
            AnsimDriveApp(ui, vm)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ansim")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
