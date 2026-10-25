package com.example.fitness_plan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness_plan.data.model.MuscleGroup

private val LightColors = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F0EC),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = Color(0xFF4A6460),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDE9),
    onSecondaryContainer = Color(0xFF06201C),
    // 橙色：临时动作等需要提醒的状态
    tertiary = Color(0xFFE07000),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7CC),
    onTertiaryContainer = Color(0xFF4A2500),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1C2024),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF1C2024),
    surfaceVariant = Color(0xFFECEFF2),
    onSurfaceVariant = Color(0xFF68717B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F4),
    surfaceContainerHighest = Color(0xFFE8EBEE),
    outline = Color(0xFFC9CFD6),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFD93A3A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00201B),
    primaryContainer = Color(0xFF0F3D37),
    onPrimaryContainer = Color(0xFFB3E8E1),
    secondary = Color(0xFFB1CCC7),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF1F3B37),
    onSecondaryContainer = Color(0xFFCDE8E3),
    tertiary = Color(0xFFFFA64D),
    onTertiary = Color(0xFF2E1600),
    tertiaryContainer = Color(0xFF4A2A06),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE5E7E9),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFE5E7E9),
    surfaceVariant = Color(0xFF262A2E),
    onSurfaceVariant = Color(0xFF9BA4AD),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF1A1D20),
    surfaceContainer = Color(0xFF1A1D20),
    surfaceContainerHigh = Color(0xFF22262A),
    surfaceContainerHighest = Color(0xFF2B3035),
    outline = Color(0xFF4A5157),
    outlineVariant = Color(0xFF2E3338),
    error = Color(0xFFFF8A80),
)

/** 状态色：完成、进行中等，不属于 Material 标准色板。 */
@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val card: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color(0xFF2E9E6A), Color(0xFFE2F4EA), Color.White)
}

private val LightExtra = ExtraColors(Color(0xFF2E9E6A), Color(0xFFE4F5EC), Color.White)
private val DarkExtra = ExtraColors(Color(0xFF5CCB94), Color(0xFF173226), Color(0xFF1A1D20))

/** 各肌群的品牌色，用于徽章和雷达图。 */
fun MuscleGroup.color(): Color = when (this) {
    MuscleGroup.CHEST -> Color(0xFF1E88E5)
    MuscleGroup.BACK -> Color(0xFF2E9E4F)
    MuscleGroup.LEGS -> Color(0xFFF4511E)
    MuscleGroup.SHOULDERS -> Color(0xFF8E24AA)
    MuscleGroup.ARMS -> Color(0xFFE53935)
    MuscleGroup.CORE -> Color(0xFFE09A00)
    MuscleGroup.OTHER -> Color(0xFF78909C)
}

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** 训练数值专用：粗体、等宽数字。 */
val NumberStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FitnessTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
