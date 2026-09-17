package com.resolvecommunity.awaydoomscrollin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the system bars so the Home screen's ambient glow reaches the top edge;
        // the app is always dark, so system bar icons stay light on every device theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        if (TelemetryManager.isTelemetryEnabled(this)) {
            TelemetryManager.sendTelemetryAsync(this)
        }

        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val initialOnboardingDone = prefs.getBoolean("onboarding_completed", false)

        AccessibilityStreakPolicy.syncState(
            this,
            isAccessibilityServiceEnabled(this, AntiScrollService::class.java)
        )

        setContent {
            ZenTheme {
                var isOnboardingCompleted by remember { mutableStateOf(initialOnboardingDone) }
                var onboardingStartStep by remember { mutableIntStateOf(1) }

                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        prefs = prefs,
                        initialStep = onboardingStartStep,
                        onComplete = {
                            prefs.edit().putBoolean("onboarding_completed", true).apply()
                            onboardingStartStep = 1
                            isOnboardingCompleted = true
                        }
                    )
                } else {
                    MainNavigationDashboard(
                        prefs = prefs,
                        onReopenOnboarding = { requestedStep ->
                            onboardingStartStep = requestedStep.coerceIn(1, 5)
                            isOnboardingCompleted = false
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AccessibilityStreakPolicy.syncState(
            this,
            isAccessibilityServiceEnabled(this, AntiScrollService::class.java)
        )
    }
}

// ==========================================
// DİNAMİK SİSTEM TEMASI (DARK / LIGHT MOD UYUMU)
// ==========================================
@Composable
fun ZenTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        background = Color(0xFF070A12), // Derin Pitch Cyber Siyah
        surface = Color(0xFF0F1523),    // Siber Koyu Slate Kart
        primary = Color(0xFF00F2FE),    // Neon Cyan Mavi
        secondary = Color(0xFF00FF87),  // Neon Canlı Yeşil
        error = Color(0xFFFF0055),      // Neon Crimson Kırmızı
        outline = Color(0xFF1E2A40),    // Parlak Siber Çerçeve
        onBackground = Color(0xFFF1F5F9),
        onSurface = Color(0xFFF1F5F9),
        errorContainer = Color(0xFF2A0813),
        onErrorContainer = Color(0xFFFF0055)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}



fun getAppLanguage(prefs: android.content.SharedPreferences): String {
    val saved = prefs.getString("app_language", null)
    if (saved == "tr" || saved == "en") return saved

    val deviceLang = java.util.Locale.getDefault().language.lowercase()
    val resolved = if (deviceLang.startsWith("tr")) "tr" else "en"
    prefs.edit().putString("app_language", resolved).apply()
    return resolved
}

private fun telemetryDisclosureText(isEn: Boolean): String = if (isEn) {
    "You can help us improve protection across different phones by sharing a pseudonymous usage summary.\n\n" +
        "Shared: a random installation ID; device brand, model and screen metrics; Android, AwayDoomscrollin', Instagram, TikTok and YouTube versions; total and per-app block counts; time spent under Instagram protection.\n\n" +
        "Never shared: your name, email address, messages, passwords, screenshots or browsing history. Telemetry is off by default. Until you turn it on, nothing is sent to our server and protection stays entirely on your device. Turning it off stops new reports; the latest report already received is deleted after the 90-day retention period."
} else {
    "İsterseniz kullanım özetinizi paylaşarak korumanın farklı telefonlarda daha iyi çalışmasına yardımcı olabilirsiniz.\n\n" +
        "Paylaşılan bilgiler: yalnızca bu kuruluma ait rastgele kimlik; cihazın marka, model ve ekran ölçüleri; Android, AwayDoomscrollin', Instagram, TikTok ve YouTube sürümleri; toplam ve uygulama bazında engelleme sayıları; Instagram koruma süresi.\n\n" +
    "Adınız, e-posta adresiniz, mesajlarınız, şifreleriniz, ekran görüntüleriniz ve gezinme geçmişiniz paylaşılmaz. Bu özellik başlangıçta kapalıdır. Siz açmadıkça sunucumuza hiçbir kullanım verisi gönderilmez. Kapattığınızda yeni gönderimler durur; daha önce ulaşan son rapor 90 günlük saklama süresinin sonunda silinir."
}

private fun accessibilityDisclosureText(isEn: Boolean): String = if (isEn) {
    "AwayDoomscrollin' needs Accessibility access to recognize the short-content areas you chose to protect in Instagram, TikTok and YouTube.\n\n" +
        "On those apps, it examines on-screen accessibility text and labels, view identifiers, window changes, taps and scroll events on your device. It uses that information to place the Instagram curtain and to perform a small set of rule-based Back, tap and scroll actions on protected screens.\n\n" +
        "This screen information is processed only on your device. It is not recorded, saved or sent to a server. Messages, passwords, photos, screenshots and typed text are not included in optional usage reports. You can continue without granting access; protection will remain off."
} else {
    "AwayDoomscrollin', Instagram, TikTok ve YouTube'da korumayı seçtiğiniz kısa içerik alanlarını tanımak için Erişilebilirlik iznine ihtiyaç duyar.\n\n" +
        "Bu uygulamalarda ekrandaki erişilebilirlik metinlerini ve etiketlerini, görünüm kimliklerini, pencere değişikliklerini, dokunma ve kaydırma olaylarını cihazınızda inceler. Bu bilgileri Instagram perdesini yerleştirmek ve korunan ekranlarda belirli, kurala bağlı Geri, dokunma veya kaydırma eylemleri uygulamak için kullanır.\n\n" +
        "İncelenen ekran bilgileri yalnızca cihazınızda işlenir; kaydedilmez, saklanmaz veya sunucuya gönderilmez. Mesajlar, şifreler, fotoğraflar, ekran görüntüleri ve yazdığınız metinler isteğe bağlı kullanım raporlarına eklenmez. İzin vermeden devam edebilirsiniz; koruma kapalı kalır."
}

private fun openAccessibilitySettings(context: Context, isEn: Boolean = false) {
    Toast.makeText(
        context,
        if (isEn) "Settings > Installed apps > AwayDoomscrollin'" else "Ayarlar > Yüklü uygulamalar > AwayDoomscrollin'",
        Toast.LENGTH_LONG
    ).show()
    runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun acceptAccessibilityDisclosure(context: Context, isEn: Boolean) {
    if (AccessibilityConsent.accept(context)) {
        openAccessibilitySettings(context, isEn)
    } else {
        Toast.makeText(
            context,
            if (isEn) "Your preference could not be saved. Please try again." else "Tercih kaydedilemedi. Lütfen tekrar deneyin.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun AccessibilityConsentDialog(
    isEn: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(if (isEn) "Accessibility access" else "Erişilebilirlik izni")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = accessibilityDisclosureText(isEn),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(
                    if (isEn) "Agree and open settings" else "Kabul et ve ayarları aç",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Not now" else "Şimdi değil")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun TelemetryConsentDialog(
    isEn: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_handshake),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(if (isEn) "Share optional usage data?" else "İsteğe bağlı kullanım verileri paylaşılsın mı?")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = telemetryDisclosureText(isEn),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(if (isEn) "Turn on sharing" else "Paylaşımı aç", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "Vazgeç")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponentName = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponentName != null && enabledComponentName == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun rememberAccessibilityStatus(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityActive by remember {
        mutableStateOf<Boolean>(isAccessibilityServiceEnabled(context, AntiScrollService::class.java))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val active = isAccessibilityServiceEnabled(context, AntiScrollService::class.java)
                isAccessibilityActive = active
                AccessibilityStreakPolicy.syncState(context, active)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return isAccessibilityActive
}

// ==========================================
// İNTERAKTİF TANITIM VE İZİN KURULUM EKRANI
// ==========================================
@Composable
fun OnboardingScreen(
    prefs: android.content.SharedPreferences,
    initialStep: Int = 1,
    onComplete: () -> Unit
) {
    var step by rememberSaveable(initialStep) { mutableIntStateOf(initialStep.coerceIn(1, 5)) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isAccessibilityActive = rememberAccessibilityStatus()
    val isEn = getAppLanguage(prefs) == "en"

    LaunchedEffect(step) {
        scrollState.scrollTo(0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // İlerleme Çubuğu
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    color = if (index + 1 <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEn) "Step $step / 5" else "Adım $step / 5",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Sayfa Geçişi
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) + slideInHorizontally { width -> width / 2 } togetherWith
                            fadeOut(animationSpec = tween(400)) + slideOutHorizontally { width -> -width / 2 }
                },
                label = "OnboardingStepTransition"
            ) { targetStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 20.dp)
                ) {
                    when (targetStep) {
                        1 -> OnboardingStepOne(isEn = isEn)
                        2 -> OnboardingStepTwo(isEn = isEn)
                        3 -> OnboardingStepThree(isEn = isEn)
                        4 -> OnboardingStepFourAppsAndPrefs(isEn = isEn, prefs = prefs, context = context)
                        5 -> OnboardingStepFivePermissions(
                            isEn = isEn,
                            isAccessibilityActive = isAccessibilityActive,
                            context = context
                        )
                    }
                }
            }

            // Alt Navigasyon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    TextButton(onClick = { step-- }) {
                        Text(if (isEn) "← Back" else "← Geri", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 15.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (step < 5) {
                            step++
                        } else {
                            onComplete()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color(0xFF0D1117)
                    )
                ) {
                    Text(
                        text = if (step == 5)
                            if (isAccessibilityActive) {
                                if (isEn) "Finish setup" else "Kurulumu tamamla"
                            } else {
                                if (isEn) "Continue without permission" else "İzin vermeden devam et"
                            }
                        else if (step == 1) 
                            (if (isEn) "Start setup →" else "Kuruluma başla →") 
                        else 
                            (if (isEn) "Next →" else "Devam et →"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingStepOne(isEn: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ==========================================
        // 1. HERO KARTI: NEDEN AWAYDOOMSCROLLIN'? (CERRAHİ KALKAN BATTLE CARD)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00FF87)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header with Glowing Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00FF87).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00FF87)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_zap),
                                contentDescription = null,
                                tint = Color(0xFF00FF87),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEn) "WHY AWAYDOOMSCROLLIN'?" else "NEDEN AWAYDOOMSCROLLIN'?",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FF87),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (isEn) "On-device protection for short-content feeds" else "Kısa içerik akışları için cihazda çalışan koruma",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison 1: Klasik Uygulamalar (Kırmızı / 20 Dakika Tuzağı)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFF0055).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0055).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_hourglass),
                            contentDescription = null,
                            tint = Color(0xFFFF0055),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEn) "Why not just use a timer?" else "Neden sadece bir süre sınırı kullanılmıyor?",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF0055)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEn)
                                    "Time limits are easy to bypass and feeds have no natural stopping point. AwayDoomscrollin' intervenes directly in supported short-content areas."
                                else
                                    "Süre sınırları kolayca atlanabilir; akışların doğal bir durma noktası yoktur. AwayDoomscrollin' desteklenen kısa içerik alanlarını doğrudan sınırlar.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Comparison 2: AwayDoomscrollin' (Yeşil / Akıllı Müdahale)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF00FF87).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00FF87).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield),
                            contentDescription = null,
                            tint = Color(0xFF00FF87),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isEn) "Communication stays available" else "İletişim açık kalır",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF87)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEn)
                                    "Instagram messages, profile information, Search and Stories remain available while Home, Explore and Reels are restricted. TikTok and YouTube use their own short-content protections."
                                else
                                    "Instagram'da mesajlar, profil bilgileri, Arama ve Hikâyeler açık kalırken Ana Sayfa, Keşfet ve Reels sınırlandırılır. TikTok ve YouTube için kendi kısa içerik korumaları kullanılır.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.95f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4'LÜ GÜVEN VE ÖZGÜRLÜK SÜTUNU (2x2 Grid - Eşitlenmiş ve Simetrik)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1: DM & İletişim Serbest
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF070A12),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chat),
                                        contentDescription = null,
                                        tint = Color(0xFF00F2FE),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "Messaging stays open" else "Mesajlaşma açık",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00F2FE),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "Messaging stays available. Only supported short-content areas are blocked." else "Mesajlaşma açık kalır. Yalnızca desteklenen kısa içerik alanları engellenir.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // 2: Gizlilik Öncelikli & Yerel
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF070A12),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_lock),
                                        contentDescription = null,
                                        tint = Color(0xFF00FF87),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "On-device detection" else "Cihazda algılama",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "Protection decisions are made on your device." else "Koruma kararları telefonunuzda verilir.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 3: Açık Kaynak
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF070A12),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = null,
                                        tint = Color(0xFFFFB703),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "Open Source" else "Açık Kaynak",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB703),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "The source code can be reviewed on GitHub." else "Kaynak kod GitHub üzerinden incelenebilir.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // 4: Sıfır Reklam & Sıfır Takip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF070A12),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_slash_ban),
                                        contentDescription = null,
                                        tint = Color(0xFF00F2FE),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "No ads" else "Reklamsız",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00F2FE),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "No ads or paid subscription is required." else "Reklam veya ücretli abonelik gerekmez.",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 2. GELİŞTİRİCİ MEKTUBU KARTI (HİKAYE)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Başlık Alanı
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_handshake),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEn) "I Am One of You." else "Ben de Sizden Biriyim.",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEn) "A letter from Resolve Community" else "Resolve Community'den bir mektup",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bölüm 1: Hikayenin Başlangıcı
                Text(
                    text = if (isEn) "Hello, I am an independent developer behind Resolve Community and creator of AwayDoomscrollin'." else "Merhaba, ben Resolve Community adına AwayDoomscrollin' uygulamasını geliştiren bağımsız bir geliştiriciyim.",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn)
                        "Ever since the COVID-19 pandemic, almost my entire day has been spent in front of computer and phone screens. Life outside was complicated; playing games or scrolling through feeds for hours felt more enjoyable and safer. The real reason was escaping reality."
                    else
                        "COVID-19 pandemisinden beri günümün neredeyse tamamı bilgisayar ve telefon ekranı karşısında geçiyordu. Dışarıdaki hayat karmaşıktı; sosyalleşmek yerine ekran başında oyun oynamak veya saatlerce akış kaydırmak daha keyifli ve güvenli geliyordu. Sanırım asıl sebebim, gerçek hayattan kaçmaktı.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bölüm 2: Farkındalık & Sağlık Etkileri
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_warning_triangle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "What I noticed" else "Fark ettiğim sorun",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEn)
                                "I realized that short-content habits were taking more time and attention than I wanted.\n\nI wanted a clearer boundary that would help me get back to my studies and my goals."
                            else
                                "Kısa içerik alışkanlığının istediğimden daha fazla zamanımı ve dikkatimi aldığını fark ettim.\n\nOkula ve hayat hedeflerime odaklanmamı sağlayacak daha net bir sınır istedim.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bölüm 3: Çözüm Arayışı
                Text(
                    text = if (isEn) "Why did I build this app under Resolve Community?" else "Bu uygulamayı neden Resolve Community bünyesinde geliştirdim?",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn)
                        "Even while doing something else, I would reach for short videos without thinking. I built AwayDoomscrollin' under Resolve Community to create a clear, user-controlled boundary. I use it myself, and the public beta continues to improve through transparent testing."
                    else
                        "Başka bir şeyle ilgilenirken bile düşünmeden kısa videolara yöneldiğimi fark ediyordum. AwayDoomscrollin'ı, kullanıcının kontrol ettiği net bir sınır oluşturmak için Resolve Community çatısı altında geliştirdim. Ben de kullanıyorum; açık beta, şeffaf testlerle gelişmeye devam ediyor.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bölüm 4: Algoritmanın Tuzağı (Madde Madde)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF85149).copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_target),
                                contentDescription = null,
                                tint = Color(0xFFF85149),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Why short-content feeds never end" else "Kısa içerik akışları neden bitmez?",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF85149)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isEn)
                                "• Feeds automatically load the next item.\n" +
                                "• Watch time and interactions can be used to personalize what appears next.\n" +
                                "• Because there is no natural stopping point, a short visit can last longer than intended."
                            else
                                "• Akışlar sıradaki içeriği otomatik olarak yükler.\n" +
                                "• İzleme süresi ve etkileşimler, sonraki içerikleri kişiselleştirmek için kullanılabilir.\n" +
                                "• Doğal bir durma noktası olmadığı için kısa bir ziyaret planlanandan uzun sürebilir.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bölüm 5: Çağrı & Kapanış
                Text(
                    text = if (isEn)
                        "AwayDoomscrollin' was created to make room for study, work and intentional time. Protection is a tool; you can review its scope and choose the apps that fit your needs."
                    else
                        "AwayDoomscrollin'; ders, iş ve bilinçli geçirilen zamana alan açmak için geliştirildi. Koruma bir araçtır; kapsamını inceleyebilir ve ihtiyacınıza uygun uygulamaları seçebilirsiniz.",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun OnboardingStepTwo(isEn: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(70.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "?",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isEn) "How does protection work?" else "Koruma nasıl çalışır?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = if (isEn) "Protection mechanism for short-video feeds" else "Kısa video akışları için koruma mekanizması",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "How it works" else "Nasıl çalışır?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 1. Instagram
                AppMechanismRow(
                    iconRes = R.drawable.ic_instagram,
                    iconTint = Color(0xFFE1306C),
                    title = "Instagram",
                    description = if (isEn)
                        "Home and Explore are hidden behind a local curtain; if Reels opens, you are taken back automatically. Stories, Direct Messages, profile information and Search remain available."
                    else
                        "Ana Sayfa ve Keşfet içerikleri cihazdaki perdeyle kapatılır; Reels açılırsa otomatik olarak geri dönülür. Hikâyeler, mesajlar, profil bilgileri ve Arama kullanılabilir."
                )

                // 2. TikTok
                AppMechanismRow(
                    iconRes = R.drawable.ic_tiktok,
                    iconTint = Color(0xFF00F2FE),
                    title = "TikTok",
                    description = if (isEn)
                        "Feed scrolling is blocked on the first swipe; single videos can still be watched."
                    else
                        "İlk akış kaydırması durdurulur; tekil videolar sonuna kadar izlenebilir."
                )

                // 3. YouTube Shorts
                AppMechanismRow(
                    iconRes = R.drawable.ic_youtube,
                    iconTint = Color(0xFFFF0055),
                    title = "YouTube Shorts",
                    description = if (isEn)
                        "The first video is intentionally allowed for useful content; the viewer closes on the first swipe to prevent endless doomscrolling."
                    else
                        "Faydalı ve eğitici içeriklere erişebilmeniz için ilk video açık bırakılır; sonsuz döngüye kapılmamanız için sonraki videoya ilk kaydırmada kapatılır."
                )

                // 4. On-Device Shield
                AppMechanismRow(
                    iconRes = R.drawable.ic_shield,
                    iconTint = Color(0xFF00FF87),
                    title = if (isEn) "On-Device & Local" else "Cihazda Yerel Koruma",
                    description = if (isEn)
                        "Protection runs entirely on your device; personal data is never collected. Platform or Android updates can affect screen detection."
                    else
                        "Koruma telefonunuzda yerel çalışır; kişisel verileriniz toplanmaz. Platform veya Android güncellemeleri algılamayı etkileyebilir."
                )
            }
        }
    }
}

