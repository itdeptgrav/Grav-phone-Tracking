package com.personal.callrecorder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.personal.callrecorder.ui.navigation.AppRoot
import com.personal.callrecorder.ui.theme.PersonalCallRecorderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Extends FragmentActivity so androidx.biometric's
 * BiometricPrompt can attach to it.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalCallRecorderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}
