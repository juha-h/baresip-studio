package com.tutpro.baresip.plus

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = remember { BaresipService.darkTheme }

    // "normal" palette, nothing change here
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme.value) dynamicDarkColorScheme(context = LocalContext.current)
            else dynamicLightColorScheme(context = LocalContext.current)
        }
        darkTheme.value -> darkColorScheme()
        else -> lightColorScheme()
    }

    // logic for which custom palette to use
    val customColorsPalette =
        if (darkTheme.value) DarkCustomColors
        else LightCustomColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val decorView = window.decorView

            // Ensure insets are applied correctly
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Handle the status bar appearance
            val insetsController = WindowCompat.getInsetsController(window, decorView)
            window.statusBarColor = customColorsPalette.background.toArgb()
            window.navigationBarColor = customColorsPalette.background.toArgb()
            insetsController.apply {
                isAppearanceLightStatusBars = !darkTheme.value
                isAppearanceLightNavigationBars = !darkTheme.value
            }
        }
    }

    // here is the important point, where you will expose custom objects
    CompositionLocalProvider(
        LocalCustomColors provides customColorsPalette // our custom palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme, // the MaterialTheme still uses the "normal" palette
            content = content
        )
    }

}
