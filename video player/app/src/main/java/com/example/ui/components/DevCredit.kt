package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.update.AppUpdateService

private val Teal = Color(0xFF5EEAD4)
private val Blue = Color(0xFF60A5FA)
private val Violet = Color(0xFFA78BFA)
private val Pink = Color(0xFFF472B6)
private val Slate = Color(0xFF94A3B8)

/**
 * The "made by" card at the bottom of Settings: glowing gradient border, an avatar whose
 * gradient ring turns slowly, the developer's name, version chips and a link to the project.
 */
@Composable
fun DevCreditCard(versionName: String, build: Int, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(26.dp)

    val spin = rememberInfiniteTransition(label = "dev_credit_spin")
    val ringAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 9000, easing = LinearEasing)),
        label = "dev_credit_ring"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0x335EEAD4), Color(0x228B5CF6), Color(0x0F000000)))
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x995EEAD4), Color(0x338B5CF6), Color(0x99F472B6))),
                shape
            )
            .padding(horizontal = 20.dp, vertical = 26.dp)
            .testTag("dev_credit"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar: initials inside a slowly rotating gradient ring
        Box(
            modifier = Modifier
                .size(88.dp)
                .drawBehind {
                    val stroke = 4.dp.toPx()
                    rotate(ringAngle) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(Teal, Blue, Violet, Pink, Teal)),
                            radius = size.minDimension / 2f - stroke / 2f,
                            style = Stroke(width = stroke)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B1020)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MY",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Teal, Violet)),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Made with ", color = Slate, fontSize = 13.sp)
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "love",
                tint = Pink,
                modifier = Modifier.size(14.dp)
            )
            Text(text = " by", color = Slate, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Madhu Yadav",
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(Teal, Blue, Violet)),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Developer of OffScroll",
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "\u201CFor people who just want to watch their own videos\u201D",
            color = Slate,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (versionName.isNotBlank()) CreditChip("v$versionName")
            CreditChip("Build $build")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Teal, Violet)))
                .clickable {
                    try {
                        uriHandler.openUri("https://github.com/${AppUpdateService.REPO}")
                    } catch (_: Exception) {
                        // No browser installed: nothing to open
                    }
                }
                .padding(horizontal = 22.dp, vertical = 11.dp)
                .testTag("dev_credit_github"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = Color(0xFF020617),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "View on GitHub",
                color = Color(0xFF020617),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CreditChip(text: String) {
    val chip = RoundedCornerShape(50)
    Text(
        text = text,
        color = Color(0xFFCBD5E1),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(chip)
            .background(Color(0x1FFFFFFF))
            .border(1.dp, Color(0x22FFFFFF), chip)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
