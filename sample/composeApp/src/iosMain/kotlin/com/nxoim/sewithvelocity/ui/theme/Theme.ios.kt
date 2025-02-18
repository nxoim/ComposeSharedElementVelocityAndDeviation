package com.nxoim.sewithvelocity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        DarkColorScheme,
        typography = Typography,
        content = content
    )
}