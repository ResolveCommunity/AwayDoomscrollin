package com.awaydoomscrollin.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AntiCheatGuiltActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_language", "auto") ?: "auto"
        val deviceLang = java.util.Locale.getDefault().language.lowercase()
        val isEn = if (savedLang == "en") true else if (savedLang == "tr") false else !deviceLang.startsWith("tr")

        val totalBlocks = prefs.getInt("total_blocks", 0)
        val userXp = prefs.getLong("user_xp", 0L)
        val savedMinutes = totalBlocks * 3

        setContent {
            ZenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.92f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                            shadowElevation = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🛑", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (isEn) "YOU ARE ABOUT TO LOWER THE SHIELD!" else "KALKANI İNDİRMEK ÜZERESİN!",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (isEn) "What you have accomplished so far:" else "Şu ana kadar başardıkların:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatBox(title = if (isEn) "Blocked" else "Engellenen", value = "$totalBlocks")
                                    StatBox(title = if (isEn) "Saved Time" else "Kurtarılan Süre", value = "$savedMinutes min")
                                    StatBox(title = if (isEn) "Earned" else "Kazanılan", value = "$userXp XP")
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = if (isEn) 
                                        "Are you really going to throw away all your hard work and achievements for a single moment of weakness?" 
                                    else 
                                        "Bütün bu emeği ve kazandığın başarıları tek bir anlık zayıflık yüzünden çöpe mi atacaksın?",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                            addCategory(android.content.Intent.CATEGORY_HOME)
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startActivity(homeIntent)
                                        finish()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = if (isEn) "I Changed My Mind, Fight On! 🛡️" else "Vazgeçtim, Mücadeleye Devam! 🛡️",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = {
                                        prefs.edit().putInt("streak_days", 0).apply()
                                        finish()
                                    }
                                ) {
                                    Text(
                                        text = if (isEn) "Surrender Anyway" else "Yine de Pes Et",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}
