package com.tutpro.baresip

import android.app.Activity
import android.os.Build.VERSION
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
        useDarkTheme || isSystemInDarkTheme() -> darkColorScheme(
            primary = dark_primary,
            onPrimary = dark_on_primary,
            primaryContainer = dark_primary_container,
            onPrimaryContainer = dark_on_primary_container,
            secondary = dark_secondary,
            onSecondary = dark_on_secondary,
            secondaryContainer = dark_secondary_container,
            onSecondaryContainer = dark_on_secondary_container,
            tertiary = dark_tertiary,
            onTertiary = dark_on_tertiary,
            tertiaryContainer = dark_tertiary_container,
            onTertiaryContainer = dark_on_tertiary_container,
            error = dark_error,
            onError = dark_on_error,
            errorContainer = dark_error_container,
            onErrorContainer = dark_on_error_container,
            background = dark_background,
            onBackground = dark_on_background,
            surface = dark_surface,
            onSurface = dark_on_surface,
            surfaceVariant = dark_surfaceVariant,
            onSurfaceVariant = dark_on_surfaceVariant,
            outline = dark_outline
        )
        else -> lightColorScheme(
            primary = light_primary,
            onPrimary = light_on_primary,
            primaryContainer = light_primary_container,
            onPrimaryContainer = light_on_primary_container,
            secondary = light_secondary,
            onSecondary = light_on_secondary,
            secondaryContainer = light_secondary_container,
            onSecondaryContainer = light_on_secondary_container,
            tertiary = light_tertiary,
            onTertiary = light_on_tertiary,
            tertiaryContainer = light_tertiary_container,
            onTertiaryContainer = light_on_tertiary_container,
            error = light_error,
            onError = light_on_error,
            errorContainer = light_error_container,
            onErrorContainer = light_on_error_container,
            background = light_background,
            onBackground = light_on_background,
            surface = light_surface,
            onSurface = light_on_surface,
            surfaceVariant = light_surfaceVariant,
            onSurfaceVariant = light_on_surfaceVariant,
            outline = light_outline
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            val isBackgroundEffectivelyLight = ColorUtils.calculateLuminance(colorScheme.background.toArgb()) > 0.5
            insetsController.isAppearanceLightStatusBars = isBackgroundEffectivelyLight
            insetsController.isAppearanceLightNavigationBars = isBackgroundEffectivelyLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
