package com.nxoim.sewithvelocity

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.nxoim.sewithvelocity.ui.theme.SampleTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        SampleTheme {
            App()
        }
    }
}