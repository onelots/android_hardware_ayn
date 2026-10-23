/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.ayn.fan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lineageos.settings.ayn.R

private val BAND_COLORS = listOf(
    Color(0xFF26A69A), // 45°C - Teal
    Color(0xFF4CAF50), // 55°C - Green
    Color(0xFFFFA000), // 65°C - Amber
    Color(0xFFFF7043), // 75°C - Orange
    Color(0xFFE53935), // 85°C - Red
)

@Composable
fun FanCurveSection(
    curveSpeeds: List<Int>,
    temperature: Float?,
    onPointChanged: (index: Int, speedPercent: Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.fan_curve_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            TextButton(onClick = onReset) {
                Text(
                    text = stringResource(R.string.fan_curve_reset),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.fan_curve_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Equalizer Faders Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FanCurve.TEMPERATURE_STEPS.forEachIndexed { i, tempStep ->
                        val speed = curveSpeeds.getOrElse(i) { 0 }
                        EqualizerBar(
                            temperatureC = tempStep,
                            speedPercent = speed,
                            accentColor = BAND_COLORS.getOrElse(i) { MaterialTheme.colorScheme.primary },
                            onSpeedChange = { newSpeed ->
                                onPointChanged(i, newSpeed)
                            },
                        )
                    }
                }

                // Live operating point on curve
                temperature?.let { temp ->
                    val activeSpeed = FanCurve.evaluate(curveSpeeds, temp)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_thermostat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.fan_curve_current_point, temp, activeSpeed),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerBar(
    temperatureC: Int,
    speedPercent: Int,
    accentColor: Color,
    onSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)
    val barHeight = 140.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Value badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "$speedPercent%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Fader Track
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val ratio = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        val pct = (ratio * 100).toInt().coerceIn(0, 100)
                        val stepped = (pct / 5) * 5
                        currentOnSpeedChange(stepped)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val ratio = (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                        val pct = (ratio * 100).toInt().coerceIn(0, 100)
                        val stepped = (pct / 5) * 5
                        currentOnSpeedChange(stepped)
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val fraction = (speedPercent / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accentColor),
            )
        }

        // Temperature label
        Text(
            text = "${temperatureC}°C",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