@Composable
private fun AppMechanismRow(
    iconRes: Int,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = iconTint.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, iconTint.copy(alpha = 0.35f)),
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun OnboardingStepThree(isEn: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(70.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color(0xFF00FF87),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isEn) "What it can and cannot do" else "Ne yapabilir, ne yapamaz?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isEn)
                "With full transparency, here is what protection can do and our privacy guarantees:"
            else
                "Tam şeffaflıkla: korumanın neler yapıp yapamadığı ve gizlilik güvenceleriniz",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        PermissionMatrixCard(isEn = isEn)
    }
}

@Composable
fun PermissionMatrixCard(isEn: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEn) "WHAT PROTECTION DOES" else "KORUMA NELERİ YAPAR?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF87),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "All analysis and protection happen directly on your device. When optional usage data is off, nothing is sent to our server." else "Tüm analiz ve koruma işlemleri doğrudan telefonunuzda çalışır; isteğe bağlı kullanım verileri kapalıyken sunucumuza hiçbir bilgi gitmez.")
                MatrixBullet(if (isEn) "Instagram Home and Explore are hidden behind a local curtain; if Reels opens, you are taken back automatically." else "Instagram Ana Sayfa ve Keşfet içerikleri cihazınızdaki perdeyle kapatılır; Reels açılırsa otomatik olarak geri dönülür.")
                MatrixBullet(if (isEn) "TikTok feed scrolling and Shorts chains are blocked; single videos can still be watched." else "TikTok akışındaki kaydırma ve Shorts zinciri engellenir; tekil videolar izlenebilir.")
                MatrixBullet(if (isEn) "Third-party interface changes can affect how reliably protection detects protected screens." else "Instagram, TikTok veya YouTube arayüzü değişirse algılama bundan etkilenebilir.")
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEn) "WHAT IT NEVER DOES OR COLLECTS" else "NELERİ ASLA YAPMAZ VE TOPLAMAZ?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "Does NOT save or send on-screen accessibility information. Messages, passwords, photos and screenshots are never collected." else "İncelenen ekran bilgilerini kaydetmez veya göndermez; mesajlarınız, şifreleriniz, fotoğraflarınız ve ekran görüntüleriniz asla toplanmaz.", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT send your personal data or browsing history to any server." else "Adınızı, kişisel bilgilerinizi veya gezinme geçmişinizi hiçbir sunucuya göndermez.", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT show ads or sell personal data." else "Reklam göstermez ve kişisel verilerinizi asla satmaz.", isNegative = true)
            }
        }
    }
}

@Composable
fun MatrixBullet(text: String, isNegative: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(id = if (isNegative) R.drawable.ic_close else R.drawable.ic_check),
            contentDescription = null,
            tint = if (isNegative) Color(0xFFFF5252) else Color(0xFF00FF87),
            modifier = Modifier
                .size(13.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun OnboardingStepFourAppsAndPrefs(
    isEn: Boolean,
    prefs: SharedPreferences,
    context: Context
) {
    var isInstaEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.INSTAGRAM)) }
    var isTiktokEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.TIKTOK)) }
    var isYoutubeEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.YOUTUBE)) }
    var showTelemetryDetails by remember { mutableStateOf(false) }
    var showTelemetryConsent by remember { mutableStateOf(false) }
    var isTelemetryOn by remember { mutableStateOf(TelemetryManager.isTelemetryEnabled(context)) }

    if (showTelemetryDetails) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "Optional usage data" else "İsteğe bağlı kullanım verileri",
            content = telemetryDisclosureText(isEn),
            iconRes = R.drawable.ic_handshake,
            iconTint = MaterialTheme.colorScheme.primary,
            onDismiss = { showTelemetryDetails = false }
        )
    }

    if (showTelemetryConsent) {
        TelemetryConsentDialog(
            isEn = isEn,
            onAccept = {
                TelemetryManager.setTelemetryEnabled(context, true)
                isTelemetryOn = true
                showTelemetryConsent = false
            },
            onDismiss = { showTelemetryConsent = false }
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(70.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_phone),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isEn) "Apps and preferences" else "Uygulamalar ve seçenekler",
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isEn) "Choose the apps where protection will be active:" else "Korumanın çalışacağı uygulamaları seçin:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 1. INSTAGRAM COMPACT CARD
        CompactOnboardingAppCard(
            appName = "Instagram",
            subtitle = if (isEn) "Reels & endless feed" else "Reels ve ana akış",
            iconRes = R.drawable.ic_instagram,
            brandColor = Color(0xFFE1306C),
            isEnabled = isInstaEnabled,
            isBeta = true,
            onToggle = { enabled ->
                if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.INSTAGRAM, enabled)) {
                    isInstaEnabled = enabled
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. TIKTOK COMPACT CARD
        CompactOnboardingAppCard(
            appName = "TikTok",
            subtitle = if (isEn) "Short Video Feed" else "Kısa video akışı",
            iconRes = R.drawable.ic_tiktok,
            brandColor = Color(0xFF00F2FE),
            isEnabled = isTiktokEnabled,
            isBeta = true,
            onToggle = { enabled ->
                if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.TIKTOK, enabled)) {
                    isTiktokEnabled = enabled
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. YOUTUBE SHORTS COMPACT CARD
        CompactOnboardingAppCard(
            appName = "YouTube Shorts",
            subtitle = if (isEn) "Shorts screen only" else "Yalnızca Shorts ekranı",
            iconRes = R.drawable.ic_youtube,
            brandColor = Color(0xFFFF0055),
            isEnabled = isYoutubeEnabled,
            isBeta = true,
            onToggle = { enabled ->
                if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.YOUTUBE, enabled)) {
                    isYoutubeEnabled = enabled
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. TELEMETRİ KARTI
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isTelemetryOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_handshake),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "Help improve protection (optional)" else "Korumayı geliştirmemize yardımcı olun (isteğe bağlı)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isTelemetryOn,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showTelemetryConsent = true
                            } else {
                                TelemetryManager.setTelemetryEnabled(context, false)
                                isTelemetryOn = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF070A12),
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Text(
                    text = if (isEn) {
                        "If enabled, the device model, app versions and aggregate block counts are shared to help improve compatibility. Messages and screen content are not included."
                    } else {
                        "Açarsanız cihaz modeli, uygulama sürümleri ve toplu engelleme bilgileri paylaşılır. Mesajlarınız ve ekrandaki içerikler paylaşılmaz."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 15.5.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )

                TextButton(
                    onClick = { showTelemetryDetails = true },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isEn) "See exactly what is shared" else "Nelerin paylaşıldığını görün",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CompactOnboardingAppCard(
    appName: String,
    subtitle: String,
    iconRes: Int,
    brandColor: Color,
    isEnabled: Boolean,
    isBeta: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) brandColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = brandColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = appName,
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isBeta) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = brandColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "BETA",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = brandColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF070A12),
                    checkedTrackColor = brandColor
                )
            )
        }
    }
}

@Composable
fun OnboardingStepFivePermissions(
    isEn: Boolean = false,
    isAccessibilityActive: Boolean,
    context: Context
) {
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    var showAccessibilityConsent by remember { mutableStateOf(false) }

    if (showAccessibilityConsent) {
        AccessibilityConsentDialog(
            isEn = isEn,
            onAccept = {
                showAccessibilityConsent = false
                acceptAccessibilityDisclosure(context, isEn)
            },
            onDismiss = { showAccessibilityConsent = false }
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(70.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isEn) "Turn on protection" else "Korumayı aç",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isEn) "Accessibility is required for protection. Notifications are optional." else "Koruma için erişilebilirlik izni gerekir. Bildirimler isteğe bağlıdır.",
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(if (isAccessibilityActive) 1.dp else 1.5.dp, if (isAccessibilityActive) MaterialTheme.colorScheme.secondary else Color(0xFF00F2FE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAccessibilityActive) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "1",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isEn) "Accessibility Permission" else "Erişilebilirlik izni", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isEn) "Used to detect protected apps and show the curtain or return you to a safe screen." else "Korunan ekranları tanımak, perdeyi göstermek veya güvenli ekrana dönmek için cihazınızda kullanılır.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (!isAccessibilityActive) showAccessibilityConsent = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAccessibilityActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                        contentColor = if (isAccessibilityActive) MaterialTheme.colorScheme.secondary else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAccessibilityActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isEn) "Permission Granted" else "İzin verildi",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            if (isEn) "Review permission and open settings" else "İzni incele ve ayarları aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!isAccessibilityActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lightbulb),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn)
                                "Tip: In Settings, find it under \"Installed apps\" or \"Downloaded services\"."
                            else
                                "İpucu: Ayarlarda \"Yüklü uygulamalar\" veya \"İndirilen servisler\" başlığı altında bulabilirsiniz.",
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (hasNotificationPermission) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasNotificationPermission) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                text = "2",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (isEn) "Notifications (optional)" else "Bildirimler (isteğe bağlı)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isEn) "Receive protection status and permission reminders." else "Koruma durumu ve izin hatırlatmaları alın.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { 
                            if (!hasNotificationPermission) {
                                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasNotificationPermission) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                            contentColor = if (hasNotificationPermission) MaterialTheme.colorScheme.secondary else Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasNotificationPermission) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shield_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isEn) "Notifications Allowed" else "Bildirimlere izin verildi",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                if (isEn) "Allow Notifications" else "Bildirimlere izin ver",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GİZLİLİK POLİTİKASI & BİLGİLENDİRME KARTI
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lock),
                        contentDescription = null,
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Privacy & Terms" else "Gizlilik ve kullanım koşulları",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) 
                        "By continuing, you agree to our terms. Optional usage data is sent only when you enable it; personal data is never sold." 
                    else 
                        "Devam ederek kullanım şartlarını kabul edersiniz. İsteğe bağlı kullanım verileri yalnızca siz açarsanız gönderilir; kişisel veriler satılmaz.",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/privacy"))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isEn) "Privacy Policy" else "Gizlilik Politikası",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F2FE)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_link_external),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text("•", fontSize = 12.sp, color = Color.Gray)
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/terms"))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isEn) "Terms of Use" else "Kullanım Şartları",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F2FE)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_link_external),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableTextDialog(
    isEn: Boolean = false,
    title: String,
    content: String,
    iconRes: Int? = null,
    iconTint: Color? = null,
    buttonText: String? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = iconTint ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    buttonText ?: if (isEn) "Got it" else "Anladım",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun FeedbackSubmissionDialog(
    manufacturer: String,
    model: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE) }
    val isEn = getAppLanguage(prefs) == "en"
    val appVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
    
    val categories = remember(isEn) {
        listOf(
            Triple("bug", R.drawable.ic_bug_report, if (isEn) "Bug" else "Hata"),
            Triple("idea", R.drawable.ic_lightbulb, if (isEn) "Idea" else "Öneri"),
            Triple("general", R.drawable.ic_chat, if (isEn) "General" else "Genel")
        )
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    val isFormValid = feedbackText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chat),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEn) "Send Feedback" else "Geri bildirim gönder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. KOMPAKT KATEGORİ ÇİPLERİ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEachIndexed { index, (_, iconRes, label) ->
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            onClick = { selectedCategoryIndex = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF070A12) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF070A12) else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. SADE MESAJ ALANI
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text(if (isEn) "Describe your issue or suggestion..." else "Karşılaştığınız sorunu veya önerinizi yazın...", fontSize = 11.5.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. MİNİ CİHAZ BİLGİSİ DİPNOTU
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isEn) "Device info ($manufacturer $model, Android ${Build.VERSION.RELEASE}) will be attached automatically." else "Cihaz bilgisi ($manufacturer $model, Android ${Build.VERSION.RELEASE}) otomatik eklenecektir.",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isFormValid) return@Button

                    val currentCat = categories[selectedCategoryIndex].third
                    val subject = "AwayDoomscrollin' [$currentCat] - $manufacturer $model"
                    val body = if (isEn) {
                        "Category: $currentCat\n\nUser Message:\n$feedbackText\n\n------------------------------\nDevice Info: $manufacturer $model (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\nApp Version: v$appVersionName"
                    } else {
                        "Kategori: $currentCat\n\nKullanıcı Mesajı:\n$feedbackText\n\n------------------------------\nCihaz Bilgisi: $manufacturer $model (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\nUygulama Sürümü: v$appVersionName"
                    }

                    try {
                        val mailtoUrl = "mailto:support@awaydoomscrollin.com" +
                                "?subject=" + Uri.encode(subject) +
                                "&body=" + Uri.encode(body)
                        
                        val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUrl)).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@awaydoomscrollin.com"))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        
                        context.startActivity(Intent.createChooser(mailIntent, if (isEn) "Send email with…" else "E-posta gönder..."))
                        
                        Toast.makeText(
                            context,
                            if (isEn) "Opening email client..." else "E-posta uygulamanız açılıyor...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        prefs.edit().putLong("last_feedback_time_ms", System.currentTimeMillis()).apply()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            if (isEn) "Mail client not found. Please email support@awaydoomscrollin.com" else "E-posta uygulaması bulunamadı. Lütfen support@awaydoomscrollin.com adresine yazın.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = isFormValid,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mail),
                        contentDescription = null,
                        tint = Color(0xFF070A12),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Send Email" else "E-posta gönder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF070A12),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "İptal", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    )
}


