package com.nxoim.sewithvelocity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.nxoim.sewithvelocity.ui.theme.SampleTheme

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            enableEdgeToEdge()

            SampleTheme {
                Surface {
                    App()
                }
            }
        }
    }
}