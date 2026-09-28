package com.pulse.apitester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pulse.apitester.ui.PulseApp
import com.pulse.apitester.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                PulseApp()
            }
        }
    }
}
