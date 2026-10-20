package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HapticTest {
    private fun state(m: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val s = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(m) as MutableState<Any>
        s.value = value
    }
    private fun field(m: VoiceModel, name: String, value: Any) = VoiceModel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(m,value)
    private fun fixture(block: (VoiceModel, MutableList<HapticCue>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m=VoiceModel(ApplicationProvider.getApplicationContext<Application>(),false)
            m.gateway="wss://haptics-${UUID.randomUUID()}.example.com"; m.restricted=false
            state(m,"connected",true); state(m,"agent","main")
            val events=mutableListOf<HapticCue>(); m.haptics.attach { events.add(it) }
            try { block(m,events) } finally { m.disconnect() }
        }
    }
    @Test fun sendOnlyAfterValidationAndInternalTerminalCleanupIsSilent() = fixture { m, events ->
        var run=""
        field(m,"transport",object: Transport {
            override suspend fun request(method:String,params:JSONObject):JSONObject {
                if(method=="chat.send") run=params.getString("idempotencyKey")
                return obj("runId" to run)
            }
            override fun close() {}
        })
        m.send(); m.draft="x".repeat(4001); m.send(); assertTrue(events.isEmpty())
        m.draft="Hello"; m.send(); m.send()
        assertEquals(listOf(HapticCue.SEND),events)
        val event=VoiceModel::class.java.getDeclaredMethod("event",String::class.java,JSONObject::class.java).apply { isAccessible=true }
        event.invoke(m,"chat",obj("runId" to run,"sessionKey" to m.selectedConversationId,"state" to "final","message" to obj("role" to "assistant","text" to "Done")))
        m.stopAudio(); m.cancelTurn(); m.disconnect()
        assertEquals(listOf(HapticCue.SEND),events)
    }
    @Test fun microphoneReadyIsOncePerActiveCaptureAndRejectsLateCallbacks() = fixture { m, events ->
        state(m,"recording",true)
        m.microphoneBecameReady(0); m.microphoneBecameReady(0)
        assertEquals(listOf(HapticCue.MICROPHONE_READY),events)
        state(m,"recording",false); state(m,"settling",true); state(m,"microphoneReady",false)
        m.microphoneBecameReady(0)
        field(m,"speechGeneration",1); state(m,"recording",true); state(m,"settling",false)
        m.microphoneBecameReady(0); assertEquals(1,events.size)
        m.microphoneBecameReady(1); assertEquals(2,events.size)
        m.stopRecording(true); m.microphoneBecameReady(1); assertEquals(2,events.size)
    }
    @Test fun acceptedHandsFreeAndExplicitStopsEmitOnceWithoutNestedCleanupCues() = fixture { m, events ->
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        state(m,"speechSource","gateway"); state(m,"gatewaySpeechAvailable",true); m.speechConsent=true
        field(m,"handsFree",HandsFreeSession(scope,{true},{},{},{ state(m,"conversationMode",it) }))
        try {
            m.startHandsFree("stale"); assertTrue(events.isEmpty())
            m.startHandsFree(m.handsFreeStartToken()); m.startHandsFree(m.handsFreeStartToken())
            assertEquals(listOf(HapticCue.HANDS_FREE_START),events)
            m.endHandsFreeByUser(); m.endHandsFreeByUser()
            assertEquals(listOf(HapticCue.HANDS_FREE_START,HapticCue.STOP),events)
            m.stopAudio(); m.stopAudioByUser()
            assertEquals(listOf(HapticCue.HANDS_FREE_START,HapticCue.STOP,HapticCue.STOP),events)
        } finally { scope.cancel() }
    }
}
