package com.nxoim.sewithvelocity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        typography = Typography,
        content = {
            Surface(content = content)
        }
    )
}