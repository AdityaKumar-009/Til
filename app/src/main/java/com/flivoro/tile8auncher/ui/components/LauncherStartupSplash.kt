package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher owns this surface only while core Start + All Apps state is being prepared.
 * This has no timeout and is never replayed by Home, Back or returning from another app.
 */
@Composable
fun LauncherStartupSplash(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090909)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "mosaic",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = StartPersonalization.accentColor,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = message,
                color = Color(0xFFC2C2C2),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }
    }
}
