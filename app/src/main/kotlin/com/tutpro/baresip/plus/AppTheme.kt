package com.tutpro.baresip.plus

import android.app.Activity
import android.os.Build.VERSION
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
    val context = LocalContext.current
    val useDarkTheme by remember { BaresipService.darkTheme }
    val useDynamicColors by remember { BaresipService.dynamicColors }

    val useActualDynamicColors = useDynamicColors && VERSION.SDK_INT >= 31
    Log.d(TAG, "Use actual dynamic colors: $useActualDynamicColors")

    val colorScheme = when {
        useActualDynamicColors -> {
            if (useDarkTheme || isSystemInDarkTheme())
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }
        useDarkTheme || isSystemInDarkTheme() -> darkColorScheme()
        else -> lightColorScheme()
    }

    val basePalette = if (useDarkTheme) DarkCustomColors else LightCustomColors

    val customColorsPalette = basePalette.copy(
            background = colorScheme.background,
            onBackground = if (useActualDynamicColors)
                colorScheme.onBackground
            else
                basePalette.onBackground,
            cardBackground = colorScheme.surfaceVariant,
            textFieldBackground = colorScheme.surfaceVariant,
            primary = if (useActualDynamicColors)
                colorScheme.primary
            else
                basePalette.primary,
            onPrimary = if (useActualDynamicColors)
                colorScheme.onPrimary
            else
                basePalette.onPrimary
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
