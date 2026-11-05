package com.resolvecommunity.awaydoomscrollin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFF00F2FE)

/**
 * About → Support. The user copies the log and pastes it into their reply to
 * an existing support email; the app never sends it anywhere and does not
 * open a new email (that would start a separate ticket).
 */
@Composable
internal fun SupportLogCard(isEn: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var logOn by remember { mutableStateOf(SupportDiagnostics.isEnabled(context)) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F1523),
        // Same as the usage data card: the border lights up while the switch is on.
        border = BorderStroke(1.dp, if (logOn) Accent.copy(alpha = 0.5f) else Color(0xFF1E2A40)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bug_report),
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isEn) "Troubleshooting log" else "Sorun giderme kaydı",
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = logOn,
                    onCheckedChange = { checked ->
                        // Off deletes the stored log at once; nothing to confirm.
                        SupportDiagnostics.setEnabled(context, checked)
                        logOn = checked
                        copied = false
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF070A12),
                        checkedTrackColor = Accent
                    )
                )
            }

            Text(
                text = when {
                    !logOn && isEn -> "The log is off and nothing is recorded. To report a problem, turn it on and copy the log after it happens again."
                    !logOn -> "Kayıt kapalı, hiçbir şey kaydedilmiyor. Bir sorun bildirmek için kaydı açın, sorun tekrar yaşanınca kopyalayın."
                    isEn -> "If something isn't working as expected, copy this log and paste it into an email."
                    else -> "Bir şey beklediğiniz gibi çalışmıyorsa bu kaydı kopyalayıp e-postaya yapıştırın."
                },
                fontSize = 11.5.sp,
                color = Color.White.copy(alpha = 0.68f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 10.dp)
            )

            if (logOn) {
                Text(
                    text = if (isEn) {
                        "Even with usage data sharing turned on, this log is never sent anywhere unless you send it yourself. " +
                            "It covers only the last 24 hours and doesn't include names, messages or on-screen text."
                    } else {
                        "Kullanım verisi paylaşımını açmış olsanız bile bu kayıt, siz göndermedikçe hiçbir yere iletilmez. " +
                            "Yalnızca son 24 saati kapsar; isim, mesaj ya da ekrandaki yazıları içermez."
                    },
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 14.5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val report = withContext(Dispatchers.IO) {
                                SupportDiagnostics.buildReport(context, isEn)
                            }
                            copyToClipboard(context, report, isEn)
                            copied = true
                        }
                    },
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = when {
                            copied -> if (isEn) "Copied" else "Kopyalandı"
                            isEn -> "Copy log"
                            else -> "Kaydı kopyala"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (copied) {
                    Text(
                        text = if (isEn) "You can now paste it into your email."
                        else "Artık e-postanıza yapıştırabilirsiniz.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00FF87),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    text = if (isEn) "Delete log" else "Kaydı sil",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    // Kept at the end edge, away from Copy: deleting is the rare action.
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 6.dp)
                        .clickable { confirmClear = true }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(if (isEn) "Delete this log?" else "Kayıt silinsin mi?") },
            text = {
                Text(if (isEn) "Everything recorded so far will be removed from this phone. New events will still be recorded."
                else "Şu ana kadar kaydedilenler bu telefondan silinir. Yeni olaylar kaydedilmeye devam eder.")
            },
            confirmButton = {
                TextButton(onClick = {
                    SupportDiagnostics.clear(context)
                    copied = false
                    confirmClear = false
                }) { Text(if (isEn) "Delete" else "Sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(if (isEn) "Cancel" else "Vazgeç") }
            }
        )
    }
}

private fun copyToClipboard(context: Context, text: String, isEn: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(if (isEn) "AwayDoomscrollin' troubleshooting log" else "AwayDoomscrollin' sorun giderme kaydı", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Keep the log out of the keyboard's clipboard preview.
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