// ------------------------------------------
// 5. SEKME: HAKKINDA (ABOUT)
// ------------------------------------------

@Composable
private fun DashboardSectionHeader(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF00F2FE))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF00F2FE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun AboutScreen(
    prefs: android.content.SharedPreferences,
    onReopenOnboarding: (Int) -> Unit
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf<Boolean>(false) }
    var showTelemetryDetails by remember { mutableStateOf(false) }
    var showTelemetryConsent by remember { mutableStateOf(false) }
    var isTelemetryOnInAbout by remember { mutableStateOf(TelemetryManager.isTelemetryEnabled(context)) }

    var currentLang by remember { mutableStateOf<String>(getAppLanguage(prefs)) }
    val isEn = currentLang == "en"

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName?.removeSuffix("-debug") ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }

    if (showFeedbackDialog) {
        FeedbackSubmissionDialog(
            manufacturer = Build.MANUFACTURER ?: "Android",
            model = Build.MODEL ?: "Device",
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (showTelemetryDetails) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "Optional usage data" else "İsteğe bağlı kullanım verileri",
            content = telemetryDisclosureText(isEn),
            iconRes = R.drawable.ic_handshake,
            iconTint = Color(0xFF00F2FE),
            onDismiss = { showTelemetryDetails = false }
        )
    }


    if (showTelemetryConsent) {
        TelemetryConsentDialog(
            isEn = isEn,
            onAccept = {
                TelemetryManager.setTelemetryEnabled(context, true)
                isTelemetryOnInAbout = true
                showTelemetryConsent = false
            },
            onDismiss = { showTelemetryConsent = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            DashboardSectionHeader(
                title = if (isEn) "About" else "Hakkında",
                subtitle = if (isEn) {
                    "App information, privacy and support"
                } else {
                    "Uygulama bilgileri, gizlilik ve destek"
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. KATEGORİ: UYGULAMA KÜNYESİ
            Text(
                text = if (isEn) "APP INFORMATION" else "UYGULAMA BİLGİLERİ",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. UYGULAMA KÜNYESİ KARTI (ENTEGRE GÜVEN ROZETLERİYLE)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_splash_logo),
                                    contentDescription = if (isEn) "App logo" else "Uygulama logosu",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "AwayDoomscrollin'",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isEn) "Version $appVersion" else "Sürüm $appVersion",
                                fontSize = 11.5.sp,
                                color = Color(0xFF00F2FE),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isEn) {
                            "Limits short-content feeds while keeping messaging, search and allowed areas available."
                        } else {
                            "Kısa içerik akışlarını sınırlar; mesajlaşma, arama ve izin verilen alanları açık tutar."
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = Color.White.copy(alpha = 0.68f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(
                                R.drawable.ic_lock,
                                Pair(
                                    if (isEn) "On-device" else "Cihazda çalışır",
                                    if (isEn) "Screen content stays private" else "Ekran içeriği paylaşılmaz"
                                ),
                                Color(0xFF00F2FE)
                            ),
                            Triple(
                                R.drawable.ic_slash_ban,
                                Pair(
                                    if (isEn) "Ad-free" else "Reklamsız",
                                    if (isEn) "No advertising" else "Reklam gösterilmez"
                                ),
                                Color(0xFF00FF87)
                            )
                        ).forEach { (iconRes, labels, color) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF070A12),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = color.copy(alpha = 0.14f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = labels.first,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = labels.second,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                            minLines = 2,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF070A12),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFB703).copy(alpha = 0.14f),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = null,
                                        tint = Color(0xFFFFB703),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEn) "Open source" else "Açık kaynak",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "GNU GPL v3.0",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFB703).copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.35f)),
                                modifier = Modifier.clickable {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://github.com/ResolveCommunity/AwayDoomscrollin")
                                            )
                                        )
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "View on GitHub" else "GitHub'da görüntüle",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB703)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_link_external),
                                        contentDescription = null,
                                        tint = Color(0xFFFFB703),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // KATEGORİ: REHBER
            Text(
                text = if (isEn) "GUIDE" else "REHBER",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReopenOnboarding(1) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFB703).copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lightbulb),
                                contentDescription = null,
                                tint = Color(0xFFFFB703),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Setup & Preview Guide" else "Kurulum ve önizleme rehberi",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) "Revisit the setup steps and the protection preview"
                            else "Kurulum adımlarını ve koruma önizlemesini tekrar gör",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 13.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = Color(0xFFFFB703),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. KATEGORİ: DİL SEÇİMİ
            Text(
                text = if (isEn) "LANGUAGE" else "DİL",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // DİL SEÇİMİ (TR / EN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (!isEn) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color(0xFF0F1523),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (!isEn) Color(0xFF00F2FE) else Color(0xFF1E2A40)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            prefs.edit().putString("app_language", "tr").apply()
                            currentLang = "tr"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            text = "Türkçe",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isEn) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isEn) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color(0xFF0F1523),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isEn) Color(0xFF00F2FE) else Color(0xFF1E2A40)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            prefs.edit().putString("app_language", "en").apply()
                            currentLang = "en"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            text = "English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEn) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. KATEGORİ: BAĞLANTILAR
            Text(
                text = if (isEn) "LINKS" else "BAĞLANTILAR",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // RESMİ WEB PORTALLARI KARTI (awaydoomscrollin.com & resolvecommunity.com)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_globe),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "awaydoomscrollin.com",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isEn) "Updates and app information" else "Güncellemeler ve uygulama bilgileri",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_link_external),
                            contentDescription = null,
                            tint = Color(0xFF00F2FE),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF1E2A40))
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://resolvecommunity.com"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00FF87).copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_users),
                                    contentDescription = null,
                                    tint = Color(0xFF00FF87),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "resolvecommunity.com",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isEn) "Developer and other projects" else "Geliştirici ve diğer projeler",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_link_external),
                            contentDescription = null,
                            tint = Color(0xFF00FF87),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. KATEGORİ: DESTEK VE BİLGİLER
            Text(
                text = if (isEn) "SUPPORT & INFORMATION" else "DESTEK VE BİLGİLER",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. ANA EYLEM KARTI (Şık ve Tek Parça)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // ÖNCELİKLİ BUTON: GERİ BİLDİRİM VEYA HATA BİLDİR
                    Button(
                        onClick = { showFeedbackDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color(0xFF070A12))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lightbulb),
                                contentDescription = null,
                                tint = Color(0xFF070A12),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEn) "Send Feedback or Report Bug" else "Geri bildirim gönder veya hata bildir",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // GİZLİLİK POLİTİKASI & KULLANIM ŞARTLARI YAN YANA (YARI YARIYA)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/privacy"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lock),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isEn) "Privacy Policy" else "Gizlilik Politikası",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00F2FE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/terms"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_document),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isEn) "Terms of Use" else "Kullanım Şartları",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00F2FE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. KATEGORİ: TOPLULUK KATKISI
            Text(
                text = if (isEn) "OPTIONAL USAGE DATA" else "İSTEĞE BAĞLI KULLANIM VERİLERİ",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. TOPLULUK ALGILAMA KATKISI KARTI
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isTelemetryOnInAbout) Color(0xFF00F2FE).copy(alpha = 0.5f) else Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_handshake),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Help improve protection" else "Korumayı geliştirmemize yardımcı olun",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isTelemetryOnInAbout,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showTelemetryConsent = true
                                } else {
                                    TelemetryManager.setTelemetryEnabled(context, false)
                                    isTelemetryOnInAbout = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF070A12),
                                checkedTrackColor = Color(0xFF00F2FE)
                            )
                        )
                    }

                    Text(
                        text = if (isEn) {
                            "If enabled, the device model, app versions and aggregate block counts are shared. Messages and screen content are never included."
                        } else {
                            "Açarsanız cihaz modeli, uygulama sürümleri ve toplu engelleme bilgileri paylaşılır. Mesajlarınız ve ekrandaki içerikler paylaşılmaz."
                        },
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.68f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Text(
                        text = if (isTelemetryOnInAbout) {
                            if (isEn) "On · At most one report every 24 hours" else "Açık · En fazla 24 saatte bir rapor gönderilir"
                        } else {
                            if (isEn) "Off · No report is sent to the server" else "Kapalı · Sunucuya rapor gönderilmez"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTelemetryOnInAbout) Color(0xFF00FF87) else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 7.dp)
                    )

                    TextButton(
                        onClick = { showTelemetryDetails = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isEn) "See shared data and retention" else "Paylaşılan veriler ve saklama süresi",
                            color = Color(0xFF00F2FE),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            // FOOTER
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© 2026 Resolve Community",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "support@awaydoomscrollin.com",
                    fontSize = 10.sp,
                    color = Color(0xFF00F2FE).copy(alpha = 0.75f),
                    modifier = Modifier
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:support@awaydoomscrollin.com")
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 4 SEKMELİ UYGULAMA MENÜSÜ
// ==========================================
@Composable
fun MainNavigationDashboard(
    prefs: android.content.SharedPreferences,
    onReopenOnboarding: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var currentLang by remember { mutableStateOf(getAppLanguage(prefs)) }
    var showAccessibilityConsent by remember { mutableStateOf(false) }
    
    val notificationQueue = remember { mutableStateListOf<UnlockedNotificationItem>() }
    var activeNotification by remember { mutableStateOf<UnlockedNotificationItem?>(null) }
    var showJourneyDialogFromNotification by remember { mutableStateOf(false) }
    var notificationTargetTab by remember { mutableIntStateOf(0) }

    val isEn = currentLang == "en"

    val requestAccessibility: () -> Unit = {
        if (AccessibilityConsent.isAccepted(prefs)) {
            openAccessibilitySettings(context, isEn)
        } else {
            showAccessibilityConsent = true
        }
    }

    if (showAccessibilityConsent) {
        AccessibilityConsentDialog(
            isEn = isEn,
            onAccept = {
                showAccessibilityConsent = false
                acceptAccessibilityDisclosure(context, isEn)
            },
            onDismiss = { showAccessibilityConsent = false }
        )
    }

    fun checkAndQueueUnlocks() {
        val streakDays = prefs.getInt("streak_days", 0)
        val currentTier = getCurrentFocusTier(streakDays)
        val lastSeenTier = prefs.getInt("last_seen_tier_level", 1)
        val seenAchievements = prefs.getStringSet("seen_unlocked_achievements", emptySet())?.toMutableSet() ?: mutableSetOf()

        val allAchievements = getAllAchievements(prefs)
        val newlyUnlockedAchievements = allAchievements.filter { it.isUnlocked && !seenAchievements.contains(it.id) }

        if (currentTier.level > lastSeenTier) {
            prefs.edit().putInt("last_seen_tier_level", currentTier.level).apply()
            val title = if (isEn) "New tier: ${currentTier.nameEn}!" else "Yeni kademe: ${currentTier.nameTr}!"
            val desc = if (isEn) "You reached Tier ${currentTier.level} (${currentTier.minDays}+ day streak)!" else "${currentTier.level}. Kademeye ulaştınız (${currentTier.minDays}+ günlük seri)!"
            val tierItem = UnlockedNotificationItem(
                title = title,
                description = desc,
                iconRes = currentTier.iconRes,
                brandColor = currentTier.color,
                isTierLevelUp = true,
                targetTab = 0
            )
            if (!notificationQueue.any { it.title == tierItem.title } && activeNotification?.title != tierItem.title) {
                notificationQueue.add(tierItem)
            }
        }

        if (newlyUnlockedAchievements.isNotEmpty()) {
            newlyUnlockedAchievements.forEach { newAch ->
                seenAchievements.add(newAch.id)
                val title = if (isEn) "Achievement Unlocked: ${newAch.titleEn}!" else "Başarım Açıldı: ${newAch.titleTr}!"
                val desc = if (isEn) newAch.descEn else newAch.descTr
                val achItem = UnlockedNotificationItem(
                    title = title,
                    description = desc,
                    iconRes = newAch.iconRes,
                    brandColor = newAch.brandColor,
                    isTierLevelUp = false,
                    targetTab = 1
                )
                if (!notificationQueue.any { it.title == achItem.title } && activeNotification?.title != achItem.title) {
                    notificationQueue.add(achItem)
                }
            }
            prefs.edit().putStringSet("seen_unlocked_achievements", seenAchievements).apply()
        }
    }

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "app_language") {
                currentLang = getAppLanguage(sharedPreferences)
            } else if (key in listOf("streak_days", "total_blocks", "blocks_instagram", "blocks_tiktok", "blocks_youtube", "last_seen_tier_level", "trigger_notification_check", "seen_unlocked_achievements")) {
                checkAndQueueUnlocks()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Check for newly reached tiers or unlocked achievements when entering dashboard
    LaunchedEffect(Unit) {
        checkAndQueueUnlocks()
    }

    // Queue Consumer: pops next pending notification whenever active is null
    LaunchedEffect(activeNotification, notificationQueue.size) {
        if (activeNotification == null && notificationQueue.isNotEmpty()) {
            delay(280L)
            if (activeNotification == null && notificationQueue.isNotEmpty()) {
                val nextItem = notificationQueue.removeAt(0)
                notificationTargetTab = nextItem.targetTab
                activeNotification = nextItem
            }
        }
    }

    // Auto-dismiss active notification after 5.5s
    LaunchedEffect(activeNotification) {
        if (activeNotification != null) {
            delay(5500L)
            activeNotification = null
        }
    }

    if (showJourneyDialogFromNotification) {
        FocusJourneyDialog(
            prefs = prefs,
            isEn = isEn,
            initialTab = notificationTargetTab,
            onDismiss = { showJourneyDialogFromNotification = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
                tonalElevation = 12.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_home,
                        label = if (isEn) "Home" else "Ana Sayfa",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_apps,
                        label = if (isEn) "Apps" else "Uygulamalar",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_analytics,
                        label = if (isEn) "Analytics" else "Analiz",
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_about,
                        label = if (isEn) "About" else "Hakkında",
                        isSelected = selectedTab == 3,
                        onClick = { selectedTab = 3 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(160)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                label = "TabSwitchAnimation"
            ) { tab ->
                // Home draws its ambient glow behind the status bar itself; the other tabs
                // keep their content below it.
                if (tab == 0) {
                    HomeScreen(
                        onRequestAccessibility = requestAccessibility,
                        prefs = prefs
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        when (tab) {
                            1 -> ModesAndAppsScreen(
                                prefs = prefs,
                                onRequestAccessibility = requestAccessibility
                            )
                            2 -> ProgressStatusScreen(prefs = prefs)
                            3 -> AboutScreen(
                                prefs = prefs,
                                onReopenOnboarding = onReopenOnboarding
                            )
                        }
                    }
                }
            }

            // In-app sliding celebration toast banner
            AnimatedVisibility(
                visible = activeNotification != null,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(250)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .zIndex(100f)
            ) {
                activeNotification?.let { item ->
                    InAppSlidingToastBanner(
                        item = item,
                        onOpenJourney = {
                            showJourneyDialogFromNotification = true
                            activeNotification = null
                        },
                        onDismiss = {
                            activeNotification = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CyberNavItem(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.18f else 0.0f,
        animationSpec = tween(durationMillis = 250),
        label = "NavGlow"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF00F2FE).copy(alpha = glowAlpha) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.65f)) else null,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BorderlessHeroStatusSection(
    isEn: Boolean,
    isAccessibilityActive: Boolean,
    todayInterventions: Int,
    onActivateClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LedPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val activeColor = Color(0xFF00FF87)
    val inactiveColor = Color(0xFFFF0055)
    val statusColor = if (isAccessibilityActive) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Minimalist Status Indicator Row (Pulsing Dot + Text)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (isAccessibilityActive) dotAlpha else 1f)
                    .background(color = statusColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAccessibilityActive) {
                    if (isEn) "PROTECTION ON" else "KORUMA AÇIK"
                } else {
                    if (isEn) "PERMISSION REQUIRED" else "İZİN GEREKLİ"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isAccessibilityActive) {
            Text(
                text = if (isEn) "$todayInterventions blocked" else "$todayInterventions engelleme",
                fontSize = if (todayInterventions >= 10_000) 32.sp else 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEn) "short-content attempts blocked today" else "bugün durdurulan kısa içerik girişimleri",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = if (isEn) "Protection is off" else "Koruma kapalı",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isEn)
                    "Turn on Accessibility so AwayDoomscrollin' can protect you automatically."
                else
                    "Otomatik korumayı başlatmak için Erişilebilirlik iznini açın.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            val onRequestAccessibility = onActivateClick
            Button(
                onClick = onRequestAccessibility,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF0055),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "TURN ON PROTECTION" else "KORUMAYI AÇ",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BorderlessPeakHourRow(
    peakHour: Pair<Int, Int>,
    isEn: Boolean
) {
    if (peakHour.first >= 0 && peakHour.second >= 1) {
        val h = peakHour.first
        val cnt = peakHour.second
        val timeRange = "${h.toString().padStart(2, '0')}:00 - ${(h + 1).toString().padStart(2, '0')}:00"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF0055))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEn) "BUSIEST HOUR TODAY: $timeRange" else "BUGÜN EN YOĞUN SAAT: $timeRange",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF0055),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEn) "$cnt attempts were blocked during this hour." else "Bu saat aralığında $cnt girişim engellendi.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ==========================================
// 12 FOCUS TIERS & GAMIFICATION SYSTEM (LOCAL VECTOR DRAWABLES)
// ==========================================
data class FocusTier(
    val level: Int,
    val minDays: Int,
    val maxDays: Int,
    val nameTr: String,
    val nameEn: String,
    val iconRes: Int,
    val color: Color,
    val descTr: String,
    val descEn: String,
    val neuroBenefitTr: String,
    val neuroBenefitEn: String
)

val ALL_FOCUS_TIERS = listOf(
    FocusTier(
        level = 1, minDays = 0, maxDays = 0,
        nameTr = "Tohum", nameEn = "Seed",
        iconRes = R.drawable.ic_tier_seed,
        color = Color(0xFF00F2FE),
        descTr = "Yeni bir başlangıç. Dijital farkındalığın ilk adımı atıldı.",
        descEn = "A fresh start. The first step toward digital awareness.",
        neuroBenefitTr = "Kısa içerik alışkanlığını gözlemlemeye başlama",
        neuroBenefitEn = "Starting to notice short-content habits"
    ),
    FocusTier(
        level = 2, minDays = 1, maxDays = 2,
        nameTr = "İlk Kıvılcım", nameEn = "First Spark",
        iconRes = R.drawable.ic_tier_spark,
        color = Color(0xFF00FF87),
        descTr = "İlk koruma günlerini tamamladınız ve düzenli kullanıma başladınız.",
        descEn = "You completed your first days of protection and started building consistency.",
        neuroBenefitTr = "Dürtü geldiğinde kısa bir ara verme pratiği",
        neuroBenefitEn = "Practicing a brief pause when the urge appears"
    ),
    FocusTier(
        level = 3, minDays = 3, maxDays = 5,
        nameTr = "Yeni Ritim", nameEn = "New Rhythm",
        iconRes = R.drawable.ic_tier_fire,
        color = Color(0xFFFF5500),
        descTr = "Koruma seriniz üç günü geçti. Kısa içeriklere ayırdığınız alanı sınırlamayı sürdürüyorsunuz.",
        descEn = "Your protection streak passed three days. You keep limiting the space you give to short content.",
        neuroBenefitTr = "Koruma düzenini sürdürme",
        neuroBenefitEn = "Maintaining a consistent protection routine"
    ),
    FocusTier(
        level = 4, minDays = 6, maxDays = 9,
        nameTr = "Kararlı Adım", nameEn = "Steady Step",
        iconRes = R.drawable.ic_tier_bolt,
        color = Color(0xFFFFB700),
        descTr = "Bir haftaya yaklaşan koruma seriniz istikrarlı biçimde devam ediyor.",
        descEn = "Your protection streak is approaching one week with steady progress.",
        neuroBenefitTr = "Günlük tercihlerde daha fazla süreklilik",
        neuroBenefitEn = "More consistency in daily choices"
    ),
    FocusTier(
        level = 5, minDays = 10, maxDays = 13,
        nameTr = "Akışa Hâkim", nameEn = "Flow Master",
        iconRes = R.drawable.ic_tier_water,
        color = Color(0xFF00E5FF),
        descTr = "Koruma seriniz 10 güne ulaştı. Kısa içerik sınırlarınızı koruyorsunuz.",
        descEn = "Your protection streak reached 10 days. You are keeping your short-content limits.",
        neuroBenefitTr = "Koruma tercihini sürdürme",
        neuroBenefitEn = "Sticking with your protection routine"
    ),
    FocusTier(
        level = 6, minDays = 14, maxDays = 20,
        nameTr = "İki Haftalık Seri", nameEn = "Two-Week Streak",
        iconRes = R.drawable.ic_tier_crystal,
        color = Color(0xFFBD00FF),
        descTr = "İki haftalık koruma serisini geride bıraktınız.",
        descEn = "You have completed a two-week protection streak.",
        neuroBenefitTr = "Kısa içerik kullanımında daha bilinçli sınırlar",
        neuroBenefitEn = "More deliberate limits around short-content use"
    ),
    FocusTier(
        level = 7, minDays = 21, maxDays = 29,
        nameTr = "Üç Haftalık Seri", nameEn = "Three-Week Streak",
        iconRes = R.drawable.ic_tier_brain,
        color = Color(0xFFFF2A85),
        descTr = "Koruma seriniz üç haftaya ulaştı. Düzeninizi korumaya devam ediyorsunuz.",
        descEn = "Your protection streak reached three weeks. You continue maintaining your routine.",
        neuroBenefitTr = "Düzenli koruma kullanımını sürdürme",
        neuroBenefitEn = "Sustaining regular protection use"
    ),
    FocusTier(
        level = 8, minDays = 30, maxDays = 44,
        nameTr = "Odak Şampiyonu", nameEn = "Focus Champion",
        iconRes = R.drawable.ic_tier_crown,
        color = Color(0xFFFFD700),
        descTr = "Bir aylık koruma serisine ulaştınız.",
        descEn = "You reached a one-month protection streak.",
        neuroBenefitTr = "Kısa içerik sınırlarını uzun süre koruma",
        neuroBenefitEn = "Keeping short-content limits over a longer period"
    ),
    FocusTier(
        level = 9, minDays = 45, maxDays = 59,
        nameTr = "Elmas Disiplin", nameEn = "Diamond Discipline",
        iconRes = R.drawable.ic_tier_gem,
        color = Color(0xFF00F5D4),
        descTr = "Koruma seriniz 45 günü geçti.",
        descEn = "Your protection streak passed 45 days.",
        neuroBenefitTr = "Kendi kullanım sınırlarınıza bağlı kalma",
        neuroBenefitEn = "Staying aligned with your own usage limits"
    ),
    FocusTier(
        level = 10, minDays = 60, maxDays = 89,
        nameTr = "Zaman Mimarı", nameEn = "Time Architect",
        iconRes = R.drawable.ic_tier_monument,
        color = Color(0xFF9D4EDD),
        descTr = "İki aylık koruma serisine ulaştınız.",
        descEn = "You reached a two-month protection streak.",
        neuroBenefitTr = "Uzun vadeli koruma düzenini sürdürme",
        neuroBenefitEn = "Maintaining a long-term protection routine"
    ),
    FocusTier(
        level = 11, minDays = 90, maxDays = 99,
        nameTr = "Döngü Efendisi", nameEn = "Loop Master",
        iconRes = R.drawable.ic_tier_galaxy,
        color = Color(0xFFFF0055),
        descTr = "Koruma seriniz 90 güne ulaştı.",
        descEn = "Your protection streak reached 90 days.",
        neuroBenefitTr = "Belirlediğiniz dijital sınırları koruma",
        neuroBenefitEn = "Keeping the digital limits you chose"
    ),
    FocusTier(
        level = 12, minDays = 100, maxDays = Int.MAX_VALUE,
        nameTr = "Zen Ustası", nameEn = "Zen Master",
        iconRes = R.drawable.ic_tier_zen,
        color = Color(0xFFFFE600),
        descTr = "100 günlük koruma serisini aştınız.",
        descEn = "You completed a 100-day protection streak.",
        neuroBenefitTr = "Uzun süreli ve tutarlı koruma kullanımı",
        neuroBenefitEn = "Long-term, consistent protection use"
    )
)

fun getCurrentFocusTier(streakDays: Int): FocusTier {
    val safeDays = streakDays.coerceAtLeast(0)
    return ALL_FOCUS_TIERS.find { safeDays >= it.minDays && safeDays <= it.maxDays }
        ?: ALL_FOCUS_TIERS.last()
}

fun getNextFocusTier(streakDays: Int): FocusTier? {
    val safeDays = streakDays.coerceAtLeast(0)
    val current = getCurrentFocusTier(safeDays)
    val nextIndex = ALL_FOCUS_TIERS.indexOf(current) + 1
    return if (nextIndex < ALL_FOCUS_TIERS.size) ALL_FOCUS_TIERS[nextIndex] else null
}

fun getTierProgress(streakDays: Int): Float {
    val safeDays = streakDays.coerceAtLeast(0)
    val current = getCurrentFocusTier(safeDays)
    val next = getNextFocusTier(safeDays) ?: return 1.0f
    val span = next.minDays - current.minDays
    if (span <= 0) return 1.0f
    val elapsed = safeDays - current.minDays
    return (elapsed.toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

// ------------------------------------------
// APP-SPECIFIC FOCUS ACHIEVEMENTS (LOCAL VECTOR DRAWABLES)
// ------------------------------------------
enum class AchievementCategory {
    ALL, INSTAGRAM, TIKTOK, YOUTUBE, GENERAL
}

data class FocusAchievement(
    val id: String,
    val category: AchievementCategory,
    val titleTr: String,
    val titleEn: String,
    val descTr: String,
    val descEn: String,
    val iconRes: Int,
    val brandColor: Color,
    val currentVal: Int,
    val targetVal: Int
) {
    val isUnlocked: Boolean get() = currentVal >= targetVal
    val progress: Float get() = if (targetVal <= 0) 1f else (currentVal.toFloat() / targetVal.toFloat()).coerceIn(0f, 1f)
}

fun getAllAchievements(prefs: SharedPreferences): List<FocusAchievement> {
    val totalBlocks = prefs.getInt("total_blocks", 0)
    val streakDays = prefs.getInt("streak_days", 0)
    val blocksInsta = prefs.getInt("blocks_instagram", 0)
    val blocksTiktok = prefs.getInt("blocks_tiktok", 0)
    val blocksYt = prefs.getInt("blocks_youtube", 0)

    return listOf(
        // --- INSTAGRAM REELS & AKIŞ ---
        FocusAchievement(
            id = "ig_1",
            category = AchievementCategory.INSTAGRAM,
            titleTr = "İlk Reels Freni",
            titleEn = "First Reels Block",
            descTr = "Bir Instagram Reels açılışını engelle",
            descEn = "Block your first Instagram Reels open",
            iconRes = R.drawable.ic_instagram,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 1
        ),
        FocusAchievement(
            id = "ig_10",
            category = AchievementCategory.INSTAGRAM,
            titleTr = "Reels Direnci",
            titleEn = "Reels Resistance",
            descTr = "Instagram'da 10 engellemeye ulaş",
            descEn = "Reach 10 blocks on Instagram",
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 10
        ),
        FocusAchievement(
            id = "ig_50",
            category = AchievementCategory.INSTAGRAM,
            titleTr = "Algoritma Fatihi",
            titleEn = "Algorithm Conqueror",
            descTr = "Instagram'da 50 engellemeye ulaş",
            descEn = "Reach 50 blocks on Instagram",
            iconRes = R.drawable.ic_tier_bolt,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 50
        ),
        FocusAchievement(
            id = "ig_100",
            category = AchievementCategory.INSTAGRAM,
            titleTr = "Reels Muhafızı",
            titleEn = "Reels Sentinel",
            descTr = "Instagram'da 100 engellemeye ulaş",
            descEn = "Reach 100 blocks on Instagram",
            iconRes = R.drawable.ic_tier_crown,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 100
        ),

        // --- TIKTOK FEED ---
        FocusAchievement(
            id = "tt_1",
            category = AchievementCategory.TIKTOK,
            titleTr = "İlk TikTok Kalkanı",
            titleEn = "First TikTok Shield",
            descTr = "TikTok akışında bir kaydırmayı durdur",
            descEn = "Stop the TikTok 'For You' feed once",
            iconRes = R.drawable.ic_tiktok,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 1
        ),
        FocusAchievement(
            id = "tt_10",
            category = AchievementCategory.TIKTOK,
            titleTr = "Akış Freni",
            titleEn = "Feed Breaker",
            descTr = "TikTok'ta 10 engellemeye ulaş",
            descEn = "Reach 10 blocks on TikTok",
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 10
        ),
        FocusAchievement(
            id = "tt_50",
            category = AchievementCategory.TIKTOK,
            titleTr = "Sonsuz Akış Kırıcı",
            titleEn = "Endless Feed Breaker",
            descTr = "TikTok'ta 50 engellemeye ulaş",
            descEn = "Reach 50 blocks on TikTok",
            iconRes = R.drawable.ic_tier_crystal,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 50
        ),
        FocusAchievement(
            id = "tt_100",
            category = AchievementCategory.TIKTOK,
            titleTr = "TikTok Efendisi",
            titleEn = "TikTok Sovereign",
            descTr = "TikTok'ta 100 engellemeye ulaş",
            descEn = "Reach 100 blocks on TikTok",
            iconRes = R.drawable.ic_tier_gem,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 100
        ),

        // --- YOUTUBE SHORTS ---
        FocusAchievement(
            id = "yt_1",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Shorts Kilidi",
            titleEn = "Shorts Lock",
            descTr = "Bir YouTube Shorts açılışını engelle",
            descEn = "Block your first YouTube Shorts open",
            iconRes = R.drawable.ic_youtube,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 1
        ),
        FocusAchievement(
            id = "yt_10",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Shorts Kalkanı",
            titleEn = "Shorts Shield",
            descTr = "YouTube Shorts'ta 10 engellemeye ulaş",
            descEn = "Reach 10 blocks on YouTube Shorts",
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 10
        ),
        FocusAchievement(
            id = "yt_50",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Kırmızı Çizgi Savunması",
            titleEn = "Red Line Defense",
            descTr = "YouTube Shorts'ta 50 engellemeye ulaş",
            descEn = "Reach 50 blocks on YouTube Shorts",
            iconRes = R.drawable.ic_target,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 50
        ),
        FocusAchievement(
            id = "yt_100",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Shorts 100",
            titleEn = "Shorts 100",
            descTr = "YouTube Shorts'ta 100 engellemeye ulaş",
            descEn = "Reach 100 blocks on YouTube Shorts",
            iconRes = R.drawable.ic_trophy,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 100
        ),

        // --- GENEL DİSİPLİN & SERİ ---
        FocusAchievement(
            id = "gen_1",
            category = AchievementCategory.GENERAL,
            titleTr = "Uyanış",
            titleEn = "Awakening",
            descTr = "İlk kısa içerik girişimini engelle",
            descEn = "Block your first short-content attempt",
            iconRes = R.drawable.ic_tier_seed,
            brandColor = Color(0xFF00FF87),
            currentVal = totalBlocks,
            targetVal = 1
        ),
        FocusAchievement(
            id = "gen_streak_3",
            category = AchievementCategory.GENERAL,
            titleTr = "3 Günlük Kıvılcım",
            titleEn = "3-Day Spark",
            descTr = "3 günlük koruma serisine ulaş",
            descEn = "Reach a 3-day protection streak",
            iconRes = R.drawable.ic_tier_fire,
            brandColor = Color(0xFFFF5500),
            currentVal = streakDays,
            targetVal = 3
        ),
        FocusAchievement(
            id = "gen_streak_7",
            category = AchievementCategory.GENERAL,
            titleTr = "1 Haftalık İrade",
            titleEn = "1-Week Fortitude",
            descTr = "7 günlük koruma serisine ulaş",
            descEn = "Reach a 7-day protection streak",
            iconRes = R.drawable.ic_tier_bolt,
            brandColor = Color(0xFFFFB700),
            currentVal = streakDays,
            targetVal = 7
        ),
        FocusAchievement(
            id = "gen_streak_21",
            category = AchievementCategory.GENERAL,
            titleTr = "21 Günlük Seri",
            titleEn = "21-Day Streak",
            descTr = "21 günlük koruma serisine ulaş",
            descEn = "Reach a 21-day protection streak",
            iconRes = R.drawable.ic_tier_brain,
            brandColor = Color(0xFFFF2A85),
            currentVal = streakDays,
            targetVal = 21
        ),
        FocusAchievement(
            id = "gen_100_blocks",
            category = AchievementCategory.GENERAL,
            titleTr = "Yüzbaşı Kalkan",
            titleEn = "Centurion Shield",
            descTr = "Toplam 100 engellemeye ulaş",
            descEn = "Reach 100 blocks overall",
            iconRes = R.drawable.ic_trophy,
            brandColor = Color(0xFFFFD700),
            currentVal = totalBlocks,
            targetVal = 100
        )
    )
}

// ------------------------------------------
// IN-APP SLIDING TOAST NOTIFICATION MODEL
// ------------------------------------------
data class UnlockedNotificationItem(
    val title: String,
    val description: String,
    val iconRes: Int,
    val brandColor: Color,
    val isTierLevelUp: Boolean = false,
    val targetTab: Int = 0
)

@Composable
fun InAppSlidingToastBanner(
    item: UnlockedNotificationItem,
    onOpenJourney: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        onClick = onOpenJourney,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, item.brandColor.copy(alpha = 0.85f)),
        shadowElevation = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(item.brandColor.copy(alpha = 0.2f), CircleShape)
                    .border(1.2.dp, item.brandColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = item.brandColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = item.brandColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FieryGlowingStreakBadge(
    streakDays: Int, 
    isEn: Boolean,
    onClick: (() -> Unit)? = null
) {
    val tier = getCurrentFocusTier(streakDays)
    val infiniteTransition = rememberInfiniteTransition(label = "streakGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(10.dp),
        color = tier.color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tier.color.copy(alpha = glowAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = tier.iconRes),
                contentDescription = null,
                tint = tier.color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isEn) "$streakDays DAY" else "$streakDays GÜN",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = tier.color
            )
        }
    }
}

@Composable
fun FocusJourneyDialog(
    prefs: SharedPreferences,
    isEn: Boolean,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val totalBlocks = prefs.getInt("total_blocks", 0)
    val streakDays = prefs.getInt("streak_days", 0)
    val currentTier = getCurrentFocusTier(streakDays)
    val nextTier = getNextFocusTier(streakDays)
    val tierProgress = getTierProgress(streakDays)
    val allAchievements = remember(totalBlocks, streakDays) { getAllAchievements(prefs) }

    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var selectedCategory by remember { mutableStateOf(AchievementCategory.ALL) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0D121F),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE).copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Top Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF00F2FE).copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_trophy),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isEn) "Achievements" else "Başarımlar",
                                    fontSize = 17.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isEn) "Focus Tiers & Special Achievements" else "Odak kademeleri ve özel başarımlar",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = Color(0xFF00F2FE),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Close Button
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // 2. Tab Navigation Switch (Kademeler vs Özel Başarımlar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .background(Color(0xFF070B12), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, if (isEn) "Tiers" else "Kademeler", R.drawable.ic_tier_galaxy),
                            Triple(1, if (isEn) "Special Achievements" else "Özel Başarımlar", R.drawable.ic_trophy)
                        )

                        tabs.forEach { (idx, label, iconRes) ->
                            val isSelected = selectedTab == idx
                            Surface(
                                onClick = { selectedTab = idx },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.7f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Tab Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {
                        if (selectedTab == 0) {
                            // TAB 0: TIERS ROADMAP
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // A. Current Tier Hero Box
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = currentTier.color.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, currentTier.color.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .background(currentTier.color.copy(alpha = 0.2f), CircleShape)
                                                    .border(1.5.dp, currentTier.color, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = currentTier.iconRes),
                                                    contentDescription = null,
                                                    tint = currentTier.color,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (isEn) "CURRENT LEVEL ${currentTier.level}" else "MEVCUT KADEME ${currentTier.level}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = currentTier.color,
                                                        letterSpacing = 0.8.sp
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = currentTier.color.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = if (isEn) "Day $streakDays" else "$streakDays. Gün",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = currentTier.color,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (isEn) currentTier.nameEn else currentTier.nameTr,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = if (isEn) currentTier.descEn else currentTier.descTr,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            lineHeight = 17.sp
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Neuro benefit highlight
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF070B12),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00FF87).copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_tier_brain),
                                                    contentDescription = null,
                                                    tint = Color(0xFF00FF87),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isEn) "Benefit at this level: ${currentTier.neuroBenefitEn}" else "Bu kademenin kazancı: ${currentTier.neuroBenefitTr}",
                                                    fontSize = 11.sp,
                                                    lineHeight = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF00FF87),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Next tier bar
                                        if (nextTier != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Text(
                                                        text = if (isEn) "Next:" else "Sıradaki:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color.White.copy(alpha = 0.65f)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isEn) "Level ${nextTier.level} · ${nextTier.nameEn}" else "${nextTier.level}. Kademe · ${nextTier.nameTr}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val remaining = nextTier.minDays - streakDays
                                                Text(
                                                    text = if (isEn) {
                                                        if (remaining == 1) "1 day left" else "$remaining days left"
                                                    } else "$remaining gün kaldı",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = currentTier.color,
                                                    maxLines = 1
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = tierProgress,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = currentTier.color,
                                                trackColor = Color(0xFF1E2A40)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // B. Full Timeline Header
                                Text(
                                    text = if (isEn) "TIERS ROADMAP" else "KADEME HARİTASI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // C. List of all 12 tiers
                                ALL_FOCUS_TIERS.forEachIndexed { idx, tierItem ->
                                    val isCurrent = tierItem.level == currentTier.level
                                    val isUnlocked = streakDays >= tierItem.minDays
                                    val isCompleted = streakDays > tierItem.maxDays

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Left Indicator Pillar
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(36.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(
                                                        if (isCurrent) tierItem.color.copy(alpha = 0.25f)
                                                        else if (isUnlocked) Color(0xFF00FF87).copy(alpha = 0.15f)
                                                        else Color(0xFF161E2E),
                                                        CircleShape
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isCurrent) tierItem.color
                                                        else if (isUnlocked) Color(0xFF00FF87).copy(alpha = 0.7f)
                                                        else Color(0xFF1E2A40),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isCompleted) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_shield_check),
                                                        contentDescription = null,
                                                        tint = Color(0xFF00FF87),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                } else if (isCurrent) {
                                                    Icon(
                                                        painter = painterResource(id = tierItem.iconRes),
                                                        contentDescription = null,
                                                        tint = tierItem.color,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                } else {
                                                    Text(
                                                        "${tierItem.level}",
                                                        color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.35f),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (idx < ALL_FOCUS_TIERS.size - 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(48.dp)
                                                        .background(
                                                            if (isUnlocked) Color(0xFF00FF87).copy(alpha = 0.3f)
                                                            else Color(0xFF1E2A40)
                                                        )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Right Tier Info Card
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isCurrent) tierItem.color.copy(alpha = 0.12f)
                                            else if (isUnlocked) Color(0xFF131A2A)
                                            else Color(0xFF0D121D),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCurrent) tierItem.color.copy(alpha = 0.8f)
                                                else if (isUnlocked) Color(0xFF1E2A40)
                                                else Color(0xFF161E2E)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (isUnlocked) 1f else 0.55f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            painter = painterResource(id = tierItem.iconRes),
                                                            contentDescription = null,
                                                            tint = if (isCurrent) tierItem.color else Color.White.copy(alpha = 0.85f),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "${tierItem.level}. ${if (isEn) tierItem.nameEn else tierItem.nameTr}",
                                                            fontSize = 13.sp,
                                                            lineHeight = 16.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCurrent) tierItem.color else Color.White
                                                        )
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isCurrent) tierItem.color.copy(alpha = 0.2f)
                                                        else if (isCompleted) Color(0xFF00FF87).copy(alpha = 0.15f)
                                                        else Color.White.copy(alpha = 0.05f)
                                                    ) {
                                                        Text(
                                                            text = if (tierItem.maxDays == Int.MAX_VALUE) "${tierItem.minDays}+ ${if (isEn) "Days" else "Gün"}"
                                                                   else if (tierItem.minDays == tierItem.maxDays) "${tierItem.minDays} ${if (isEn) "Day" else "Gün"}"
                                                                   else "${tierItem.minDays}-${tierItem.maxDays} ${if (isEn) "Days" else "Gün"}",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCurrent) tierItem.color
                                                            else if (isCompleted) Color(0xFF00FF87)
                                                            else Color.White.copy(alpha = 0.5f),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = if (isEn) tierItem.descEn else tierItem.descTr,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    lineHeight = 14.sp
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_tier_brain),
                                                        contentDescription = null,
                                                        tint = if (isCurrent) Color(0xFF00FF87) else Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isEn) tierItem.neuroBenefitEn else tierItem.neuroBenefitTr,
                                                        fontSize = 10.sp,
                                                        lineHeight = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isCurrent) Color(0xFF00FF87) else Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        } else {
                            // TAB 1: ACHIEVEMENTS & TROPHIES
                            Column(modifier = Modifier.fillMaxSize()) {
                                // A. Category Filter Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val categories = listOf(
                                        Triple(AchievementCategory.ALL, if (isEn) "All" else "Hepsi", null),
                                        Triple(AchievementCategory.INSTAGRAM, "Instagram", R.drawable.ic_instagram),
                                        Triple(AchievementCategory.TIKTOK, "TikTok", R.drawable.ic_tiktok),
                                        Triple(AchievementCategory.YOUTUBE, "YouTube", R.drawable.ic_youtube),
                                        Triple(AchievementCategory.GENERAL, if (isEn) "Discipline" else "Disiplin", R.drawable.ic_shield)
                                    )

                                    categories.forEach { (cat, label, iconRes) ->
                                        val isCatSelected = selectedCategory == cat
                                        Surface(
                                            onClick = { selectedCategory = cat },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCatSelected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color(0xFF131A2A),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCatSelected) Color(0xFF00F2FE) else Color(0xFF1E2A40)
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                if (iconRes != null) {
                                                    Icon(
                                                        painter = painterResource(id = iconRes),
                                                        contentDescription = null,
                                                        tint = if (isCatSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                }
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCatSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // B. Achievements List
                                val filteredAchievements = if (selectedCategory == AchievementCategory.ALL) {
                                    allAchievements
                                } else {
                                    allAchievements.filter { it.category == selectedCategory }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredAchievements.forEach { ach ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (ach.isUnlocked) ach.brandColor.copy(alpha = 0.12f) else Color(0xFF0D121D),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (ach.isUnlocked) ach.brandColor.copy(alpha = 0.65f) else Color(0xFF1E2A40)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .alpha(if (ach.isUnlocked) 1f else 0.6f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Icon Box
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(
                                                            if (ach.isUnlocked) ach.brandColor.copy(alpha = 0.2f) else Color(0xFF161E2E),
                                                            CircleShape
                                                        )
                                                        .border(
                                                            1.2.dp,
                                                            if (ach.isUnlocked) ach.brandColor else Color(0xFF1E2A40),
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = ach.iconRes),
                                                        contentDescription = null,
                                                        tint = if (ach.isUnlocked) ach.brandColor else Color.Gray,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = if (isEn) ach.titleEn else ach.titleTr,
                                                            fontSize = 13.5.sp,
                                                            lineHeight = 16.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (ach.isUnlocked) Color.White else Color.White.copy(alpha = 0.8f)
                                                        )

                                                        if (ach.isUnlocked) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFF00FF87).copy(alpha = 0.2f),
                                                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00FF87))
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        painter = painterResource(id = R.drawable.ic_shield_check),
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF00FF87),
                                                                        modifier = Modifier.size(10.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text(
                                                                        text = if (isEn) "UNLOCKED" else "AÇILDI",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = Color(0xFF00FF87)
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            Text(
                                                                text = "${ach.currentVal} / ${ach.targetVal}",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Text(
                                                        text = if (isEn) ach.descEn else ach.descTr,
                                                        fontSize = 11.sp,
                                                        lineHeight = 14.sp,
                                                        color = Color.White.copy(alpha = 0.65f)
                                                    )

                                                    if (!ach.isUnlocked) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        LinearProgressIndicator(
                                                            progress = ach.progress,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(4.dp)
                                                                .clip(RoundedCornerShape(2.dp)),
                                                            color = ach.brandColor,
                                                            trackColor = Color(0xFF1E2A40)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    // 4. Bottom Dismiss Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = if (isEn) "Close" else "Kapat",
                                color = Color(0xFF0A0E1A),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 1. SEKME: ANA SAYFA (HOME SCREEN)
// ------------------------------------------
@Composable
fun HomeScreen(
    onRequestAccessibility: () -> Unit,
    prefs: android.content.SharedPreferences
) {
    val isAccessibilityActive = rememberAccessibilityStatus()
    val isEn = getAppLanguage(prefs) == "en"
    var todayStr by remember { mutableStateOf(InstagramProtectionMetrics.dateString(System.currentTimeMillis())) }
    var todayInterventions by remember { mutableIntStateOf(prefs.getInt("blocks_$todayStr", 0)) }
    var showJourneyDialog by remember { mutableStateOf(false) }

    if (showJourneyDialog) {
        FocusJourneyDialog(
            prefs = prefs,
            isEn = isEn,
            onDismiss = { showJourneyDialog = false }
        )
    }

    LaunchedEffect(Unit) {
        while(true) {
            val currentDay = InstagramProtectionMetrics.dateString(System.currentTimeMillis())
            todayStr = currentDay
            todayInterventions = prefs.getInt("blocks_$currentDay", 0)
            kotlinx.coroutines.delay(1000)
        }
    }

    // DİNAMİK AMBIENT ARKA PLAN IŞILTISI (Aktifken Canlı Yeşil / Pasifken Uyarıcı Kırmızı)
    val glowColor by animateColorAsState(
        targetValue = if (isAccessibilityActive) Color(0xFF00FF87) else Color(0xFFFF0055),
        animationSpec = tween(durationMillis = 800),
        label = "HomeScreenAmbientColor"
    )

    val infiniteGlow = rememberInfiniteTransition(label = "AmbientGlowTransition")
    val ambientPulse by infiniteGlow.animateFloat(
        initialValue = if (isAccessibilityActive) 0.14f else 0.22f,
        targetValue = if (isAccessibilityActive) 0.32f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. ANA ARKA PLAN AMBIENT GLOW (Sol Üst / Merkez Odaklı Radyal Halka)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = ambientPulse),
                            glowColor.copy(alpha = ambientPulse * 0.4f),
                            glowColor.copy(alpha = ambientPulse * 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(240f, 180f),
                        radius = 900f
                    )
                )
        )

        // 2. İKİNCİL SAĞ ÜST YAYILMA (Siberpunk derinlik ve atmosfer)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = ambientPulse * 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(950f, 90f),
                        radius = 700f
                    )
                )
        )

        // ANA İÇERİK AKIŞI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Clean Header: [Logo 30dp] + Away (Beyaz) + Doomscrollin' (Cyan) + Fiery Streak Badge (Top Right)
            val streakDays = prefs.getInt("streak_days", 0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F1523),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.35f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_splash_logo),
                                contentDescription = if (isEn) "App logo" else "Uygulama logosu",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    // Soft Vertical Divider Line
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .height(18.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Away",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Doomscrollin'",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00F2FE)
                            )
                        }
                        Text(
                            text = "by Resolve Community",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                FieryGlowingStreakBadge(
                    streakDays = streakDays, 
                    isEn = isEn,
                    onClick = { showJourneyDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OPTION B: BORDERLESS HERO STATUS & METRIC SECTION
            BorderlessHeroStatusSection(
                isEn = isEn,
                isAccessibilityActive = isAccessibilityActive,
                todayInterventions = todayInterventions,
                onActivateClick = onRequestAccessibility
            )



            // BORDERLESS PEAK HOUR ROW
            val peakHour = remember(todayInterventions, todayStr) {
                var maxBlocks = 0
                var peakH = -1
                for (h in 0..23) {
                    val key = "blocks_${todayStr}_hour_${h.toString().padStart(2, '0')}"
                    val count = prefs.getInt(key, 0)
                    if (count > maxBlocks) { maxBlocks = count; peakH = h }
                }
                Pair(peakH, maxBlocks)
            }

            if (isAccessibilityActive) {
                BorderlessPeakHourRow(
                    peakHour = peakHour,
                    isEn = isEn
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            // ⏱️ BORDERLESS SHIELD ACTIVITY LOG
            BorderlessShieldActivityLog(prefs = prefs)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BorderlessShieldActivityLog(prefs: android.content.SharedPreferences) {
    val isEn = getAppLanguage(prefs) == "en"
    var rawLogs by remember { mutableStateOf(prefs.getString("recent_shield_logs", "") ?: "") }

    LaunchedEffect(Unit) {
        while (true) {
            rawLogs = prefs.getString("recent_shield_logs", "") ?: ""
            kotlinx.coroutines.delay(1500)
        }
    }

    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    val logList = remember(rawLogs) {
        if (rawLogs.isBlank()) emptyList()
        else {
            val entries = rawLogs.split(";").filter { it.isNotBlank() }
            entries.mapIndexed { index, entry ->
                val parts = entry.split("|")
                val time = parts.getOrNull(0) ?: "--:--"
                val rawApp = parts.getOrNull(1) ?: "Instagram"
                val app = if (
                    rawApp.contains("Instagram", ignoreCase = true) ||
                    rawApp.contains("Gönderiler", ignoreCase = true)
                ) {
                    "Instagram"
                } else {
                    rawApp
                }
                val appKey = when {
                    app.contains("TikTok", ignoreCase = true) -> "tiktok"
                    app.contains("YouTube", ignoreCase = true) -> "youtube"
                    else -> "instagram"
                }
                val parsedCount = parts.getOrNull(2)?.toIntOrNull()
                val appCount = if (parsedCount != null && parsedCount > 0) {
                    parsedCount
                } else {
                    val currentDailyApp = when (appKey) {
                        "tiktok" -> prefs.getInt("blocks_${todayStr}_tiktok", 0)
                        "youtube" -> prefs.getInt("blocks_${todayStr}_youtube", 0)
                        else -> prefs.getInt("blocks_${todayStr}_instagram", 0)
                    }
                    val occurrencesAfter = entries.take(index).count { e ->
                        val a = e.split("|").getOrNull(1) ?: ""
                        when (appKey) {
                            "tiktok" -> a.contains("TikTok", ignoreCase = true)
                            "youtube" -> a.contains("YouTube", ignoreCase = true)
                            else -> !a.contains("TikTok", ignoreCase = true) && !a.contains("YouTube", ignoreCase = true)
                        }
                    }
                    (currentDailyApp - occurrencesAfter).coerceAtLeast(1)
                }
                Triple(time, app, appCount)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
                text = if (isEn) "RECENT BLOCKS" else "SON ENGELLEMELER",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (logList.isEmpty()) {
            Text(
                text = if (isEn) "No blocks have been recorded yet." else "Henüz bir engelleme kaydedilmedi.",
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                val displayList = logList.take(4)
                displayList.forEachIndexed { index, (time, app, _) ->
                    val drawableRes = when {
                        app.contains("TikTok", ignoreCase = true) -> R.drawable.ic_tiktok
                        app.contains("YouTube", ignoreCase = true) -> R.drawable.ic_youtube
                        else -> R.drawable.ic_instagram
                    }
                    val accentColor = when {
                        app.contains("TikTok", ignoreCase = true) -> Color(0xFF00F2FE)
                        app.contains("YouTube", ignoreCase = true) -> Color(0xFFFF0055)
                        else -> Color(0xFFE1306C)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(28.dp)
                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = drawableRes),
                                    contentDescription = app,
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = app,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isEn) "At $time" else "Saat $time",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }

                    if (index < displayList.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(Color.White.copy(alpha = 0.07f))
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 2. SEKME: MODLAR & HEDEF UYGULAMALAR
// ------------------------------------------
@Composable
fun ModesAndAppsScreen(
    prefs: android.content.SharedPreferences,
    @Suppress("UNUSED_PARAMETER") onRequestAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val isEn = getAppLanguage(prefs) == "en"
    val isProtectionRunning = rememberAccessibilityStatus()
    var isInstagramEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.INSTAGRAM)) }
    var isTiktokEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.TIKTOK)) }
    var isYoutubeEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.YOUTUBE)) }
    var pendingDisableApp by remember { mutableStateOf<String?>(null) }
    var showPhilosophyDialog by remember { mutableStateOf(false) }
    var activeAppInfoDialog by remember { mutableStateOf<String?>(null) }

    var blocksInstagram by remember { mutableIntStateOf(prefs.getInt("blocks_instagram", 0)) }
    var blocksTiktok by remember { mutableIntStateOf(prefs.getInt("blocks_tiktok", 0)) }
    var blocksYoutube by remember { mutableIntStateOf(prefs.getInt("blocks_youtube", 0)) }

    LaunchedEffect(Unit) {
        while(true) {
            blocksInstagram = prefs.getInt("blocks_instagram", 0)
            blocksTiktok = prefs.getInt("blocks_tiktok", 0)
            blocksYoutube = prefs.getInt("blocks_youtube", 0)
            kotlinx.coroutines.delay(2000)
        }
    }

    when (activeAppInfoDialog) {
        "instagram" -> {
            ScrollableTextDialog(
                isEn = isEn,
                title = if (isEn) "Instagram Protection Scope (Beta)" else "Instagram Koruma Kapsamı (Beta)",
                content = if (isEn)
                    "Covers Home and Explore, and closes Reels, posts and comments. Stories, Direct Messages, profile information and Search remain available. Media grids in profiles and message details may also be covered. Instagram UI/accessibility changes can cause missed detections or false positives."
                else
                    "Ana Sayfa ve Keşfet içeriklerini perdeyle kapatır; Reels, gönderi ve yorum ekranları açılırsa otomatik olarak geri döner. Hikâyeler, mesajlar, profil bilgileri ve Arama kullanılabilir. Profil ve mesaj ayrıntılarındaki medya ızgaraları da kapatılabilir. Instagram arayüzü değişirse bazı ekranlar geç veya yanlış algılanabilir.",
                iconRes = R.drawable.ic_instagram,
                iconTint = Color(0xFFE1306C),
                onDismiss = { activeAppInfoDialog = null }
            )
        }
        "tiktok" -> {
            ScrollableTextDialog(
                isEn = isEn,
                title = if (isEn) "TikTok Protection Scope" else "TikTok Koruma Kapsamı",
                content = if (isEn)
                    "Browsing TikTok is allowed; the first vertical swipe in the For You or Friends feed takes you back. Switching to the Following or Community top tabs is undone automatically. Inbox, Direct Messages, profile, search and comments stay usable. TikTok UI/accessibility changes can cause missed detections or false positives."
                else
                    "TikTok'u açmak ve gezmek serbesttir; Sana Özel veya Arkadaşlar akışındaki ilk dikey kaydırmada geri dönülür. Takipte veya Topluluk sekmesine geçiş otomatik olarak geri alınır. Gelen Kutusu, mesajlar, profil, arama ve yorumlar kullanılabilir. TikTok arayüzü değişirse bazı hareketler geç veya yanlış algılanabilir.",
                iconRes = R.drawable.ic_tiktok,
                iconTint = Color(0xFF00F2FE),
                onDismiss = { activeAppInfoDialog = null }
            )
        }
        "youtube" -> {
            ScrollableTextDialog(
                isEn = isEn,
                title = if (isEn) "YouTube Shorts Protection Scope" else "YouTube Shorts Koruma Kapsamı",
                content = if (isEn)
                    "Shorts can be opened and watched to the end; the first swipe toward the next video closes the viewer. Normal videos, Search and comments stay usable. YouTube UI/accessibility changes can cause missed detections or false positives."
                else
                    "Shorts açılıp sonuna kadar izlenebilir; sonraki videoya ilk kaydırmada görüntüleyici kapatılır. Normal videolar, Arama ve yorumlar kullanılabilir. YouTube arayüzü değişirse bazı ekranlar geç veya yanlış algılanabilir.",
                iconRes = R.drawable.ic_youtube,
                iconTint = Color(0xFFFF0055),
                onDismiss = { activeAppInfoDialog = null }
            )
        }
    }

    if (showPhilosophyDialog) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "How protection works" else "Koruma nasıl çalışır?",
            content = if (isEn)
                "AwayDoomscrollin' intervenes when it recognizes a supported short-content area. Instagram Home and Explore are covered with a curtain; Reels and protected post views are closed. TikTok feed scrolling and the YouTube Shorts chain are interrupted directly, while single videos stay watchable. Platform, Android and manufacturer changes can affect how reliably protection detects protected screens."
            else
                "AwayDoomscrollin', desteklediği kısa içerik ekranlarını algıladığında korumayı uygular. Instagram Ana Sayfa ve Keşfet içerikleri perdeyle kapatılır; Reels ve korunan gönderiler açılırsa otomatik olarak geri dönülür. TikTok'ta ilk akış kaydırması durdurulur, Shorts'ta sonraki videoya ilk kaydırmada geri dönülür; tekil videolar izlenebilir. Uygulama veya telefon arayüzü değişirse bazı ekranlar geç ya da yanlış algılanabilir.",
            iconRes = R.drawable.ic_shield,
            iconTint = Color(0xFFFFB703),
            onDismiss = { showPhilosophyDialog = false }
        )
    }

    if (pendingDisableApp != null) {
        val pendingAppName = when (pendingDisableApp) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "youtube" -> "YouTube Shorts"
            else -> if (isEn) "This app" else "Bu uygulama"
        }
        AlertDialog(
            onDismissRequest = { pendingDisableApp = null },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning_triangle),
                        contentDescription = null,
                        tint = Color(0xFFFF0055),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Turn off protection?" else "Koruma kapatılsın mı?",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            },
            text = { 
                Text(
                    if (isEn) 
                        "$pendingAppName short-content protection will be disabled. Your current daily streak will reset to 0." 
                    else 
                        "$pendingAppName kısa içerik koruması kapanacak. Mevcut günlük seriniz 0'a sıfırlanacak.", 
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        val disabled = when (pendingDisableApp) {
                            "instagram" -> {
                                ProtectionPreferences.setEnabled(
                                    prefs,
                                    ProtectedApp.INSTAGRAM,
                                    enabled = false,
                                    resetStreak = true
                                ).also { if (it) isInstagramEnabled = false }
                            }
                            "tiktok" -> {
                                ProtectionPreferences.setEnabled(
                                    prefs,
                                    ProtectedApp.TIKTOK,
                                    enabled = false,
                                    resetStreak = true
                                ).also { if (it) isTiktokEnabled = false }
                            }
                            "youtube" -> {
                                ProtectionPreferences.setEnabled(
                                    prefs,
                                    ProtectedApp.YOUTUBE,
                                    enabled = false,
                                    resetStreak = true
                                ).also { if (it) isYoutubeEnabled = false }
                            }
                            else -> false
                        }

                        if (disabled) {
                            NotificationHelper.showShieldStatusNotification(
                                context,
                                if (isEn) "$pendingAppName protection is off" else "$pendingAppName koruması kapalı",
                                if (isEn) "Short-content protection is disabled and your daily streak was reset." else "Kısa içerik koruması kapatıldı ve günlük seriniz sıfırlandı.",
                                1004
                            )
                        } else {
                            android.util.Log.e("ProtectionPreferences", "$pendingAppName koruma tercihi kaydedilemedi.")
                        }

                        pendingDisableApp = null
                    }
                ) {
                    Text(if (isEn) "Turn off" else "Kapat", color = Color(0xFFFF0055), fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { pendingDisableApp = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color(0xFF070A12))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_check),
                            contentDescription = null,
                            tint = Color(0xFF070A12),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEn) "Keep protection on" else "Açık kalsın", fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            containerColor = Color(0xFF0F1523),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        DashboardSectionHeader(
            title = if (isEn) "Protected Apps" else "Korunan Uygulamalar",
            subtitle = if (isEn) {
                "Choose apps you want to protect"
            } else {
                "Korumak istediğiniz uygulamaları seçin"
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. KATEGORİ: GÜVENLİK & FELSEFE
        Text(
            text = if (isEn) "HOW PROTECTION WORKS" else "KORUMA NASIL ÇALIŞIR?",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Refined Cyber Protocol Bar (Gold Accent)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF161204),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPhilosophyDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    tint = Color(0xFFFFB703),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "PROTECTION APPROACH" else "KORUMA YAKLAŞIMI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFB703),
                            letterSpacing = 0.4.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isEn) "Read" else "Oku",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB703)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_forward),
                                contentDescription = null,
                                tint = Color(0xFFFFB703),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEn) 
                            "Different apps use different protection methods." 
                        else 
                            "Her uygulama için uygun koruma yöntemi kullanılır.",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. KATEGORİ: HEDEF UYGULAMA KALKANLARI
        Text(
            text = if (isEn) "APPS" else "UYGULAMALAR",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Open Borderless Target Apps List
        BorderlessTargetAppsList(
            isEn = isEn,
            isProtectionRunning = isProtectionRunning,
            isInstagramEnabled = isInstagramEnabled,
            isTiktokEnabled = isTiktokEnabled,
            isYoutubeEnabled = isYoutubeEnabled,
            onInfoClick = { app -> activeAppInfoDialog = app },
            onToggleApp = { app, checked ->
                if (!checked) {
                    pendingDisableApp = app
                } else {
                    when (app) {
                        "instagram" -> {
                            if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.INSTAGRAM, true)) {
                                isInstagramEnabled = true
                            }
                        }
                        "tiktok" -> {
                            if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.TIKTOK, true)) {
                                isTiktokEnabled = true
                            }
                        }
                        "youtube" -> {
                            if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.YOUTUBE, true)) {
                                isYoutubeEnabled = true
                            }
                        }
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BorderlessTargetAppsList(
    isEn: Boolean,
    isProtectionRunning: Boolean,
    isInstagramEnabled: Boolean,
    isTiktokEnabled: Boolean,
    isYoutubeEnabled: Boolean,
    onInfoClick: (String) -> Unit,
    onToggleApp: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Item 1: Instagram
        BorderlessTargetAppRow(
            isEn = isEn,
            name = "Instagram",
            iconRes = R.drawable.ic_instagram,
            isChecked = isInstagramEnabled,
            isProtectionRunning = isProtectionRunning,
            brandColor = Color(0xFFE1306C),
            isBeta = true,
            onInfoClick = { onInfoClick("instagram") },
            onCheckedChange = { onToggleApp("instagram", it) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Item 2: TikTok
        BorderlessTargetAppRow(
            isEn = isEn,
            name = "TikTok",
            iconRes = R.drawable.ic_tiktok,
            isChecked = isTiktokEnabled,
            isProtectionRunning = isProtectionRunning,
            brandColor = Color(0xFF00F2FE),
            isBeta = true,
            onInfoClick = { onInfoClick("tiktok") },
            onCheckedChange = { onToggleApp("tiktok", it) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Item 3: YouTube Shorts
        BorderlessTargetAppRow(
            isEn = isEn,
            name = "YouTube Shorts",
            iconRes = R.drawable.ic_youtube,
            isChecked = isYoutubeEnabled,
            isProtectionRunning = isProtectionRunning,
            brandColor = Color(0xFFFF0000),
            isBeta = true,
            onInfoClick = { onInfoClick("youtube") },
            onCheckedChange = { onToggleApp("youtube", it) }
        )
    }
}

@Composable
fun BorderlessTargetAppRow(
    isEn: Boolean,
    name: String,
    iconRes: Int,
    isChecked: Boolean,
    isProtectionRunning: Boolean = false,
    brandColor: Color,
    isBeta: Boolean = false,
    onInfoClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    isSettingsOpen: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isChecked) brandColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = name,
                    tint = if (isChecked) brandColor else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Başlık + BETA Rozeti (Asla alt satıra bölünmez)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isBeta) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFB703).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFB703).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "BETA",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFB703),
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Durum + Göz Alıcı Belirgin Kapsam Butonu
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            !isChecked -> if (isEn) "Off" else "Kapalı"
                            else -> if (isEn) "On" else "Açık"
                        },
                        fontSize = 11.sp,
                        color = if (isChecked) brandColor else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        onClick = onInfoClick,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00F2FE).copy(alpha = 0.20f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE).copy(alpha = 0.85f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_info),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEn) "Scope" else "Kapsam",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00F2FE),
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                    if (onSettingsClick != null && isChecked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = onSettingsClick,
                            shape = RoundedCornerShape(8.dp),
                            color = brandColor.copy(alpha = 0.20f),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, brandColor.copy(alpha = 0.85f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isSettingsOpen) R.drawable.ic_arrow_down else R.drawable.ic_settings),
                                    contentDescription = null,
                                    tint = brandColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEn) "Tune" else "Ayarla",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = brandColor,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = if (isEn) "$name protection" else "$name koruması"
                stateDescription = when {
                    !isChecked -> if (isEn) "Off" else "Kapalı"
                    isProtectionRunning -> if (isEn) "On; protection is running" else "Açık; koruma çalışıyor"
                    else -> if (isEn) "On; Accessibility permission required" else "Açık; erişilebilirlik izni gerekli"
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = brandColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E2A40)
            )
        )
    }
}

