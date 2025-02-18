package com.nxoim.sewithvelocity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun SampleTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme())
        DarkColorScheme
    else
        LightColorScheme

    MaterialTheme(
        colorScheme,
        typography = Typography,
        content = content
    )
}