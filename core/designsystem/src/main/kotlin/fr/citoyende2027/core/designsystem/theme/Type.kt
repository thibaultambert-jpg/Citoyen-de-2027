package fr.citoyende2027.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.citoyende2027.core.designsystem.R

/** Roboto Flex (OFL, bundled so it works offline): one variable file, weight and width axes. */
private fun flex(weight: FontWeight, width: Float = 100f) = Font(
    resId = R.font.roboto_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(width)),
)

private val BodyFamily = FontFamily(
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Bold),
)

// Wider letterforms for screen and section titles.
private val TitleFamily = FontFamily(
    flex(FontWeight.SemiBold, width = 110f),
    flex(FontWeight.Bold, width = 112f),
)

private val Defaults = Typography()

private fun TextStyle.body() = copy(fontFamily = BodyFamily)

// Sizes are in sp, so they follow the system font size and the in-app text size.
internal val CitoyenTypography = Typography(
    displayLarge = Defaults.displayLarge.body(),
    displayMedium = Defaults.displayMedium.body(),
    displaySmall = Defaults.displaySmall.body(),
    // Screen titles ("Candidats", "Comparer"…).
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = TitleFamily, fontWeight = FontWeight.Bold),
    headlineMedium = Defaults.headlineMedium.copy(
        fontFamily = TitleFamily,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = Defaults.headlineSmall.copy(
        fontFamily = TitleFamily,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = Defaults.titleLarge.copy(fontFamily = TitleFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = Defaults.titleMedium.copy(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = Defaults.titleSmall.body(),
    bodyLarge = Defaults.bodyLarge.body(),
    bodyMedium = Defaults.bodyMedium.body(),
    bodySmall = Defaults.bodySmall.body(),
    labelLarge = Defaults.labelLarge.body(),
    labelMedium = Defaults.labelMedium.body(),
    // Uppercase section labels are written in the text itself, not forced here.
    labelSmall = Defaults.labelSmall.copy(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)
