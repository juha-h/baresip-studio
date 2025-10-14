package com.tutpro.baresip

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val useDarkTheme by remember { BaresipService.darkTheme }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme)
                dynamicDarkColorScheme(context = LocalContext.current)
            else
                if (isSystemInDarkTheme())
                    dynamicDarkColorScheme(context = LocalContext.current)
                else
                    dynamicLightColorScheme(context = LocalContext.current)
        }
        useDarkTheme -> darkColorScheme()
        else ->
            if (isSystemInDarkTheme())
                darkColorScheme()
            else
                lightColorScheme()
    }

    val customColorsPalette =
        (if (useDarkTheme) DarkCustomColors else LightCustomColors).copy(
            background = colorScheme.background,
            cardBackground = colorScheme.surfaceVariant,
            textFieldBackground = colorScheme.surfaceVariant
        )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // A common threshold for luminance is 0.5. Colors with luminance > 0.5 are considered light.
            val isBackgroundEffectivelyLight = ColorUtils.calculateLuminance(customColorsPalette.background.toArgb()) > 0.5

            insetsController.isAppearanceLightStatusBars = isBackgroundEffectivelyLight
            insetsController.isAppearanceLightNavigationBars = isBackgroundEffectivelyLight
        }
    }

    CompositionLocalProvider(
        LocalCustomColors provides customColorsPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme, // MaterialTheme still uses the "normal" colorScheme
            content = content
        )
    }

}
