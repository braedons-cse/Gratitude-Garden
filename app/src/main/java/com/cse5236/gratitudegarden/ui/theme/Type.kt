package com.cse5236.gratitudegarden.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cse5236.gratitudegarden.R

// ── Fonts ────────────────────────────────────────────────────────
// Caprasimo = warm chunky display serif (headings).
// Nunito    = rounded UI sans (body / controls). Bundled as a variable
//             font; each weight maps onto the 'wght' axis automatically.
val Caprasimo = FontFamily(
    Font(R.font.caprasimo_regular, FontWeight.Normal),
)

val Nunito = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal),
    Font(R.font.nunito_variable, FontWeight.Medium),
    Font(R.font.nunito_variable, FontWeight.SemiBold),
    Font(R.font.nunito_variable, FontWeight.Bold),
    Font(R.font.nunito_variable, FontWeight.ExtraBold),
)

// Material defaults to Nunito so stock components stay on-brand.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
)
