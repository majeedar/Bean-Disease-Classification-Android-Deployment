package com.majeedar.beandoc.ui.result

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Colour + icon per label. Colour is never the only signal: the card always shows the icon and the title too.
 * Unknown labels (e.g. from a newer model) get the neutral style.
 */
data class DiagnosisStyle(val icon: ImageVector, val container: Color, val content: Color) {
    companion object {
        @Composable
        fun forLabel(label: String): DiagnosisStyle {
            val dark = isSystemInDarkTheme()
            return when (label) {
                "healthy" -> if (dark) {
                    DiagnosisStyle(Icons.Filled.CheckCircle, Color(0xFF1E4D22), Color(0xFFC8E6C9))
                } else {
                    DiagnosisStyle(Icons.Filled.CheckCircle, Color(0xFFC8E6C9), Color(0xFF1B5E20))
                }
                "angular_leaf_spot" -> if (dark) {
                    DiagnosisStyle(Icons.Filled.Warning, Color(0xFF5C3300), Color(0xFFFFDCB5))
                } else {
                    DiagnosisStyle(Icons.Filled.Warning, Color(0xFFFFE0B2), Color(0xFF7A4100))
                }
                "bean_rust" -> if (dark) {
                    DiagnosisStyle(Icons.Filled.Report, Color(0xFF5A2615), Color(0xFFF6D2C4))
                } else {
                    DiagnosisStyle(Icons.Filled.Report, Color(0xFFF0D0C2), Color(0xFF6D2E14))
                }
                else -> notSure()
            }
        }

        @Composable
        fun notSure(): DiagnosisStyle = if (isSystemInDarkTheme()) {
            DiagnosisStyle(Icons.AutoMirrored.Filled.HelpOutline, Color(0xFF3A3F44), Color(0xFFE1E4E8))
        } else {
            DiagnosisStyle(Icons.AutoMirrored.Filled.HelpOutline, Color(0xFFE3E7EB), Color(0xFF2F3740))
        }
    }
}