@Composable
fun InstagramSubSettingsCard(
    isEn: Boolean,
    config: InstagramProtectionConfig,
    onConfigChange: (InstagramProtectionConfig) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1306C).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = null,
                    tint = Color(0xFFE1306C),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEn) "INSTAGRAM PROTECTION SCOPE" else "INSTAGRAM KORUMA KAPSAMI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE1306C),
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
            text = if (isEn) "Home behavior" else "Ana sayfa davranışı",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                val isFullMode = config.feedMode == InstagramProtectionConfig.FEED_MODE_FULL
                Surface(
                    onClick = {
                        onConfigChange(
                            InstagramProtectionConfig(
                                feedMode = InstagramProtectionConfig.FEED_MODE_FULL,
                                allowDmReels = false,
                                blockStories = false,
                                hideExploreFeed = true,
                                blockComments = true
                            )
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isFullMode) Color(0xFFE1306C).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isFullMode) Color(0xFFE1306C) else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isEn) "Full protection" else "Tam koruma",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isFullMode) Color.White else Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Hide feeds" else "Akışları gizle",
                            fontSize = 10.sp,
                            color = if (isFullMode) Color(0xFFE1306C) else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val isDirectlyDm = config.feedMode == InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM
                Surface(
                    onClick = {
                        onConfigChange(config.copy(feedMode = InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM))
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDirectlyDm) Color(0xFF00F2FE).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isDirectlyDm) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isEn) "Open DMs first" else "Önce mesajları aç",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDirectlyDm) Color.White else Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Go straight to DMs" else "Doğrudan DM'e git",
                            fontSize = 10.sp,
                            color = if (isDirectlyDm) Color(0xFF00F2FE) else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (config.feedMode == InstagramProtectionConfig.FEED_MODE_DIRECTLY_DM) {
                    if (isEn) "Opens Messages automatically when Instagram launches. Pressing back returns to the protected Home feed."
                    else "Instagram açıldığında doğrudan mesajlara gider. Geri döndüğünüzde ana sayfa korumalı kalır."
                } else {
                    if (isEn) "Home, Explore, posts, comments and blocked media opened from DMs are covered. Stories remain available."
                    else "Ana Sayfa ve Keşfet akışları gizlenir; gönderiler, yorumlar ve DM'den açılan korumalı içerikler kapatılır. Hikâyeler kullanılabilir."
                },
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 14.sp
            )

            if (config.feedMode == InstagramProtectionConfig.FEED_MODE_FULL) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE1306C).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1306C).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_check),
                            contentDescription = null,
                            tint = Color(0xFFE1306C),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEn) "FULL PROTECTION IS ON" else "TAM KORUMA AÇIK",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE1306C),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isEn)
                                    "• Home Feed & Explore: Covered\n• Posts & Comments: Blocked\n• Stories: Available\n• Reels Shared in DMs: Blocked"
                                else
                                    "• Ana Sayfa ve Keşfet: Kapatıldı\n• Gönderiler ve yorumlar: Engellendi\n• Hikâyeler: Açık\n• DM'de paylaşılan Reels: Engellendi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.height(10.dp))

                InstagramSubSwitchRow(
                    title = if (isEn) "Allow Reels sent in DMs" else "DM'de paylaşılan Reels videolarına izin ver",
                    subtitle = if (isEn) "Friends' shared reels can be watched inside DM" else "Sohbet içinde paylaşılan Reels videoları izlenebilir",
                    checked = config.allowDmReels,
                    onCheckedChange = { onConfigChange(config.copy(allowDmReels = it)) }
                )

                InstagramSubSwitchRow(
                    title = if (isEn) "Block Stories" else "Hikâyeleri engelle",
                    subtitle = if (isEn) "Covers the stories tray and exits the story viewer" else "Hikâye çubuğunu kapatır ve açılan hikâyeden geri döner",
                    checked = config.blockStories,
                    onCheckedChange = { onConfigChange(config.copy(blockStories = it)) }
                )

                InstagramSubSwitchRow(
                    title = if (isEn) "Hide Explore Feed" else "Keşfet akışını gizle",
                    subtitle = if (isEn) "The search bar remains available; the recommended-posts grid is covered." else "Arama açık kalır, öneri ızgarası kapatılır",
                    checked = config.hideExploreFeed,
                    onCheckedChange = { onConfigChange(config.copy(hideExploreFeed = it)) }
                )

                InstagramSubSwitchRow(
                    title = if (isEn) "Block Post Comments" else "Gönderi yorumlarını engelle",
                    subtitle = if (isEn) "Prevents doomscrolling through post comments" else "Gönderi yorumlarının kaydırılmasını engeller",
                    checked = config.blockComments,
                    onCheckedChange = { onConfigChange(config.copy(blockComments = it)) }
                )
            }
        }
    }
}

