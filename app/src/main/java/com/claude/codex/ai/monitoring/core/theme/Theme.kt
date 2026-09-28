package com.claude.codex.ai.monitoring.core.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.claude.codex.ai.monitoring.core.utils.findActivity

/**
 * App theme. It also sets the system-bar icon appearance to match [darkTheme], so the status bar
 * follows the selected theme (System / Dark / Light) and not only the system setting. Screens draw
 * their own background edge-to-edge behind the bars, so the bar colour always matches the theme.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = appColors.brandStart,
            onPrimary = appColors.onBrand,
            secondary = appColors.brandEnd,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.outline,
            error = appColors.error,
        )
    } else {
        lightColorScheme(
            primary = appColors.brandStart,
            onPrimary = appColors.onBrand,
            secondary = appColors.brandEnd,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceElevated,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.outline,
            error = appColors.error,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Accessors for the app's own tokens, mirroring `MaterialTheme.colorScheme`. */
object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
