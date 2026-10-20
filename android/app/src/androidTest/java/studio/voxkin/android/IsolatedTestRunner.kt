package studio.voxkin.android

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.AndroidJUnitRunner

/** UI tests must never automatically contact the phone owner's saved gateway. */
class IsolatedTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        MainActivity.modelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceModel(targetContext.applicationContext as Application, automaticConnection = false).apply {
                    gateway = "wss://ui-test-${java.util.UUID.randomUUID()}.example.com"
                    restricted = false
                } as T
            }
        }
        super.onCreate(arguments)
    }
}
