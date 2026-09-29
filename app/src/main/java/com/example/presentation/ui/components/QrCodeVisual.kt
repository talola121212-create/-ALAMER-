package com.example.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@Composable
fun QrCodeVisual(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 180
) {
    val gridSize = 21
    val grid = remember(content) {
        val seed = content.hashCode().toLong()
        val random = Random(seed)
        val matrix = Array(gridSize) { BooleanArray(gridSize) }

        // Fill random data modules
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                matrix[r][c] = random.nextBoolean()
            }
        }

        // Draw 3 QR Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        fun drawFinderPattern(startRow: Int, startCol: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    matrix[startRow + r][startCol + c] = isBorder || isCenter
                }
            }
            // Separator white ring
            for (r in -1..7) {
                for (c in -1..7) {
                    val row = startRow + r
                    val col = startCol + c
                    if (row in 0 until gridSize && col in 0 until gridSize) {
                        if (r == -1 || r == 7 || c == -1 || c == 7) {
                            matrix[row][col] = false
                        }
                    }
                }
            }
        }

        drawFinderPattern(0, 0)
        drawFinderPattern(0, gridSize - 7)
        drawFinderPattern(gridSize - 7, 0)

        // Timing patterns
        for (i in 8 until gridSize - 8) {
            matrix[6][i] = i % 2 == 0
            matrix[i][6] = i % 2 == 0
        }

        matrix
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.size(sizeDp.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = size.width / gridSize
                val darkColor = Color(0xFF0F172A)

                for (r in 0 until gridSize) {
                    for (c in 0 until gridSize) {
                        if (grid[r][c]) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(c * cellSize, r * cellSize),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }
    }
}
