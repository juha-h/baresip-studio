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
    val isDark = useDarkTheme || isSystemInDarkTheme()

    val colorScheme = when {
        useActualDynamicColors -> {
            if (isDark)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = PrimaryDark,
            onPrimary = OnPrimaryDark,
            primaryContainer = PrimaryContainerDark,
            onPrimaryContainer = OnPrimaryContainerDark,
            secondary = SecondaryDark,
            onSecondary = OnSecondaryDark,
            secondaryContainer = SecondaryContainerDark,
            onSecondaryContainer = OnSecondaryContainerDark,
            tertiary = TertiaryDark,
            onTertiary = OnTertiaryDark,
            tertiaryContainer = TertiaryContainerDark,
            onTertiaryContainer = OnTertiaryContainerDark,
            error = ErrorDark,
            onError = OnErrorDark,
            errorContainer = ErrorContainerDark,
            onErrorContainer = OnErrorContainerDark,
            background = BackgroundDark,
            onBackground = OnBackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = OnSurfaceVariantDark,
            surfaceContainer = SurfaceContainerDark,
            surfaceContainerLow = SurfaceContainerLowDark,
            surfaceContainerHigh = SurfaceContainerHighDark,
            surfaceContainerLowest = SurfaceContainerLowestDark,
            surfaceContainerHighest = SurfaceContainerHighestDark,
            outline = OutlineDark,
            outlineVariant = OutlineVariantDark
        )
        else -> lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            secondary = Secondary,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = OnSecondaryContainer,
            tertiary = Tertiary,
            onTertiary = OnTertiary,
            tertiaryContainer = TertiaryContainer,
            onTertiaryContainer = OnTertiaryContainer,
            error = Error,
            onError = OnError,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer,
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            surfaceContainer = SurfaceContainer,
            surfaceContainerLow = SurfaceContainerLow,
            surfaceContainerHigh = SurfaceContainerHigh,
            surfaceContainerLowest = SurfaceContainerLowest,
            surfaceContainerHighest = SurfaceContainerHighest,
            outline = Outline,
            outlineVariant = OutlineVariant
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
