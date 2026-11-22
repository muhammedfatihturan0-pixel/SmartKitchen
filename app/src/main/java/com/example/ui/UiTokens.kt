package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Geometric Balance Theme Palette Custom Colors
val KitchenSage = Color(0xFF386B01)       // Green Balance primary
val SoftSageBg = Color(0xFFF3F4EE)        // Soft earthy background
val KitchenClay = Color(0xFFD07050)       // Clay/Terracotta secondary accent
val DarkCharcoal = Color(0xFF1B1C17)      // Deep high-contrast text
val SafeGreen = Color(0xFF386B01)         // Sustainable badge text
val WarningYellow = Color(0xFFE65100)     // High-contrast warning
val AlertRed = Color(0xFFB71C1C)          // High-contrast alert
val BorderColor = Color(0xFFDDE4D1)       // Geometric balanced border
val AccentGreenBg = Color(0xFFE8F3D6)     // Light green container
val AccentGreenBorder = Color(0xFFB7CC9D) // Light green border
val HighlightGreen = Color(0xFFD2E8B1)    // Soft green button/accent highlight
val HighlightDark = Color(0xFF141E00)     // Dark green label/text inside highlight

@Composable
fun smartKitchenFieldColors(
    focusedBorderColor: Color = KitchenSage,
    unfocusedBorderColor: Color = Color.LightGray
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DarkCharcoal,
    unfocusedTextColor = DarkCharcoal,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    focusedLabelColor = focusedBorderColor,
    unfocusedLabelColor = Color.Gray,
    focusedPlaceholderColor = Color.Gray,
    unfocusedPlaceholderColor = Color.Gray,
    cursorColor = focusedBorderColor
)

@Composable
fun SuggestionNutritionChip(text: String, bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: Dp = 0.dp,
    crossAxisSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0)
        val placeables = measurables.map { it.measure(looseConstraints) }
        val layoutWidth = constraints.maxWidth
        val lines = mutableListOf<List<Placeable>>()
        var currentLine = mutableListOf<Placeable>()
        var currentLineWidth = 0
        val mainAxisSpacingPx = mainAxisSpacing.roundToPx()
        val crossAxisSpacingPx = crossAxisSpacing.roundToPx()

        for (placeable in placeables) {
            val spaceNeeded = if (currentLineWidth == 0) placeable.width else placeable.width + mainAxisSpacingPx
            if (currentLineWidth + spaceNeeded <= layoutWidth || currentLine.isEmpty()) {
                currentLine.add(placeable)
                currentLineWidth += spaceNeeded
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(placeable)
                currentLineWidth = placeable.width
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        var layoutHeight = 0
        for ((index, line) in lines.withIndex()) {
            val maxLineHeight = line.maxOf { it.height }
            layoutHeight += maxLineHeight
            if (index < lines.size - 1) {
                layoutHeight += crossAxisSpacingPx
            }
        }

        layout(layoutWidth, layoutHeight.coerceAtLeast(0)) {
            var y = 0
            for (line in lines) {
                var x = 0
                val maxLineHeight = line.maxOf { it.height }
                for (placeable in line) {
                    placeable.placeRelative(x, y + (maxLineHeight - placeable.height) / 2)
                    x += placeable.width + mainAxisSpacingPx
                }
                y += maxLineHeight + crossAxisSpacingPx
            }
        }
    }
}
