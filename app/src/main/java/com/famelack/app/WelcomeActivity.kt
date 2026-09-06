package com.famelack.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If user already completed welcome, skip straight to the app
        if (WelcomePrefs.isDone(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            WelcomeScreen(
                onTwitter = {
                    // Open Twitter/X profile for the user to follow & support
                    val ctx = this
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://x.com/hellboy_code".toUri()))
                    } catch (_: Exception) {
                        // fallback: try to open in system browser
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://x.com/hellboy_code".toUri()))
                    }
                },
                onGetStarted = {
                    WelcomePrefs.markDone(this)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                },
                onSkip = {
                    WelcomePrefs.markDone(this)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            )
        }
    }
}

/** Simple persisted flag so welcome shows only on first launch. */
object WelcomePrefs {
    private const val PREFS = "welcome_prefs"
    private const val KEY = "welcome_done"

    fun isDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markDone(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}

@Composable
private fun WelcomeScreen(
    onTwitter: () -> Unit,
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
) {
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF020617))
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.height(24.dp))

                // Sun TV logo (drawn)
                SunTvLogo(size = 150.dp)

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Suni TV",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "سانی تی‌وی",
                    color = Color(0xFFFBBF24),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Live TV · Radio · Webcams\nتلویزیون زنده، رادیو و وبکم‌های سراسر دنیا",
                    color = Color(0xFFCBD5E1),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )

                Spacer(Modifier.height(36.dp))

                // Follow/support CTA
                Text(
                    text = "از توسعه‌دهنده حمایت کنید ❤️",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "اگر از برنامه راضی هستید، در توییتر ما را دنبال کنید تا به ساخت امکانات جدید کمک شود.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onTwitter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DA1F2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("🐦  Follow on Twitter / X", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                // Twitter handle as link
                Text(
                    text = "@hellboy_code",
                    color = Color(0xFF60A5FA),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(30.dp))

                Button(
                    onClick = onGetStarted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color(0xFF1E1B4B)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("🚀  Welcome · Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                // Skip option — dismiss and go straight to app
                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("بستن و ورود به برنامه  /  Skip", color = Color(0xFFCBD5E1), fontSize = 16.sp)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Draws the golden Sun + TV logo (matches the new launcher icon). */
@Composable
private fun SunTvLogo(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val sunBrush = Brush.radialGradient(
            listOf(Color(0xFFFFF59D), Color(0xFFFBBF24), Color(0xFFF97316)),
            center = Offset(cx, cy),
            radius = w * 0.5f
        )

        // Sun rays
        val ray = Color(0xFFFBBF24)
        val rayStroke = Stroke(width = w * 0.028f, cap = StrokeCap.Round)
        for (angle in 0 until 8) {
            val rad = Math.toRadians(angle * 45.0)
            val rOut = w * 0.49f
            val rIn = w * 0.36f
            drawLine(
                color = ray,
                start = Offset(cx + (Math.cos(rad) * rIn).toFloat(), cy + (Math.sin(rad) * rIn).toFloat()),
                end = Offset(cx + (Math.cos(rad) * rOut).toFloat(), cy + (Math.sin(rad) * rOut).toFloat()),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }

        // Sun core
        drawCircle(brush = sunBrush, radius = w * 0.30f, center = Offset(cx, cy))

        // Antennas
        val ant = Color(0xFFF1F5F9)
        drawLine(ant, Offset(cx, cy - w * 0.16f), Offset(cx - w * 0.14f, cy - w * 0.30f), w * 0.016f, cap = StrokeCap.Round)
        drawLine(ant, Offset(cx, cy - w * 0.16f), Offset(cx + w * 0.14f, cy - w * 0.30f), w * 0.016f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFF59E0B), w * 0.016f, Offset(cx - w * 0.152f, cy - w * 0.31f))
        drawCircle(Color(0xFFF59E0B), w * 0.016f, Offset(cx + w * 0.152f, cy - w * 0.31f))

        // TV body
        val tvTop = cy + w * 0.02f
        val tvBottom = cy + w * 0.34f
        val tvLeft = cx - w * 0.28f
        val tvRight = cx + w * 0.28f
        val bodyColor = Color(0xFF334155)
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(tvLeft, tvTop),
            size = Size(tvRight - tvLeft, tvBottom - tvTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
        )

        // Screen
        val sLeft = tvLeft + w * 0.06f
        val sTop = tvTop + w * 0.04f
        val sRight = tvRight - w * 0.13f
        val sBottom = tvBottom - w * 0.06f
        val screenBrush = Brush.linearGradient(
            listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF6366F1))
        )
        drawRoundRect(
            brush = screenBrush,
            topLeft = Offset(sLeft, sTop),
            size = Size(sRight - sLeft, sBottom - sTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Play triangle
        val tri = Path()
        tri.moveTo(sLeft + (sRight - sLeft) * 0.28f, sTop + (sBottom - sTop) * 0.25f)
        tri.lineTo(sLeft + (sRight - sLeft) * 0.62f, (sTop + sBottom) / 2f)
        tri.lineTo(sLeft + (sRight - sLeft) * 0.28f, sTop + (sBottom - sTop) * 0.75f)
        tri.close()
        drawPath(tri, Color.White)

        // Control dots
        drawCircle(Color(0xFFF59E0B), w * 0.016f, Offset(tvRight - w * 0.06f, tvTop + w * 0.07f))
        drawCircle(Color(0xFF38BDF8), w * 0.016f, Offset(tvRight - w * 0.06f, tvTop + w * 0.13f))

        // TV stand
        drawLine(Color(0xFF475569), Offset(tvLeft + w * 0.06f, tvBottom), Offset(tvLeft - w * 0.03f, tvBottom + w * 0.06f), w * 0.016f, cap = StrokeCap.Round)
        drawLine(Color(0xFF475569), Offset(tvRight - w * 0.06f, tvBottom), Offset(tvRight + w * 0.03f, tvBottom + w * 0.06f), w * 0.016f, cap = StrokeCap.Round)
    }
}