@Composable
fun InstagramSubSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f),
                lineHeight = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE1306C),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E2A40)
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun CyberTargetAppCard(
    isEn: Boolean = false,
    name: String,
    iconRes: Int,
    isChecked: Boolean,
    brandColor: Color,
    blockCount: Int = 0,
    isBeta: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    BorderlessTargetAppRow(
        isEn = isEn,
        name = name,
        iconRes = iconRes,
        isChecked = isChecked,
        brandColor = brandColor,
        isBeta = isBeta,
        onInfoClick = { onInfoClick?.invoke() },
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun TargetAppRow(
    name: String,
    iconRes: Int,
    isChecked: Boolean,
    isComingSoon: Boolean = false,
    isEn: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isComingSoon) 0.5f else 1.0f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
        }

        if (isComingSoon) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ) {
                    Text(
                        if (isEn) "SOON" else "YAKINDA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            )
        }
    }
}

data class DayBlockDetail(
    val dayName: String,
    val dateStr: String,
    val totalBlocks: Int,
    val instagramBlocks: Int,
    val tiktokBlocks: Int,
    val youtubeBlocks: Int,
    val instagramProtectionMs: Long
) {
    val categorizedBlocks: Int
        get() = instagramBlocks + tiktokBlocks + youtubeBlocks

    val uncategorizedBlocks: Int
        get() = (totalBlocks - categorizedBlocks).coerceAtLeast(0)

    val chartTotal: Int
        get() = maxOf(totalBlocks, categorizedBlocks)

    val effectiveInsta: Int
        get() = instagramBlocks

    val effectiveTiktok: Int
        get() = tiktokBlocks

    val effectiveYt: Int
        get() = youtubeBlocks

    val topAppColor: Color
        get() = when {
            effectiveInsta >= effectiveTiktok && effectiveInsta >= effectiveYt && effectiveInsta > 0 -> Color(0xFF833AB4) // Instagram Purple
            effectiveTiktok >= effectiveInsta && effectiveTiktok >= effectiveYt && effectiveTiktok > 0 -> Color(0xFF00F2FE) // TikTok Neon Cyan
            effectiveYt >= effectiveInsta && effectiveYt >= effectiveTiktok && effectiveYt > 0 -> Color(0xFFFF0000) // YouTube Shorts Bright Red
            chartTotal > 0 -> Color(0xFF64748B) // Legacy unattributed data
            else -> Color(0xFF1E2A40)
        }

}

