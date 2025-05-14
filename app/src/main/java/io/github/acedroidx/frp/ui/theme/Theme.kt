package io.github.acedroidx.frp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 原始主题
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// iOS风格主题
private val iOSLightColorScheme = lightColorScheme(
    primary = iOSBlue,
    onPrimary = Color.White,
    secondary = iOSGreen,
    onSecondary = Color.White,
    tertiary = iOSRed,
    onTertiary = Color.White,
    background = iOSGray1,
    surface = Color.White,
    surfaceVariant = Color.White,
    outline = iOSGray2,
    onSurfaceVariant = iOSGray3
)

private val iOSDarkColorScheme = darkColorScheme(
    primary = iOSBlue,
    onPrimary = Color.White,
    secondary = iOSGreen,
    onSecondary = Color.White,
    tertiary = iOSRed,
    onTertiary = Color.White,
    background = iOSGray6,
    surface = iOSGray5,
    surfaceVariant = iOSGray5,
    outline = iOSGray4,
    onSurfaceVariant = iOSGray3
)

@Composable
fun FrpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    useIOSStyle: Boolean = true, // 添加iOS风格选项，默认为true
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 如果启用了动态颜色且系统支持，优先使用动态颜色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 如果启用了iOS风格，使用iOS风格的配色方案
        useIOSStyle -> {
            if (darkTheme) iOSDarkColorScheme else iOSLightColorScheme
        }
        // 否则使用原始配色方案
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}