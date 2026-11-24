package io.github.kevin77688.uvd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kevin77688.uvd.extractor.Platform

val Platform.color: Color get() = Color(brandColor)

/** Small pill with the platform name, used on gallery tiles. */
@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(platform.color.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            platform.displayName,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