// ------------------------------------------
// 4. SEKME: İLERLEME DURUMU & GERÇEK ZAMANLI İSTATİSTİKLER (ESKİ ROZETLER)
// ------------------------------------------
@Composable
fun ProgressStatusScreen(prefs: android.content.SharedPreferences) {
    val isEn = getAppLanguage(prefs) == "en"
    val initialToday = InstagramProtectionMetrics.dateString(System.currentTimeMillis())
    var totalBlocks by remember { mutableIntStateOf(prefs.getInt("total_blocks", 0)) }
    var todayInterventions by remember {
        mutableIntStateOf(
            prefs.getInt("blocks_$initialToday", 0)
        )
    }
    var todayInstagramBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_${initialToday}_instagram", 0))
    }
    var todayTiktokBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_${initialToday}_tiktok", 0))
    }
    var todayYoutubeBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_${initialToday}_youtube", 0))
    }
    var streakDays by remember { mutableIntStateOf(prefs.getInt("streak_days", 0)) }
    var instagramProtectionTodayMs by remember {
        mutableLongStateOf(InstagramProtectionMetrics.todayMs(prefs))
    }
    var instagramProtectionTotalMs by remember {
        mutableLongStateOf(InstagramProtectionMetrics.totalMs(prefs))
    }
    var allTimeInstagramBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_instagram", 0))
    }
    var allTimeTiktokBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_tiktok", 0))
    }
    var allTimeYoutubeBlocks by remember {
        mutableIntStateOf(prefs.getInt("blocks_youtube", 0))
    }
    var monthlyBlocks by remember { mutableIntStateOf(0) }
    var monthlyInstagramBlocks by remember { mutableIntStateOf(0) }
    var monthlyTiktokBlocks by remember { mutableIntStateOf(0) }
    var monthlyYoutubeBlocks by remember { mutableIntStateOf(0) }
    var monthlyInstagramMs by remember { mutableLongStateOf(0L) }
    var selectedTimeframeTab by remember { mutableIntStateOf(0) }
    var showJourneyDialog by remember { mutableStateOf(false) }
    var weeklyDetails by remember {
        mutableStateOf(List(7) { DayBlockDetail("", "", 0, 0, 0, 0, 0L) })
    }
    LaunchedEffect(Unit) {
        while(true) {
            totalBlocks = prefs.getInt("total_blocks", 0)
            val today = InstagramProtectionMetrics.dateString(System.currentTimeMillis())
            todayInterventions = prefs.getInt("blocks_$today", 0)
            todayInstagramBlocks = prefs.getInt("blocks_${today}_instagram", 0)
            todayTiktokBlocks = prefs.getInt("blocks_${today}_tiktok", 0)
            todayYoutubeBlocks = prefs.getInt("blocks_${today}_youtube", 0)
            streakDays = prefs.getInt("streak_days", 0)
            instagramProtectionTodayMs = InstagramProtectionMetrics.todayMs(prefs)
            instagramProtectionTotalMs = InstagramProtectionMetrics.totalMs(prefs)
            allTimeInstagramBlocks = prefs.getInt("blocks_instagram", 0)
            allTimeTiktokBlocks = prefs.getInt("blocks_tiktok", 0)
            allTimeYoutubeBlocks = prefs.getInt("blocks_youtube", 0)

            val calendar = java.util.Calendar.getInstance()
            calendar.firstDayOfWeek = java.util.Calendar.MONDAY
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            
            val dayShortNames = if (isEn) 
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") 
            else 
                listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
            
            val newWeeklyDetails = mutableListOf<DayBlockDetail>()
            for (i in 0 until 7) {
                val dayStr = format.format(calendar.time)
                val dayTotal = prefs.getInt("blocks_$dayStr", 0)
                val rawInsta = prefs.getInt("blocks_${dayStr}_instagram", 0)
                val rawTiktok = prefs.getInt("blocks_${dayStr}_tiktok", 0)
                val rawYt = prefs.getInt("blocks_${dayStr}_youtube", 0)

                newWeeklyDetails.add(
                    DayBlockDetail(
                        dayName = dayShortNames[i],
                        dateStr = dayStr,
                        totalBlocks = dayTotal,
                        instagramBlocks = rawInsta,
                        tiktokBlocks = rawTiktok,
                        youtubeBlocks = rawYt,
                        instagramProtectionMs = InstagramProtectionMetrics.dayMs(prefs, dayStr)
                    )
                )
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            weeklyDetails = newWeeklyDetails

            // Son 30 gün (Aylık) hesaplama
            val monthCal = java.util.Calendar.getInstance()
            var mTotal = 0
            var mInsta = 0
            var mTiktok = 0
            var mYt = 0
            var mInstaTime = 0L
            for (i in 0 until 30) {
                val dStr = format.format(monthCal.time)
                val dayInsta = prefs.getInt("blocks_${dStr}_instagram", 0)
                val dayTiktok = prefs.getInt("blocks_${dStr}_tiktok", 0)
                val dayYt = prefs.getInt("blocks_${dStr}_youtube", 0)
                val dayTotal = prefs.getInt("blocks_$dStr", 0)
                val effectiveTotal = maxOf(dayTotal, dayInsta + dayTiktok + dayYt)

                mTotal += effectiveTotal
                mInsta += dayInsta
                mTiktok += dayTiktok
                mYt += dayYt
                mInstaTime += if (i == 0) {
                    instagramProtectionTodayMs
                } else {
                    InstagramProtectionMetrics.dayMs(prefs, dStr)
                }
                monthCal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            }
            monthlyBlocks = mTotal
            monthlyInstagramBlocks = mInsta
            monthlyTiktokBlocks = mTiktok
            monthlyYoutubeBlocks = mYt
            monthlyInstagramMs = mInstaTime

            val storedTotalBlocks = prefs.getInt("total_blocks", 0)
            val storedInstaBlocks = prefs.getInt("blocks_instagram", 0)
            val storedTiktokBlocks = prefs.getInt("blocks_tiktok", 0)
            val storedYoutubeBlocks = prefs.getInt("blocks_youtube", 0)
            val storedInstaTotalMs = InstagramProtectionMetrics.totalMs(prefs)

            allTimeInstagramBlocks = maxOf(storedInstaBlocks, mInsta)
            allTimeTiktokBlocks = maxOf(storedTiktokBlocks, mTiktok)
            allTimeYoutubeBlocks = maxOf(storedYoutubeBlocks, mYt)
            totalBlocks = maxOf(
                storedTotalBlocks,
                allTimeInstagramBlocks + allTimeTiktokBlocks + allTimeYoutubeBlocks,
                mTotal
            )
            instagramProtectionTotalMs = maxOf(storedInstaTotalMs, mInstaTime)

            delay(1000)
        }
    }

    val instagramProtectionToday = formatMeasuredDuration(instagramProtectionTodayMs, isEn)

    if (showJourneyDialog) {
        FocusJourneyDialog(
            prefs = prefs,
            isEn = isEn,
            onDismiss = { showJourneyDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        DashboardSectionHeader(
            title = if (isEn) "Protection Analytics" else "Koruma Analizi",
            subtitle = if (isEn) {
                "Blocks, streaks and achievements"
            } else {
                "Engellemeler, seri ve başarımlar"
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. KATEGORİ: ODAK & ENGELLEME GENEL BAKIŞI
        Text(
            text = if (isEn) "TODAY" else "BUGÜN",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bugünün uygulama bazlı özeti
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield),
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) (if (todayInterventions == 1) "1 block" else "$todayInterventions blocks") else "$todayInterventions engelleme",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) "Across all protected apps" else "Tüm korunan uygulamalarda",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    Surface(
                        onClick = { showJourneyDialog = true },
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFFFFB703).copy(alpha = 0.13f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tier_fire),
                                contentDescription = null,
                                tint = Color(0xFFFFB703),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEn) "$streakDays-day streak" else "$streakDays günlük seri",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB703)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isEn) "BY APP" else "UYGULAMALARA GÖRE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_instagram,
                        label = "Instagram",
                        count = todayInstagramBlocks,
                        color = Color(0xFFE1306C)
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_tiktok,
                        label = "TikTok",
                        count = todayTiktokBlocks,
                        color = Color(0xFF00F2FE)
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_youtube,
                        label = "YouTube",
                        count = todayYoutubeBlocks,
                        color = Color(0xFFFF0000)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070A12), RoundedCornerShape(11.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_instagram),
                        contentDescription = null,
                        tint = Color(0xFFE1306C),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Instagram protection time" else "Instagram koruma süresi",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) "Measured only for Instagram" else "Yalnızca Instagram için ölçülür",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.48f)
                        )
                    }
                    Text(
                        text = instagramProtectionToday,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE1306C)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            onClick = { showJourneyDialog = true },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB703).copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trophy),
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isEn) "Achievements and streak" else "Başarımlar ve seri",
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEn) "View your progress" else "İlerlemenizi görüntüleyin",
                        fontSize = 10.5.sp,
                        lineHeight = 13.5.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB703).copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        CyberWeeklyPillarsChart(isEn = isEn, weeklyDetails = weeklyDetails)

        Spacer(modifier = Modifier.height(18.dp))

        // 3. KATEGORİ: AYLIK & TÜM ZAMANLAR İSTATİSTİKLERİ
        Text(
            text = if (isEn) "MONTHLY & ALL TIME" else "AYLIK VE TÜM ZAMANLAR",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Sekme Seçici (Tab Selector)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070A12), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = if (isEn) listOf("Last 30 Days", "All Time") else listOf("Son 30 Gün", "Tüm Zamanlar")
                    tabs.forEachIndexed { index, label ->
                        val isSelected = selectedTimeframeTab == index
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) Color(0xFF161E30) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.35f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTimeframeTab = index }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF00FF87) else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val currentBlocksCount = if (selectedTimeframeTab == 0) monthlyBlocks else totalBlocks
                val currentInstaBlocks = if (selectedTimeframeTab == 0) monthlyInstagramBlocks else allTimeInstagramBlocks
                val currentTiktokBlocks = if (selectedTimeframeTab == 0) monthlyTiktokBlocks else allTimeTiktokBlocks
                val currentYoutubeBlocks = if (selectedTimeframeTab == 0) monthlyYoutubeBlocks else allTimeYoutubeBlocks
                val currentInstaMs = if (selectedTimeframeTab == 0) monthlyInstagramMs else instagramProtectionTotalMs

                // Ana İstatistik Başlığı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield),
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) (if (currentBlocksCount == 1) "1 block" else "$currentBlocksCount blocks") else "$currentBlocksCount engelleme",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTimeframeTab == 0) {
                                if (isEn) "Total blocks in the last 30 days" else "Son 30 günde toplam engelleme"
                            } else {
                                if (isEn) "Total blocks across all apps" else "Tüm korunan uygulamalarda toplam"
                            },
                            fontSize = 10.5.sp,
                            lineHeight = 13.5.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Uygulama Bazlı Dağılım Başlığı
                Text(
                    text = if (isEn) "BY APP" else "UYGULAMALARA GÖRE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_instagram,
                        label = "Instagram",
                        count = currentInstaBlocks,
                        color = Color(0xFFE1306C)
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_tiktok,
                        label = "TikTok",
                        count = currentTiktokBlocks,
                        color = Color(0xFF00F2FE)
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_youtube,
                        label = "YouTube",
                        count = currentYoutubeBlocks,
                        color = Color(0xFFFF0000)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Instagram Koruma Süresi Kartı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070A12), RoundedCornerShape(11.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_instagram),
                        contentDescription = null,
                        tint = Color(0xFFE1306C),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 1.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (selectedTimeframeTab == 0) {
                                if (isEn) "30-day Instagram protection" else "30 günlük Instagram koruması"
                            } else {
                                if (isEn) "All-time Instagram protection" else "Tüm zamanlar Instagram koruması"
                            },
                            fontSize = 10.5.sp,
                            lineHeight = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (selectedTimeframeTab == 0) {
                                if (isEn) "Curtain duration in last 30 days" else "Son 30 gündeki perde süresi"
                            } else {
                                if (isEn) "Total curtain duration since install" else "Tüm günlerdeki perde süresi"
                            },
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            color = Color.White.copy(alpha = 0.52f)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatMeasuredDuration(currentInstaMs, isEn),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE1306C)
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun TodayAppMetric(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    count: Int,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFF070A12),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CyberWeeklyPillarsChart(isEn: Boolean = false, weeklyDetails: List<DayBlockDetail>) {
    val maxVal = (weeklyDetails.maxOfOrNull { it.chartTotal } ?: 1).coerceAtLeast(1)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showChartInfo by remember { mutableStateOf(false) }

    if (showChartInfo) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "How the weekly chart works" else "Haftalık grafik nasıl çalışır?",
            content = if (isEn) {
                "Each bar shows that day's Instagram, TikTok and YouTube blocks in separate colors. Tap a day to see its breakdown. Protection time is measured only for Instagram because TikTok and YouTube use direct blocking instead of a curtain."
            } else {
                "Her sütun, o günkü Instagram, TikTok ve YouTube engellemelerini ayrı renklerde gösterir. Ayrıntıları görmek için bir güne dokunun. Koruma süresi yalnızca Instagram için ölçülür; TikTok ve YouTube doğrudan engelleme yöntemini kullanır."
            },
            iconRes = R.drawable.ic_info,
            iconTint = Color(0xFF00F2FE),
            onDismiss = { showChartInfo = false }
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEn) "This week" else "Bu hafta",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(
                    onClick = { showChartInfo = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = if (isEn) "How the chart works" else "Grafik açıklaması",
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Renk Açıklama Rozetleri (Minimalist Dot Legend)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Instagram
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFFE1306C), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Instagram", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
                }

                // TikTok
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFF00F2FE), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("TikTok", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
                }

                // YouTube
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFFFF0000), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("YouTube", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Medium)
                }

                if (weeklyDetails.any { it.uncategorizedBlocks > 0 }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).background(Color(0xFF64748B), CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            if (isEn) "Older records" else "Eski kayıtlar",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7 Kule Yan Yana (Tam Hizanlama & Simetri)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyDetails.forEachIndexed { index, detail ->
                    val total = detail.chartTotal
                    val fraction = total.toFloat() / maxVal
                    val isSelected = selectedIndex == index
                    val maxPillarHeightDp = 100.dp
                    val currentPillarHeightDp = (maxPillarHeightDp * fraction).coerceAtLeast(6.dp)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedIndex = if (isSelected) null else index }
                            .padding(vertical = 2.dp)
                    ) {
                        // 1. ÜST SLOT: Sayı
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (total > 0) "$total" else "0",
                                fontSize = 11.sp,
                                fontWeight = if (total > 0) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (total > 0) detail.topAppColor else Color.White.copy(alpha = 0.25f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 2. ORTA SLOT: Çubuk Kanvası
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 22.dp else 16.dp)
                                    .height(if (total > 0) currentPillarHeightDp else 6.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(if (isSelected) Color(0xFF2A3B5C) else Color(0xFF162032))
                            ) {
                                if (total > 0) {
                                    val instaCount = detail.effectiveInsta
                                    val tiktokCount = detail.effectiveTiktok
                                    val ytCount = detail.effectiveYt
                                    val uncategorizedCount = detail.uncategorizedBlocks

                                    val instaRatio = instaCount.toFloat() / total
                                    val tiktokRatio = tiktokCount.toFloat() / total
                                    val ytRatio = ytCount.toFloat() / total
                                    val uncategorizedRatio = uncategorizedCount.toFloat() / total

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Üst - YouTube Shorts (Kırmızı)
                                        if (ytRatio > 0f) {
                                             Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(ytRatio)
                                                    .background(Color(0xFFFF0000))
                                            )
                                        }
                                        // Orta - TikTok (Turkuaz)
                                        if (tiktokRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(tiktokRatio)
                                                    .background(Color(0xFF00F2FE))
                                            )
                                        }
                                        // Alt - Instagram (Pembe/Mor)
                                        if (instaRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(instaRatio)
                                                    .background(Color(0xFFE1306C))
                                            )
                                        }
                                        if (uncategorizedRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(uncategorizedRatio)
                                                    .background(Color(0xFF64748B))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. ALT SLOT: Gün Adı
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = detail.dayName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Etkileşimli Günlük Oran Dökümü Kartı
            if (selectedIndex != null) {
                val selectedDetail = weeklyDetails.getOrNull(selectedIndex!!)
                if (selectedDetail != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131A29),
                        border = androidx.compose.foundation.BorderStroke(1.dp, selectedDetail.topAppColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // 1. SATIR: Gün Başlığı + Rozetler
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_calendar),
                                        contentDescription = null,
                                        tint = selectedDetail.topAppColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isEn) "${selectedDetail.dayName} summary" else "${selectedDetail.dayName} özeti",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = selectedDetail.topAppColor
                                    )
                                }

                                val dayProtectionTime = formatMeasuredDuration(
                                    selectedDetail.instagramProtectionMs,
                                    isEn
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF00FF87).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_instagram),
                                            contentDescription = null,
                                            tint = Color(0xFF00FF87),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isEn) "Instagram: $dayProtectionTime" else "Instagram: $dayProtectionTime",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00FF87)
                                        )
                                    }
                                }
                            }

                            if (selectedDetail.chartTotal > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val total = selectedDetail.chartTotal
                                    val insta = selectedDetail.effectiveInsta
                                    val tiktok = selectedDetail.effectiveTiktok
                                    val yt = selectedDetail.effectiveYt
                                    val uncategorized = selectedDetail.uncategorizedBlocks

                                    if (insta > 0) {
                                        val pct = (insta * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(95.dp)) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_instagram),
                                                    contentDescription = null,
                                                    tint = Color(0xFFE1306C),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Instagram:", fontSize = 11.5.sp, color = Color(0xFFE1306C), fontWeight = FontWeight.Bold)
                                            }
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0F1523))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFFE1306C)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$insta ($pct%)" else "$insta (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (tiktok > 0) {
                                        val pct = (tiktok * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(95.dp)) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_tiktok),
                                                    contentDescription = null,
                                                    tint = Color(0xFF00F2FE),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("TikTok:", fontSize = 11.5.sp, color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold)
                                            }
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0F1523))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFF00F2FE)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$tiktok ($pct%)" else "$tiktok (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (yt > 0) {
                                        val pct = (yt * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(95.dp)) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_youtube),
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF0000),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("YouTube:", fontSize = 11.5.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.Bold)
                                            }
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0F1523))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFFFF0000)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$yt ($pct%)" else "$yt (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (uncategorized > 0) {
                                        val pct = (uncategorized * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(95.dp)) {
                                                Box(modifier = Modifier.size(13.dp).background(Color(0xFF64748B), CircleShape))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    if (isEn) "Older:" else "Eski kayıt:",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0F1523))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFF64748B)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$uncategorized ($pct%)" else "$uncategorized (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isEn) "Nothing was blocked on this day." else "Seçilen gün için kaydedilmiş engelleme yok.",
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
