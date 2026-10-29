package fr.citoyende2027.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Defaults = Typography()

// Sizes are in sp, so they follow the system font size and the in-app text size.
// The font family is the system one; a bundled variable font can be set here for the whole app.
internal val CitoyenTypography = Typography(
    // Screen titles ("Candidats", "Comparer"…).
    headlineMedium = Defaults.headlineMedium.copy(
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = Defaults.headlineSmall.copy(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = Defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    // Uppercase section labels are written in the text itself, not forced here.
    labelSmall = Defaults.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

