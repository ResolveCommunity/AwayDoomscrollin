package com.awaydoomscrollin.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.VideoView
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import kotlin.math.ceil
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Remote rules are automatic. Telemetry is sent only after explicit opt-in.
        RemoteRuleManager.fetchRulesAsync(this)
        if (TelemetryManager.isTelemetryEnabled(this)) {
            TelemetryManager.sendTelemetryAsync(this)
        }

        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val initialOnboardingDone = prefs.getBoolean("onboarding_completed", false)

        setContent {
            ZenTheme {
                var isOnboardingCompleted by remember { mutableStateOf(initialOnboardingDone) }

                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        prefs = prefs,
                        onComplete = {
                            prefs.edit().putBoolean("onboarding_completed", true).apply()
                            isOnboardingCompleted = true
                        }
                    )
                } else {
                    MainNavigationDashboard(
                        prefs = prefs,
                        onReopenOnboarding = {
                            isOnboardingCompleted = false
                        }
                    )
                }
            }
        }
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
fun rememberPermissionStatus(): Pair<Boolean, Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityActive by remember {
        mutableStateOf<Boolean>(isAccessibilityServiceEnabled(context, AntiScrollService::class.java))
    }
    var isIgnoringBattery by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf<Boolean>(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = isAccessibilityServiceEnabled(context, AntiScrollService::class.java)
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return Pair(isAccessibilityActive, isIgnoringBattery)
}

// ==========================================
// İNTERAKTİF TANITIM VE İZİN KURULUM EKRANI
// ==========================================
@Composable
fun OnboardingScreen(
    prefs: android.content.SharedPreferences,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val (isAccessibilityActive, isIgnoringBattery) = rememberPermissionStatus()
    val isDark = isSystemInDarkTheme()
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
                            isIgnoringBattery = isIgnoringBattery,
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
                        contentColor = if (isDark) Color(0xFF0D1117) else Color.White
                    )
                ) {
                    Text(
                        text = if (step == 5) 
                            (if (isEn) "Finish & Start" else "Kurulumu Tamamla") 
                        else if (step == 1) 
                            (if (isEn) "Start Setup →" else "Kuruluma Başla →") 
                        else 
                            (if (isEn) "Next →" else "Devam Et →"),
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
                            text = if (isEn) "Smart Shield Against Dopamine Loops" else "Dopamin Tuzağına Karşı Akıllı Koruma",
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
                                text = if (isEn) "Other Apps: The '20-Minute Timer' Trap" else "Diğer Uygulamalar: 20 Dakika Süre Yanılgısı",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF0055)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEn)
                                    "Traditional apps set soft '20-minute daily limits'. You burn through every second, suffer sudden dopamine withdrawal when it ends, and repeatedly tap 'Snooze 5 more mins'. The addiction loop never breaks."
                                else
                                    "Klasik uygulamalar günde 20 dakika gibi süre sınırları koyar. Bu süreyi son saniyesine kadar harcar, süre bitince '5 dakika daha' diyerek sürekli ertelersiniz. Kaydırma döngüsü hiçbir zaman kırılmaz.",
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
                                text = if (isEn) "AwayDoomscrollin': Focus Protection Without Isolation" else "AwayDoomscrollin': İletişimi Kesmeyen Akıllı Koruma",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF87)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEn)
                                    "No whole-app blocking! Your DMs, search, and communication stay 100% open. We automatically intercept ONLY infinite Reels, Shorts, and Feed scrolling the moment you enter the trap."
                                else
                                    "Uygulamayı tamamen kilitlemez; mesajlarınız (DM), arama ve profiller açık kalır. Sadece zamanınızı çalan sonsuz Reels, Shorts ve Keşfet videolarına girdiğiniz anda araya girip videoyu kapatır.",
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
                                        text = if (isEn) "DMs Allowed" else "Sohbet Serbest",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00F2FE),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "Chat freely. Only endless video feeds are blocked." else "İletişiminiz kesilmez. Yalnızca video akışları engellenir.",
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
                                        text = if (isEn) "100% Local" else "%100 Yerel",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "All detection runs locally on your device CPU." else "Ekran analizleri sadece telefonunuzda işlenir.",
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
                                    text = if (isEn) "Fully transparent code, zero hidden backdoors." else "GitHub'da şeffaf kod, sıfır gizli arka kapı.",
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
                                        text = if (isEn) "Zero Ads" else "Sıfır Reklam",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00F2FE),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = if (isEn) "Zero tracking, zero ads, zero subscriptions." else "Sıfır reklam, sıfır ticari takip ve tuzak.",
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
                            text = if (isEn) "A Letter from Resolve Community" else "Resolve Community'den Bir Mektup",
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
                        "Ever since the COVID-19 pandemic, almost my entire day was spent in front of computer and phone screens. Life outside was complicated; playing games or scrolling through feeds for hours felt more enjoyable and safe. The real reason was escaping reality."
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
                                text = if (isEn) "Painful Truth I Faced:" else "Yüzleştiğim Acı Gerçek:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEn) 
                                "I realized how negatively screen addiction impacted my weight, sleep schedule, eyesight, and behavior.\n\nIs it too late for someone aiming for university or life goals? Absolutely not!"
                            else 
                                "Bu ekran bağımlılığının kilomu, uyku düzenimi, göz sağlığımı ve davranışlarımı ne kadar olumsuz etkilediğini fark ettim.\n\nÜniversite veya hayat hedefleri olan biri için bu farkındalık geç mi kaldı? Kesinlikle hayır!",
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
                    text = if (isEn) "Why Did I Build This App Under Resolve Community?" else "Bu Uygulamayı Neden Geliştirdim?",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn) 
                        "Even when playing PC games, my eyes would wander to my phone screen, watching Reels and jumping around. I built AwayDoomscrollin' under Resolve Community to solve this addiction. I use it actively myself and can confidently say it's much more effective than other well-being apps."
                    else 
                        "Bilgisayarda oyun oynarken bile gözüm telefona kayıyor, bir yandan Reels izleyip oradan oraya hopluyordum. Bu bağımlılığı çözmek için Resolve Community çatısı altında AwayDoomscrollin'ı geliştirdim. Şu an kendim de aktif kullanıyorum ve diğer tüm well-being uygulamalarından çok daha etkili olduğunu rahatlıkla söyleyebilirim.",
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
                                text = if (isEn) "How the Algorithm Trap Works" else "Algoritmanın Tuzağı Nasıl Çalışır?",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF85149)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isEn) 
                                "• Instagram & TikTok's sole purpose is to lock you onto the screen.\n" +
                                "• They analyze how many seconds you watch a video and serve similar content that delivers instant dopamine.\n" +
                                "• The reason we get bored of long videos now is these short, fake dopamine hits.\n" +
                                "• If you've ever gotten trapped in endless scrolling, you know exactly what I mean."
                            else 
                                "• Instagram & TikTok'un tek amacı sizi ekrana kilitlemektir.\n" +
                                "• Hangi videoyu kaç saniye izlediğinizi analiz edip, beyninize en hızlı dopamini verecek benzer içerikleri sunarlar.\n" +
                                "• Artık uzun videolardan canımızın sıkılmasının tek sebebi, bu kısa ve sahte dopamin patlamalarıdır.\n" +
                                "• Eğer durmadan kaydırıp 'kilitlenme' sorunu yaşadıysanız, beni çok iyi anlıyorsunuz demektir.",
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
                        "My sole purpose is to study productively and make quality time for myself. Once you break free from scrolling addiction, you'll see how fruitful life becomes.\n\nNever stop trying even if you fail sometimes! (I failed many times too, but this is my 2nd attempt at not giving up, and this time we'll succeed together...)"
                    else 
                        "Tek amacım verimli ders çalışabilmek ve kendime kaliteli zaman ayırmak. Ekran ve kaydırma bağımlılığından bir kez kurtulduğunuzda, hayatınızın ne kadar verimli geçtiğini göreceksiniz.\n\nHer ne kadar bazen başarısız olsanız da çabalamaktan asla vazgeçmeyin! (Ben de defalarca başarısız oldum, ancak bu benim 2. pes etmeyişim ve bu sefer birlikte başaracağız...)",
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
        Text(
            text = if (isEn) "How Does the Shield Work?" else "Kalkan Nasıl Çalışır?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = if (isEn) "Beta Detection Shield\nfor Short-Video Feeds" else "Kısa Video Akışları İçin\nBeta Algılama Kalkanı",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        ReelsToHomeSettingsPreview(isEn = isEn)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Current Beta Behavior:" else "Mevcut Beta Davranışı:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn)
                        "• The beta engine attempts to recognize supported short-video feeds from accessibility events.\n• When detection succeeds, the shield instantly returns you to the Home screen, resets the feed task, and sends a scroll alert notification.\n• Platform, Android, or manufacturer UI changes can cause missed detections or false positives."
                    else
                        "• Beta motoru desteklenen kısa video akışlarını erişilebilirlik olaylarından algılamaya çalışır.\n• Algılama başarılı olduğunda kalkan sizi anında Ana Ekrana döndürür, kaydırma görevini sıfırlar ve kaydırma uyarısı bildirimi gönderir.\n• Platform, Android veya üretici arayüzü değişiklikleri kaçırılan ya da hatalı algılamalara yol açabilir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun MutedVideoPlayer(
    resId: Int,
    onCompletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var loadedResId by remember { mutableIntStateOf(-1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    mp.setVolume(0f, 0f)
                }
                setOnCompletionListener {
                    onCompletion()
                }
            }
        },
        update = { view ->
            if (loadedResId != resId) {
                loadedResId = resId
                val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                view.setVideoURI(uri)
                view.start()
            }
        }
    )
}

@Composable
fun AnimatedSwipeGesture() {
    val infiniteTransition = rememberInfiniteTransition(label = "SwipeHandAnimation")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HandOffsetY"
    )
    val alphaVal by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HandAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = offsetY.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_swipe_gesture),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(alphaVal)
            )
        }
    }
}

enum class SimulatorState {
    HOME,
    BLOCKED_REELS,
    BLOCKED_SCROLL,
    SAFE_ZONE
}

@Composable
fun ReelsToHomeSettingsPreview(isEn: Boolean = false) {
    var simulatorState by remember { mutableStateOf(SimulatorState.HOME) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_gamepad),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isEn) "Interactive Simulator" else "İnteraktif Simülatör",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        
        Surface(
            modifier = Modifier
                .width(260.dp)
                .height(360.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF000000),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = simulatorState,
                    transitionSpec = { fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(350)) },
                    label = "SimulatorStateAnimation"
                ) { state ->
                    when (state) {
                        SimulatorState.HOME -> {
                            val infiniteTransition = rememberInfiniteTransition()
                            val handOffsetY by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = -15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "HandBounce"
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF000000))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, _ ->
                                            simulatorState = SimulatorState.BLOCKED_SCROLL
                                        }
                                    }
                            ) {
                                // Top bar
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Instagram", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_heart),
                                        contentDescription = null,
                                        tint = Color(0xFFFF0055),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { simulatorState = SimulatorState.SAFE_ZONE }
                                    )
                                }
                                
                                // Story Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    repeat(4) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .border(2.dp, Brush.linearGradient(listOf(Color(0xFFF58529), Color(0xFFDD2A7B))), CircleShape)
                                                .padding(4.dp)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray)
                                        )
                                    }
                                }
                                
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF262626)))
                                
                                // Feed Area (Posts)
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Post Header
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("away_doomscrollin", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    // Post Image/Video
                                    Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF161B22)), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_arrow_down),
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isEn) "Try scrolling down!" else "Akışı kaydırmayı dene!", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                                
                                // Bottom Bar (Ana Sayfa - Reels - DM - Arama - Profil)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(Color(0xFF000000)),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Ana Sayfa
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_nav_home),
                                        contentDescription = "Home",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { simulatorState = SimulatorState.HOME }
                                    )
                                    
                                    // 2. Reels (Reels tuzağı - Blok tetikleyici)
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_reels),
                                            contentDescription = "Reels",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable { simulatorState = SimulatorState.BLOCKED_REELS }
                                        )
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_swipe_gesture),
                                            contentDescription = null,
                                            tint = Color(0xFF00F2FE),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .offset(x = 12.dp, y = handOffsetY.dp)
                                                .padding(top = 28.dp)
                                        )
                                    }

                                    // 3. DM (Direkt Mesaj - Güvenli Bölge)
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_dm),
                                        contentDescription = "Direct Messages",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { simulatorState = SimulatorState.SAFE_ZONE }
                                    )
                                    
                                    // 4. Arama (Güvenli Bölge)
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_search),
                                        contentDescription = "Search",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { simulatorState = SimulatorState.SAFE_ZONE }
                                    )

                                    // 5. Profil (Güvenli Bölge)
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_user),
                                        contentDescription = "Profile",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { simulatorState = SimulatorState.SAFE_ZONE }
                                    )
                                }
                            }
                        }
                        SimulatorState.BLOCKED_REELS -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_slash_ban),
                                    contentDescription = null,
                                    tint = Color(0xFFF85149),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    if (isEn) "Reels Blocked!" else "Reels Engellendi!",
                                    color = Color(0xFFF85149),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isEn) "This simulator demonstrates the intended shield response. Real-app detection is beta and can vary."
                                    else "Bu simülatör hedeflenen kalkan davranışını gösterir. Gerçek uygulama algılaması betadır ve değişebilir.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { simulatorState = SimulatorState.HOME }) {
                                    Text(if (isEn) "Reset Simulation" else "Simülasyonu Sıfırla", fontSize = 10.sp)
                                }
                            }
                        }
                        SimulatorState.BLOCKED_SCROLL -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_slash_ban),
                                    contentDescription = null,
                                    tint = Color(0xFFF85149),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    if (isEn) "Feed Scroll Blocked!" else "Akış Kaydırması Engellendi!",
                                    color = Color(0xFFF85149),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isEn) "AwayDoomscrollin blocks endless feed scrolling as well." 
                                    else "AwayDoomscrollin' sadece Reels değil, Ana Sayfa üzerindeki sonsuz gönderi akışını da engeller.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { simulatorState = SimulatorState.HOME }) {
                                    Text(if (isEn) "Reset Simulation" else "Simülasyonu Sıfırla", fontSize = 10.sp)
                                }
                            }
                        }
                        SimulatorState.SAFE_ZONE -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = Color(0xFF3FB950),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    if (isEn) "Safe Zone" else "Güvenli Bölge",
                                    color = Color(0xFF3FB950),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isEn) "DMs, Search, and Profiles are allowed. Only endless video/feed scrolling is blocked." 
                                    else "Uygulama sadece sonsuz akış kaydırmalarını engeller. Mesajlaşma ve arama gibi yararlı işlevlere izin verir.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { simulatorState = SimulatorState.HOME }) {
                                    Text(if (isEn) "Back to Home" else "Ana Sayfaya Dön", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isEn) "Test it yourself!" else "Kendiniz test edin!",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun OnboardingStepThree(isEn: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isEn) "What It CAN & CANNOT Do" else "Ne Yapabilir? Ne Yapamaz?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isEn) 
                "With full transparency, here is what the shield can do and our privacy guarantees:" 
            else 
                "Tam şeffaflık ile gizliliğiniz ve sistem izinlerimiz:",
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
                        if (isEn) "ACTIVE SHIELD (WHAT IT CAN DO)" else "AKTİF KALKAN (YAPABİLDİKLERİ)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF87),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "100% Local Analysis: Screen events run entirely on device CPU. Only downloads rule updates (JSON) from GitHub every 6h (zero personal data sent)." else "%100 Yerel Analiz: Ekran olayları tamamen telefonunuzun yerel işlemcisinde çalışır. Dinamik kalkan için yalnızca 6 saatte bir GitHub'dan kural dosyasını (JSON) indirir (dışarıya hiçbir veri gönderilmez).")
                MatrixBullet(if (isEn) "Beta shield: Intervenes instantly when a supported short-video feed is recognized." else "Beta kalkan: Desteklenen kısa video akışı algılandığında anında müdahale eder.")
                MatrixBullet(if (isEn) "Instantly terminates the scroll feed task and returns you to the Home screen." else "Sonsuz akış görevini anında sonlandırarak sizi Ana Ekrana döndürür.")
                MatrixBullet(if (isEn) "Sends an instant scroll alert notification to pull you out of the trance." else "Sizi transtan çıkarıp odaklanmanız için anında kaydırma uyarısı gönderir.")
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
                        if (isEn) "PRIVACY GUARANTEE (WHAT IT CANNOT DO)" else "GİZLİLİK GARANTİSİ (YAPAMADIKLARI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "NEVER reads, captures, or records your messages, passwords, or photos." else "Mesajlarınızı, şifrelerinizi, fotoğraflarınızı veya ekranınızı ASLA okumaz ve kaydetmez.", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT send your personal data or browsing history to any server." else "Sunuculara kişisel veri veya gezinme geçmişi göndermez (Topluluk katkısı tamamen isteğe bağlıdır).", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT drain battery or memory in the background." else "Arka planda pilinizi ve belleğinizi tüketmez.", isNegative = true)
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
    var showScopeDialog by remember { mutableStateOf(false) }

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
            text = if (isEn) "Target Apps & Preferences" else "Hedef Uygulamalar & Tercihler",
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isEn) "Select which platforms shield protects and set preferences:" else "Kalkanın hangi platformlarda aktif olacağını seçin:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 1. INSTAGRAM COMPACT CARD
        CompactOnboardingAppCard(
            appName = "Instagram",
            subtitle = if (isEn) "Reels & Infinite Feed" else "Reels & Sonsuz Akış",
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
            subtitle = if (isEn) "Short Video Feed" else "Kısa Video Akışı",
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
            subtitle = if (isEn) "Shorts Screen Only" else "Shorts Ekranı",
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

        // 4. KAPSAM BİLGİSİ BUTONU
        Surface(
            onClick = { showScopeDialog = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Protection Scope Info (Safe Areas)" else "Kalkan Kapsamı Bilgisi (Güvenli Alanlar)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. TELEMETRİ KARTI
        var isTelemetryOn by remember { mutableStateOf(TelemetryManager.isTelemetryEnabled(context)) }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isTelemetryOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_handshake),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "Community Shield Calibration (Opt-in)" else "Topluluk Algılama Katkısı (İsteğe Bağlı)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEn) {
                            "Help us improve shield accuracy when Instagram, TikTok, or YouTube updates their UI. Enabling this shares anonymous block statistics for your device model so we can keep detection rules optimized.\n\nExplicit opt-in shares a pseudonymous installation ID, OS version, and aggregate block/streak stats. Personal messages, passwords, or screen captures are NEVER recorded. Results expire within 90 days. Rule updates run automatically from GitHub."
                        } else {
                            "Instagram, TikTok veya YouTube arayüzünü güncellediğinde kalkan kurallarını anında kalibre edebilmemiz için cihazınızdaki anonim başarı istatistiğini paylaşarak algılama motorunu birlikte güçlendirin.\n\nAçık onay; rastgele kurulum kimliği, işletim sistemi ve toplu engelleme/seri verisini anonim paylaşır. Kişisel mesajlar, şifreler veya ekran görüntüleri ASLA toplanmaz. Kayıtlar en geç 90 günde silinir. Kural güncellemeleri GitHub'dan otomatik alınır."
                        },
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isTelemetryOn,
                    onCheckedChange = { checked ->
                        isTelemetryOn = checked
                        TelemetryManager.setTelemetryEnabled(context, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF070A12),
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        if (showScopeDialog) {
            val scopeTitle = if (isEn) "Protection Scope & Safe Areas" else "Kalkan Kapsamı & Güvenli Alanlar"
            val scopeContent = if (isEn) {
                "AwayDoomscrollin' Protects:\n\n" +
                "• Instagram (BETA): The shield attempts to detect Reels/Explore feeds while excluding DMs and comments. Third-party UI changes can cause misses or false positives.\n\n" +
                "• TikTok (BETA): Attempts to detect the short-video feed while excluding Inbox and Direct Messages; misses or false positives are possible.\n\n" +
                "• YouTube Shorts (BETA): Attempts to detect the Shorts screen while excluding normal videos and Search; misses or false positives are possible."
            } else {
                "AwayDoomscrollin' Koruması:\n\n" +
                "• Instagram (BETA): Kalkan Reels/Keşfet akışlarını algılamaya, DM ve yorumları kapsam dışında tutmaya çalışır. Üçüncü taraf arayüz değişiklikleri kaçırılan veya hatalı algılamalara yol açabilir.\n\n" +
                "• TikTok (BETA): Kısa video akışını algılamaya, Gelen Kutusu ve mesajları kapsam dışında tutmaya çalışır; kaçırılan veya hatalı algılamalar olabilir.\n\n" +
                "• YouTube Shorts (BETA): Shorts ekranını algılamaya, normal videoları ve Arama'yı kapsam dışında tutmaya çalışır; kaçırılan veya hatalı algılamalar olabilir."
            }

            ScrollableTextDialog(
                isEn = isEn,
                title = scopeTitle,
                content = scopeContent,
                iconRes = R.drawable.ic_instagram,
                iconTint = MaterialTheme.colorScheme.primary,
                onDismiss = { showScopeDialog = false }
            )
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
    isIgnoringBattery: Boolean,
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

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
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
            text = if (isEn) "Activate Shield Protection" else "Korumayı Etkinleştirin",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isEn) "Complete the permissions below to enable automated protection:" else "Otomatik korumanın çalışabilmesi için lütfen aşağıdaki izinleri verin:",
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

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
                    Text(if (isEn) "Accessibility Permission" else "Erişilebilirlik İzni", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isEn) "Required for automatic scroll detection and app stopping." else "Otomatik tespit ve durdurma eylemi için şarttır.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
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
                                if (isEn) "Permission Granted" else "İzin Verildi",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            if (isEn) "1. Grant Accessibility Permission" else "1. Erişilebilirlik İznini Aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isIgnoringBattery) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isIgnoringBattery) {
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
                    Text(if (isEn) "Background Protection" else "Arka Plan Koruması", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isEn) "Prevents Android system from putting shield to sleep." else "Sistemin arka planda uyutulmasını engeller.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIgnoringBattery) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                        contentColor = if (isIgnoringBattery) MaterialTheme.colorScheme.secondary else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isIgnoringBattery) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isEn) "Restriction Removed" else "Kısıtlama Kaldırıldı",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            if (isEn) "2. Disable Battery Restrictions" else "2. Pil Optimizasyonunu Kaldır",
                            fontWeight = FontWeight.Bold
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
                                text = "3",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (isEn) "Notification Permission" else "Bildirim İzni", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isEn) "Sends daily streak achievements and prevents system from killing shield in background." else "Günlük motivasyon serisi (streak) güncellemeleri ve uygulamanın arka planda öldürülmesini engeller.",
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
                                    if (isEn) "Notifications Allowed" else "Bildirimlere İzin Verildi",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                if (isEn) "3. Allow Notifications" else "3. Bildirimlere İzin Ver",
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
                        text = if (isEn) "Privacy & Legal Compliance" else "Gizlilik Beyanı ve Sözleşmeler",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEn) 
                        "By continuing, you agree to our terms. Personal data is never sold or shared." 
                    else 
                        "Devam ederek şartları kabul etmiş olursunuz. Kişisel verileriniz asla satılmaz ve paylaşılmaz.",
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
                                text = if (isEn) "Terms of Service" else "Kullanım Şartları",
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
                    buttonText ?: if (isEn) "Got It & Close" else "Anladım & Kapat",
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
                    text = if (isEn) "Send Feedback" else "Geri Bildirim Gönder",
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
                        
                        context.startActivity(Intent.createChooser(mailIntent, if (isEn) "Send Email via..." else "E-Posta Gönder..."))
                        
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
                        text = if (isEn) "Send Email" else "E-Posta Gönder",
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
fun AboutScreen(
    prefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf<Boolean>(false) }

    var currentLang by remember { mutableStateOf<String>(getAppLanguage(prefs)) }
    val isEn = currentLang == "en"

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.1.0"
        } catch (_: Exception) {
            "1.1.0"
        }
    }

    if (showFeedbackDialog) {
        FeedbackSubmissionDialog(
            manufacturer = Build.MANUFACTURER ?: "Android",
            model = Build.MODEL ?: "Device",
            onDismiss = { showFeedbackDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Arka Plan Şeffaf Logo (Watermark)
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Background Watermark Logo",
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer(alpha = 0.08f)
                .clip(CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Left Accent Header Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF00F2FE))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isEn) "About & Contact" else "Hakkında & İletişim",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00F2FE)
                    )
                    Text(
                        text = if (isEn) "App info, language preferences and feedback" else "Uygulama künyesi, dil seçimi ve geri bildirim",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. KATEGORİ: UYGULAMA KÜNYESİ
            Text(
                text = if (isEn) "APP IDENTITY & SPECS" else "UYGULAMA KÜNYESİ",
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
                                    painter = painterResource(id = R.mipmap.ic_launcher),
                                    contentDescription = "App Logo",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(9.dp))
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
                                text = if (isEn) "Version $appVersion • Open Source" else "Sürüm $appVersion • Açık Kaynak",
                                fontSize = 11.5.sp,
                                color = Color(0xFF00F2FE),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimal Trust Badges Strip (3 İnce Rozet)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(R.drawable.ic_lock, if (isEn) "100% Local" else "%100 Yerel", Color(0xFF00F2FE)),
                            Triple(R.drawable.ic_slash_ban, if (isEn) "Zero Ads" else "Sıfır Reklam", Color(0xFF00FF87)),
                            Triple(R.drawable.ic_battery, if (isEn) "Battery Safe" else "Pil Dostu", Color(0xFFFFB703))
                        ).forEach { (iconRes, label, color) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF070A12),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. KATEGORİ: DİL SEÇİMİ
            Text(
                text = if (isEn) "LANGUAGE PREFERENCE" else "DİL TERCİHİ",
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

            // 3. KATEGORİ: RESMİ WEB PORTALLARI
            Text(
                text = if (isEn) "OFFICIAL WEB PORTALS" else "RESMİ WEB PORTALLARI",
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
                                text = if (isEn) "Official Application Website" else "Uygulama Resmi Web Sitesi",
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "resolvecommunity.com",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF00FF87).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "DEVELOPER",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00FF87),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isEn) "Resolve Community Portal" else "Geliştirici Topluluk Portalı",
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

            // 4. KATEGORİ: DESTEK & ŞEFFAFLIK
            Text(
                text = if (isEn) "SUPPORT & TRANSPARENCY" else "DESTEK & GERİ BİLDİRİM",
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
                                if (isEn) "Send Feedback or Report Bug" else "Geri Bildirim veya Hata Bildir",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // ÜÇÜNCÜL BUTON: GITHUB (GPLv3)
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ResolveCommunity/AwayDoomscrollin"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEn) "GitHub Source Code (GPLv3)" else "GitHub Açık Kaynak (GPLv3)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. KATEGORİ: TOPLULUK KATKISI
            Text(
                text = if (isEn) "COMMUNITY CALIBRATION" else "TOPLULUK KATKISI (İSTEĞE BAĞLI)",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. TOPLULUK ALGILAMA KATKISI KARTI
            var isTelemetryOnInAbout by remember { mutableStateOf(TelemetryManager.isTelemetryEnabled(context)) }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isTelemetryOnInAbout) Color(0xFF00F2FE).copy(alpha = 0.5f) else Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_handshake),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Community Shield Calibration (Opt-in)" else "Topluluk Algılama Katkısı (İsteğe Bağlı)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = if (isEn) {
                                "Help us keep detection rules optimized when Instagram, TikTok, or YouTube updates their UI. Enabling this shares anonymous block statistics for your device model.\n\nExplicit opt-in shares a pseudonymous installation ID, OS version, and aggregate block/streak stats. Personal messages, passwords, or screen captures are NEVER recorded. Results expire within 90 days. Rule updates run automatically from GitHub."
                            } else {
                                "Instagram, TikTok veya YouTube arayüzünü güncellediğinde kalkan kurallarını anında kalibre edebilmemiz için cihazınızdaki anonim başarı istatistiğini paylaşarak algılama motorunu birlikte güçlendirin.\n\nAçık onay; rastgele kurulum kimliği, işletim sistemi ve toplu engelleme/seri verisini anonim paylaşır. Kişisel mesajlar, şifreler veya ekran görüntüleri ASLA toplanmaz. Kayıtlar en geç 90 günde silinir. Kural güncellemeleri GitHub'dan otomatik alınır."
                            },
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            lineHeight = 14.5.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isTelemetryOnInAbout,
                        onCheckedChange = { checked ->
                            isTelemetryOnInAbout = checked
                            TelemetryManager.setTelemetryEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF070A12),
                            checkedTrackColor = Color(0xFF00F2FE)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            // FOOTER
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© 2026 Resolve Community • GNU GPLv3",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isEn) "Licensed under GNU General Public License v3.0" else "GNU Genel Açık Lisansı (GPLv3) ile Lisanslanmıştır",
                    fontSize = 10.sp,
                    color = Color(0xFF00F2FE).copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "support@awaydoomscrollin.com",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================
// 3 SEKMELİ UYGULAMA MENÜSÜ
// ==========================================
@Composable
fun MainNavigationDashboard(
    prefs: android.content.SharedPreferences,
    onReopenOnboarding: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentLang by remember { mutableStateOf(getAppLanguage(prefs)) }
    
    val notificationQueue = remember { mutableStateListOf<UnlockedNotificationItem>() }
    var activeNotification by remember { mutableStateOf<UnlockedNotificationItem?>(null) }
    var showJourneyDialogFromNotification by remember { mutableStateOf(false) }
    var notificationTargetTab by remember { mutableIntStateOf(0) }

    val isEn = currentLang == "en"

    fun checkAndQueueUnlocks() {
        val streakDays = prefs.getInt("streak_days", 0)
        val currentTier = getCurrentFocusTier(streakDays)
        val lastSeenTier = prefs.getInt("last_seen_tier_level", 1)
        val seenAchievements = prefs.getStringSet("seen_unlocked_achievements", emptySet())?.toMutableSet() ?: mutableSetOf()

        val allAchievements = getAllAchievements(prefs)
        val newlyUnlockedAchievements = allAchievements.filter { it.isUnlocked && !seenAchievements.contains(it.id) }

        if (currentTier.level > lastSeenTier) {
            prefs.edit().putInt("last_seen_tier_level", currentTier.level).apply()
            val title = if (isEn) "Tier Level Up: ${currentTier.nameEn}!" else "Yeni Kademe: ${currentTier.nameTr}!"
            val desc = if (isEn) "You reached Tier ${currentTier.level} (${currentTier.minDays}+ days streak)!" else "${currentTier.level}. Kademeye ulaştın (${currentTier.minDays}+ gün seri)!"
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
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
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
                    val duration = 280
                    if (targetState > initialState) {
                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(duration)) + 
                         fadeIn(animationSpec = tween(duration)) + 
                         scaleIn(initialScale = 0.96f, animationSpec = tween(duration))) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(duration)) + 
                         fadeOut(animationSpec = tween(duration)) + 
                         scaleOut(targetScale = 0.96f, animationSpec = tween(duration)))
                    } else {
                        (slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(duration)) + 
                         fadeIn(animationSpec = tween(duration)) + 
                         scaleIn(initialScale = 0.96f, animationSpec = tween(duration))) togetherWith
                        (slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(duration)) + 
                         fadeOut(animationSpec = tween(duration)) + 
                         scaleOut(targetScale = 0.96f, animationSpec = tween(duration)))
                    }
                },
                label = "TabSwitchAnimation"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(onReopenOnboarding = onReopenOnboarding, prefs = prefs)
                    1 -> ModesAndAppsScreen(prefs = prefs)
                    2 -> ProgressStatusScreen(prefs = prefs)
                    3 -> AboutScreen(prefs = prefs)
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
    totalBlocks: Int,
    savedTimeStr: String,
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
                    if (isEn) "SYSTEM SHIELD ACTIVE" else "SİSTEM KORUMASI AKTİF"
                } else {
                    if (isEn) "SHIELD DEACTIVATED" else "KORUMA DEVRE DIŞI"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isAccessibilityActive) {
            val timeFontSize = when {
                savedTimeStr.length > 14 -> 30.sp
                savedTimeStr.length > 8 -> 36.sp
                else -> 46.sp
            }
            val timeLineHeight = when {
                savedTimeStr.length > 14 -> 38.sp
                savedTimeStr.length > 8 -> 44.sp
                else -> 52.sp
            }

            // Hero Typography Metric with adaptive sizing and proper line height
            Text(
                text = savedTimeStr,
                fontSize = timeFontSize,
                lineHeight = timeLineHeight,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEn) "total focus time reclaimed from feeds" else "sonsuz akışlardan kurtarılan serbest zaman",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sleek Borderless Interventions Sub-Metric (No Box/Border)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00F2FE))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEn) "Broke the loop $totalBlocks times today" else "Bugün $totalBlocks kez döngüyü kırdın",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F2FE)
                )
            }
        } else {
            Text(
                text = if (isEn) "Shield is Sleeping" else "Kalkan Uyku Modunda",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEn)
                    "Enable accessibility to automatically guard against Reels and Shorts scrolling."
                else
                    "Reels ve Shorts tuzaklarını engellemek için lütfen erişilebilirlik iznini etkinleştirin.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActivateClick,
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
                        text = if (isEn) "ACTIVATE SHIELD" else "KORUMAYI ETKİNLEŞTİR",
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
                    text = if (isEn) "CRITICAL TIME: $timeRange" else "KRİTİK ZAMAN: $timeRange",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF0055),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isEn) "$cnt traps blocked during this hour. Stay focused!" else "Bu saatte $cnt tuzak engellendi. Odağınızı koruyun!",
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
        neuroBenefitTr = "Dopamin tuzaklarını fark etmeye başlama",
        neuroBenefitEn = "Beginning to notice dopamine traps"
    ),
    FocusTier(
        level = 2, minDays = 1, maxDays = 2,
        nameTr = "İlk Kıvılcım", nameEn = "First Spark",
        iconRes = R.drawable.ic_tier_spark,
        color = Color(0xFF00FF87),
        descTr = "Döngüyü kırmaya başladınız. Otomatik kaydırma refleksi zayıflıyor.",
        descEn = "You started breaking the loop. Autopilot scrolling weakens.",
        neuroBenefitTr = "Otomatik el alışkanlığı kontrol altına alınıyor",
        neuroBenefitEn = "Subconscious swiping reflex is being brought under control"
    ),
    FocusTier(
        level = 3, minDays = 3, maxDays = 5,
        nameTr = "Dopamin Sıfırlama", nameEn = "Dopamine Reset",
        iconRes = R.drawable.ic_tier_fire,
        color = Color(0xFFFF5500),
        descTr = "Beyin sürekli uyarıcı arayışını yavaşlatıyor. Odak süresi uzuyor.",
        descEn = "Brain slows down the craving for constant stimuli. Focus span grows.",
        neuroBenefitTr = "Dopamin reseptörleri duyarlılık kazanıyor",
        neuroBenefitEn = "Dopamine receptors regain sensitivity"
    ),
    FocusTier(
        level = 4, minDays = 6, maxDays = 9,
        nameTr = "Çelik İrade", nameEn = "Steel Will",
        iconRes = R.drawable.ic_tier_bolt,
        color = Color(0xFFFFB700),
        descTr = "1 haftalık kritik eşik aşıldı! Ekran dürtülerine karşı direnç zirvede.",
        descEn = "1-week critical milestone achieved! Strong resistance against screen urges.",
        neuroBenefitTr = "Prefrontal korteks karar alma gücünü geri kazanıyor",
        neuroBenefitEn = "Prefrontal cortex regains decision-making dominance"
    ),
    FocusTier(
        level = 5, minDays = 10, maxDays = 13,
        nameTr = "Akışa Hakimiyet", nameEn = "Flow Master",
        iconRes = R.drawable.ic_tier_water,
        color = Color(0xFF00E5FF),
        descTr = "Artık ekran sizi değil, siz zamanınızı yönetiyorsunuz.",
        descEn = "You control your time now instead of screens controlling you.",
        neuroBenefitTr = "Derin odaklanma ve akış (flow) durumuna giriş hızlanıyor",
        neuroBenefitEn = "Entering deep focus and flow state becomes effortless"
    ),
    FocusTier(
        level = 6, minDays = 14, maxDays = 20,
        nameTr = "Zihinsel Berraklık", nameEn = "Mental Clarity",
        iconRes = R.drawable.ic_tier_crystal,
        color = Color(0xFFBD00FF),
        descTr = "2 haftalık dijital detoks etkisi. Zihin gürültüsü ve sis dağıldı.",
        descEn = "2 weeks of digital detox. Brain fog and cognitive noise disperse.",
        neuroBenefitTr = "Bilişsel yorgunluk ve beyin sisi minimuma iniyor",
        neuroBenefitEn = "Cognitive fatigue and brain fog drop to minimum"
    ),
    FocusTier(
        level = 7, minDays = 21, maxDays = 29,
        nameTr = "Nöroplastisite Eşiği", nameEn = "Neuroplastic Shift",
        iconRes = R.drawable.ic_tier_brain,
        color = Color(0xFFFF2A85),
        descTr = "21 gün kuralı: Beyinde yeni ve sağlıklı dikkat nöron yolları kalıcılaştı.",
        descEn = "21-day rule: New and healthy neural pathways are firmly established.",
        neuroBenefitTr = "Kalıcı yeni odaklanma alışkanlıkları oturdu",
        neuroBenefitEn = "Permanent healthy attention habits are solidified"
    ),
    FocusTier(
        level = 8, minDays = 30, maxDays = 44,
        nameTr = "Odak Şampiyonu", nameEn = "Focus Champion",
        iconRes = R.drawable.ic_tier_crown,
        color = Color(0xFFFFD700),
        descTr = "Tam 1 ay! Artık doomscrolling bağımlılığından tamamen özgürsünüz.",
        descEn = "A full month! You are completely free from doomscrolling addiction.",
        neuroBenefitTr = "Dikkat süresi ve hafıza kapasitesi belirgin şekilde arttı",
        neuroBenefitEn = "Attention span and memory capacity significantly increased"
    ),
    FocusTier(
        level = 9, minDays = 45, maxDays = 59,
        nameTr = "Elmas Disiplin", nameEn = "Diamond Discipline",
        iconRes = R.drawable.ic_tier_gem,
        color = Color(0xFF00F5D4),
        descTr = "Sarsılmaz odak. Günlük üretim ve öğrenme veriminiz katlandı.",
        descEn = "Unshakable discipline. Daily productivity and learning multiplied.",
        neuroBenefitTr = "Dürtüsel davranış kontrolü otomatikleşti",
        neuroBenefitEn = "Impulsive behavior control is now fully automatic"
    ),
    FocusTier(
        level = 10, minDays = 60, maxDays = 89,
        nameTr = "Zaman Mimarı", nameEn = "Time Architect",
        iconRes = R.drawable.ic_tier_monument,
        color = Color(0xFF9D4EDD),
        descTr = "2 ay! Hayatınızın her anını bilinçli ve amaç doğrultusunda inşa ediyorsunuz.",
        descEn = "2 months! You actively construct your life with deliberate purpose.",
        neuroBenefitTr = "Uzun vadeli planlama ve hedef odaklılık güçlendi",
        neuroBenefitEn = "Long-term planning and goal-oriented focus strengthened"
    ),
    FocusTier(
        level = 11, minDays = 90, maxDays = 99,
        nameTr = "Döngü Efendisi", nameEn = "Loop Master",
        iconRes = R.drawable.ic_tier_galaxy,
        color = Color(0xFFFF0055),
        descTr = "Çeyrek yıl (90 gün)! Dijital dünyanın en güçlü kancaları bile size işlemiyor.",
        descEn = "Quarter of a year! Even the strongest digital hooks have no hold on you.",
        neuroBenefitTr = "Dopamin sistemi tamamen doğal ritmine kavuştu",
        neuroBenefitEn = "Dopamine system is fully recalibrated to its natural rhythm"
    ),
    FocusTier(
        level = 12, minDays = 100, maxDays = Int.MAX_VALUE,
        nameTr = "Zen Ustası", nameEn = "Zen Master",
        iconRes = R.drawable.ic_tier_zen,
        color = Color(0xFFFFE600),
        descTr = "100+ Gün! Mutlak zihinsel berraklık ve dijital bilgelik zirvesi.",
        descEn = "100+ Days! Pinnacle of absolute mental clarity and digital serenity.",
        neuroBenefitTr = "Kalıcı iç huzur, derin odaklanma ve yüksek farkındalık",
        neuroBenefitEn = "Sustained inner calm, deep immersion, and heightened presence"
    )
)

fun getCurrentFocusTier(streakDays: Int): FocusTier {
    return ALL_FOCUS_TIERS.find { streakDays >= it.minDays && streakDays <= it.maxDays }
        ?: ALL_FOCUS_TIERS.last()
}

fun getNextFocusTier(streakDays: Int): FocusTier? {
    val current = getCurrentFocusTier(streakDays)
    val nextIndex = ALL_FOCUS_TIERS.indexOf(current) + 1
    return if (nextIndex < ALL_FOCUS_TIERS.size) ALL_FOCUS_TIERS[nextIndex] else null
}

fun getTierProgress(streakDays: Int): Float {
    val current = getCurrentFocusTier(streakDays)
    val next = getNextFocusTier(streakDays) ?: return 1.0f
    val span = next.minDays - current.minDays
    if (span <= 0) return 1.0f
    val elapsed = streakDays - current.minDays
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
            titleEn = "First Reels Brake",
            descTr = "Instagram Reels tuzağını 1 kez engelle",
            descEn = "Block Instagram Reels trap 1 time",
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
            descTr = "Instagram'da 10 kez kaydırma döngüsünü kır",
            descEn = "Break the scroll loop 10 times on Instagram",
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
            descTr = "Instagram akışını 50 kez alt et",
            descEn = "Overpower the Instagram feed 50 times",
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
            descTr = "TikTok 'Sizin İçin' akışını 1 kez durdur",
            descEn = "Stop the TikTok 'For You' feed 1 time",
            iconRes = R.drawable.ic_tiktok,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 1
        ),
        FocusAchievement(
            id = "tt_10",
            category = AchievementCategory.TIKTOK,
            titleTr = "Dopamin Kalkanı",
            titleEn = "Dopamine Shield",
            descTr = "TikTok akışını 10 kez engelleyerek odağını koru",
            descEn = "Protect focus by blocking TikTok 10 times",
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 10
        ),
        FocusAchievement(
            id = "tt_50",
            category = AchievementCategory.TIKTOK,
            titleTr = "Sonsuz Akış Kırıcı",
            titleEn = "Endless Stream Breaker",
            descTr = "TikTok hipnozunu 50 kez kır",
            descEn = "Break TikTok hypnosis 50 times",
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
            descTr = "TikTok'ta 100 tuzağı başarıyla püskürt",
            descEn = "Successfully repel 100 traps on TikTok",
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
            descTr = "YouTube Shorts tuzağını 1 kez engelle",
            descEn = "Block YouTube Shorts trap 1 time",
            iconRes = R.drawable.ic_youtube,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 1
        ),
        FocusAchievement(
            id = "yt_10",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Kısa Video Panzehri",
            titleEn = "Short Form Antidote",
            descTr = "Shorts döngüsünü 10 kez durdur",
            descEn = "Halt the Shorts loop 10 times",
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 10
        ),
        FocusAchievement(
            id = "yt_50",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Kırmızı Hat Savunması",
            titleEn = "Red Line Defense",
            descTr = "YouTube Shorts'u 50 kez bertaraf et",
            descEn = "Repel YouTube Shorts 50 times",
            iconRes = R.drawable.ic_target,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 50
        ),
        FocusAchievement(
            id = "yt_100",
            category = AchievementCategory.YOUTUBE,
            titleTr = "Zaman Kurtarıcısı",
            titleEn = "Time Savior",
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
            descTr = "Toplamda ilk tuzağı kır",
            descEn = "Break your very first trap overall",
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
            descTr = "3 günlük aktif kalkan serisine ulaş",
            descEn = "Reach a 3-day active shield streak",
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
            descTr = "7 günlük kesintisiz kalkan serisini tamamla",
            descEn = "Complete a 7-day unbroken shield streak",
            iconRes = R.drawable.ic_tier_bolt,
            brandColor = Color(0xFFFFB700),
            currentVal = streakDays,
            targetVal = 7
        ),
        FocusAchievement(
            id = "gen_streak_21",
            category = AchievementCategory.GENERAL,
            titleTr = "21 Gün Alışkanlık Devrimi",
            titleEn = "21-Day Habit Revolution",
            descTr = "21 gün kuralını tamamla ve kalıcı alışkanlık kazan",
            descEn = "Achieve the 21-day rule for lasting cognitive habits",
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
            descTr = "Toplam 100 kez tuzakları durdur",
            descEn = "Stop digital traps 100 times overall",
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
fun FocusJourneyOverviewCard(
    streakDays: Int,
    totalBlocks: Int,
    isEn: Boolean,
    onOpenJourney: () -> Unit,
    prefs: SharedPreferences
) {
    val currentTier = getCurrentFocusTier(streakDays)
    val nextTier = getNextFocusTier(streakDays)
    val tierProgress = getTierProgress(streakDays)
    val allAchievements = remember(totalBlocks, streakDays) { getAllAchievements(prefs) }
    val unlockedCount = allAchievements.count { it.isUnlocked }
    val totalCount = allAchievements.size

    Surface(
        onClick = onOpenJourney,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, currentTier.color.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Icon + Level & Name + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(currentTier.color.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, currentTier.color.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = currentTier.iconRes),
                            contentDescription = null,
                            tint = currentTier.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEn) "LEVEL ${currentTier.level} OF 12" else "KADEME ${currentTier.level} / 12",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTier.color,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isEn) currentTier.nameEn else currentTier.nameTr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = currentTier.color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, currentTier.color.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "Details" else "Detaylar",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = currentTier.color
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            tint = currentTier.color,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar to Next Tier
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
                        text = if (nextTier != null) {
                            if (isEn) nextTier.nameEn else nextTier.nameTr
                        } else {
                            if (isEn) "Peak Master" else "Zirve Seviye"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (nextTier != null) {
                        val remaining = nextTier.minDays - streakDays
                        if (isEn) "$remaining days left" else "$remaining gün kaldı"
                    } else {
                        "100%"
                    },
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

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Ribbon: Special Achievements progress + View all action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131A2A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trophy),
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "$unlockedCount / $totalCount Special Achievements" else "$unlockedCount / $totalCount Özel Başarım Açıldı",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEn) "View All" else "Tümünü Gör",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
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

    var selectedTab by remember { mutableIntStateOf(initialTab) }
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
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isEn) "Focus Tiers & Special Achievements" else "Odak Kademeleri & Özel Başarımlar",
                                    fontSize = 11.sp,
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
                                                modifier = Modifier.padding(8.dp),
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
                                                    text = if (isEn) "Neuro Benefit: ${currentTier.neuroBenefitEn}" else "Nörolojik Kazanım: ${currentTier.neuroBenefitTr}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF00FF87)
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
                                                    text = if (isEn) "$remaining days left" else "$remaining gün kaldı",
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
                                    text = if (isEn) "TIERS ROADMAP" else "KADEMELER YOLCULUK HARİTASI",
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

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = if (isEn) tierItem.descEn else tierItem.descTr,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    lineHeight = 15.sp
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

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
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isCurrent) Color(0xFF00FF87) else Color.White.copy(alpha = 0.5f)
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

                                                    Spacer(modifier = Modifier.height(3.dp))

                                                    Text(
                                                        text = if (isEn) ach.descEn else ach.descTr,
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.65f),
                                                        lineHeight = 15.sp
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

fun formatSavedFocusTime(totalBlocks: Int, isEn: Boolean): String {
    if (totalBlocks <= 0) return if (isEn) "0 Minute" else "0 Dakika"
    val totalSeconds = totalBlocks * 30
    if (totalSeconds < 60) {
        return if (isEn) "${totalSeconds}s" else "${totalSeconds} Sn"
    }
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remMins = minutes % 60
    return if (hours > 0) {
        if (remMins > 0) {
            if (isEn) "$hours Hour $remMins Minute" else "$hours Saat $remMins Dakika"
        } else {
            if (isEn) "$hours Hour" else "$hours Saat"
        }
    } else {
        if (isEn) "$minutes Minute" else "$minutes Dakika"
    }
}

// ------------------------------------------
// 1. SEKME: ANA SAYFA (HOME SCREEN)
// ------------------------------------------
@Composable
fun HomeScreen(
    onReopenOnboarding: () -> Unit,
    prefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    val (isAccessibilityActive, _) = rememberPermissionStatus()
    val isEn = getAppLanguage(prefs) == "en"
    var totalBlocks by remember { mutableIntStateOf(prefs.getInt("total_blocks", 0)) }
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
            totalBlocks = prefs.getInt("total_blocks", 0)
            kotlinx.coroutines.delay(2000)
        }
    }

    val savedTimeStr = formatSavedFocusTime(totalBlocks, isEn)

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
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

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
                totalBlocks = totalBlocks,
                savedTimeStr = savedTimeStr,
                onActivateClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            // In-App Update Banner (GitHub Releases / F-Droid Update Check)
            val remoteConfig = remember { RemoteRuleManager.getConfig(context) }
            val currentVersionCode = 1

            if (remoteConfig.latestVersionCode > currentVersionCode) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F1523),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_rocket),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "New Version ${remoteConfig.latestVersionName} Available!" else "Yeni Sürüm ${remoteConfig.latestVersionName} Mevcut!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF00F2FE)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteConfig.updateUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE))
                        ) {
                            Text(
                                text = if (isEn) "Update" else "Güncelle",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF070A12),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // BORDERLESS PEAK HOUR ROW
            val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
            val peakHour = remember(totalBlocks) {
                var maxBlocks = 0
                var peakH = -1
                for (h in 0..23) {
                    val key = "blocks_${todayStr}_hour_${h.toString().padStart(2, '0')}"
                    val count = prefs.getInt(key, 0)
                    if (count > maxBlocks) { maxBlocks = count; peakH = h }
                }
                Pair(peakH, maxBlocks)
            }

            BorderlessPeakHourRow(
                peakHour = peakHour,
                isEn = isEn
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ⏱️ BORDERLESS SHIELD ACTIVITY LOG
            BorderlessShieldActivityLog(prefs = prefs)

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onReopenOnboarding,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (isEn) "Setup & Preview Guide" else "Kurulum ve Önizleme Rehberi",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                val rawApp = parts.getOrNull(1) ?: (if (isEn) "Instagram Reels & Feed" else "Instagram Reels & Akış")
                val app = if (rawApp.contains("Gönderiler", ignoreCase = true)) {
                    if (isEn) "Instagram Reels & Feed" else "Instagram Reels & Akış"
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
            text = if (isEn) "RECENT INTERVENTIONS" else "SON ENGELLEMELER",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (logList.isEmpty()) {
            Text(
                text = if (isEn) "No recent interventions yet. Focus is safe." else "Henüz bir tuzak engellenmedi. Zihniniz ve odağınız güvende.",
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                val displayList = logList.take(4)
                displayList.forEachIndexed { index, (time, app, appCount) ->
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
                                    text = if (isEn) "Time $time" else "Saat $time",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Text(
                            text = if (isEn) "Blocked ${appCount}x today" else "Bugün $appCount. kez",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor.copy(alpha = 0.9f),
                            letterSpacing = 0.3.sp
                        )
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
fun ModesAndAppsScreen(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val isEn = getAppLanguage(prefs) == "en"
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
                    "Attempts to detect Instagram Reels and Explore feeds while excluding DMs and comments. Instagram UI/accessibility changes can cause missed detections or false positives."
                else 
                    "Instagram Reels ve Keşfet akışlarını algılamaya, DM ve yorumları kapsam dışında tutmaya çalışır. Instagram arayüzü/erişilebilirlik ağacındaki değişiklikler kaçırılan veya hatalı algılamalara yol açabilir.",
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
                    "Attempts to detect TikTok's short-video feed while excluding messaging. TikTok UI/accessibility changes can cause missed detections or false positives."
                else 
                    "TikTok kısa video akışını algılamaya ve mesajlaşmayı kapsam dışında tutmaya çalışır. TikTok arayüzü/erişilebilirlik değişiklikleri kaçırılan veya hatalı algılamalara yol açabilir.",
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
                    "Attempts to detect YouTube Shorts while excluding normal videos and Search. YouTube UI/accessibility changes can cause missed detections or false positives."
                else 
                    "YouTube Shorts'u algılamaya, normal videoları ve Arama'yı kapsam dışında tutmaya çalışır. YouTube arayüzü/erişilebilirlik değişiklikleri kaçırılan veya hatalı algılamalara yol açabilir.",
                iconRes = R.drawable.ic_youtube,
                iconTint = Color(0xFFFF0055),
                onDismiss = { activeAppInfoDialog = null }
            )
        }
    }

    if (showPhilosophyDialog) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "Why So Unforgiving?" else "Neden Bu Kadar Acımasız?",
            content = if (isEn) 
                "Other digital-wellbeing apps may use time limits or scroll allowances. AwayDoomscrollin' instead attempts to intervene when its beta detection engine recognizes a supported doomscrolling feed. It does not promise perfect recognition: platform, Android, and manufacturer changes can cause missed detections or false positives."
            else 
                "Diğer dijital refah uygulamaları zaman sınırı veya kaydırma hakkı kullanabilir. AwayDoomscrollin' bunun yerine beta algılama motoru desteklenen bir sonsuz kaydırma akışını tanıdığında müdahale etmeye çalışır. Kusursuz algılama sözü vermez: platform, Android ve üretici değişiklikleri kaçırılan veya hatalı algılamalara yol açabilir.",
            iconRes = R.drawable.ic_shield,
            iconTint = Color(0xFFFFB703),
            onDismiss = { showPhilosophyDialog = false }
        )
    }

    if (pendingDisableApp != null) {
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
                        text = if (isEn) "SHIELD ALARM: STREAK WILL BE BROKEN!" else "SIS-ALARM: SERİ BOZULACAK!",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF0055),
                        letterSpacing = 1.sp,
                        fontSize = 15.sp
                    )
                }
            },
            text = { 
                Text(
                    if (isEn) 
                        "Disabling any app protection shield will INSTANTLY RESET YOUR DAILY STREAK to 0!\n\nThe instant the shield is lowered, your streak drops to 0 days and all your hard work is reset. Do you still want to surrender?" 
                    else 
                        "Herhangi bir uygulamanın koruma kalkanını kapatırsanız GÜNLÜK SERİNİZ (STREAK) ANINDA SIFIRLANIR!\n\nKalkan indirildiği an seriniz 0 güne düşecek ve tüm emeğiniz sıfırlanacaktır. Yine de pes etmek istiyor musunuz?", 
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        val appName = when (pendingDisableApp) {
                            "instagram" -> if (isEn) "Instagram Reels & Feed" else "Instagram Reels & Akış"
                            "tiktok" -> "TikTok"
                            "youtube" -> "YouTube Shorts"
                            else -> "Hedef Uygulama"
                        }
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
                            AntiScrollService().showShieldStatusNotification(
                                context,
                                if (isEn) "$appName Shield Lowered!" else "$appName Kalkanı İndirildi!",
                                if (isEn) "$appName protection shield disabled. Your daily streak has been reset to 0." else "$appName koruma kalkanı kapatıldı. Günlük seriniz 0'a düştü.",
                                1004
                            )
                        } else {
                            android.util.Log.e("ProtectionPreferences", "$appName koruma tercihi kaydedilemedi.")
                        }

                        pendingDisableApp = null
                    }
                ) {
                    Text(if (isEn) "Break Streak & Disable" else "Seriyi Boz ve Kapat", color = Color(0xFFFF0055), fontWeight = FontWeight.ExtraBold)
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
                        Text(if (isEn) "Keep Shield & Streak" else "Seriyi Koru, Açık Kalsın", fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            containerColor = Color(0xFF2A0813),
            titleContentColor = Color(0xFFFF0055),
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

        // Left Accent Header Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00F2FE))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEn) "Shield Controls" else "Koruma Kalkanları",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE)
                )
                Text(
                    text = if (isEn) "Choose platforms to guard against doomscrolling" else "Engellenmesini istediğin sonsuz akışları seç",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. KATEGORİ: GÜVENLİK & FELSEFE
        Text(
            text = if (isEn) "SECURITY & PHILOSOPHY" else "GÜVENLİK & PROTOKOL",
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
                            text = if (isEn) "ZERO-COMPROMISE PROTOCOL" else "TAVİZSİZ KORUMA PROTOKOLÜ",
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
                            "The system does not bargain with screen addiction." 
                        else 
                            "Sistem bağımlılıkla pazarlık yapmaz. Çalışma mantığını görün.",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. KATEGORİ: HEDEF UYGULAMA KALKANLARI
        Text(
            text = if (isEn) "TARGET APP SHIELDS" else "HEDEF UYGULAMA KALKANLARI",
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
            name = if (isEn) "Instagram Reels & Feed" else "Instagram Reels & Akış",
            iconRes = R.drawable.ic_instagram,
            isChecked = isInstagramEnabled,
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
            name = if (isEn) "TikTok Feed" else "TikTok Akışı",
            iconRes = R.drawable.ic_tiktok,
            isChecked = isTiktokEnabled,
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
    brandColor: Color,
    isBeta: Boolean = false,
    onInfoClick: () -> Unit,
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
                        text = if (isChecked) (if (isEn) "Shield Active" else "Kalkan Devrede") else (if (isEn) "Disabled" else "Kapalı"),
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
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = brandColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E2A40)
            )
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
                        "YAKINDA",
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

enum class FocusImpactCategory(
    val iconRes: Int,
    val titleTr: String,
    val titleEn: String,
    val brandColor: Color
) {
    BOOK(R.drawable.ic_impact_book, "Kitap", "Books", Color(0xFF00FF87)),
    LANGUAGE(R.drawable.ic_globe, "Dil", "Languages", Color(0xFF00F2FE)),
    WALK(R.drawable.ic_impact_walk, "Yürüyüş", "Walking", Color(0xFFFFB703)),
    MOVIE(R.drawable.ic_impact_movie, "Sinema", "Cinema", Color(0xFFFF0055)),
    SKILL(R.drawable.ic_impact_code, "Beceri", "Skills", Color(0xFFBD00FF))
}

data class FocusImpactInsight(
    val iconRes: Int,
    val title: String,
    val headline: String,
    val description: String,
    val footnote: String,
    val brandColor: Color,
    val progress: Float,
    val milestoneLabel: String
)

fun getFocusCategoryImpact(
    category: FocusImpactCategory,
    totalBlocks: Int,
    isEn: Boolean
): FocusImpactInsight {
    val totalSeconds = totalBlocks * 30
    val totalMinutes = totalSeconds / 60
    val formattedTime = formatSavedFocusTime(totalBlocks, isEn)

    if (totalMinutes < 2) {
        val prog = ((totalSeconds / 120.0).toFloat()).coerceIn(0.05f, 1f)
        return FocusImpactInsight(
            iconRes = R.drawable.ic_tier_seed,
            title = if (isEn) "Reclaimed Free Time" else "Kazanılan Serbest Zaman",
            headline = if (isEn) "Shield Active" else "Kalkan Devrede",
            description = if (isEn)
                "Your shield is active! Every 30s video interrupted steadily returns valuable free time back to your real life."
            else
                "Artık kalkanınız aktif! Engellenen her 30 saniyelik video akışı, gerçek hayatınıza adım adım serbest zaman olarak geri dönüyor.",
            footnote = if (isEn)
                "* Calculation based on ~30s per block."
            else
                "* Hesaplama her engelleme için ~30 sn tabanlıdır.",
            brandColor = category.brandColor,
            progress = prog,
            milestoneLabel = if (isEn) "First Milestone (2 Minutes)" else "İlk Hedef: 2 Dakika Serbest Zaman"
        )
    }

    return when (category) {
        FocusImpactCategory.BOOK -> {
            val pages = (totalMinutes / 1.2).roundToInt().coerceAtLeast(1)
            val books = totalMinutes / 300.0 // 1 book = 5 hours = 300 mins (website standard)
            val headline = when {
                books >= 1.0 -> {
                    val booksStr = if (books < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", books)
                    if (isEn) "~$booksStr Full Books" else "~$booksStr Tam Kitap"
                }
                else -> {
                    if (isEn) "~$pages Book Pages" else "~$pages Sayfa Kitap"
                }
            }
            val desc = when {
                books >= 1.0 -> {
                    val booksStr = if (books < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", books)
                    if (isEn) "With your saved $formattedTime, you have unlocked enough focus to finish ~$booksStr full books!"
                    else "Kurtardığınız $formattedTime ile tam ~$booksStr kitap bitirecek değerli bir odak süresi kazandınız!"
                }
                else -> {
                    if (isEn) "With your saved $formattedTime, you have gained quiet time to read ~$pages book pages."
                    else "Kurtardığınız $formattedTime sayesinde ~$pages sayfa kitap okuyacak dingin bir vakit kazandınız."
                }
            }
            val progress = if (books >= 1.0) {
                ((totalMinutes % 300) / 300.0).toFloat().coerceIn(0.05f, 1f)
            } else {
                (totalMinutes / 300.0).toFloat().coerceIn(0.05f, 1f)
            }
            val milestoneLabel = if (books >= 1.0) {
                val nextTarget = (ceil(books + 0.01)).toInt().coerceAtLeast(2)
                if (isEn) "Target: $nextTarget Books" else "Hedef: $nextTarget Kitap"
            } else {
                if (isEn) "Target: 1 Full Book (300 Minutes)" else "Hedef: 1 Tam Kitap (300 Dakika)"
            }
            FocusImpactInsight(
                iconRes = R.drawable.ic_impact_book,
                title = if (isEn) "Book Reading" else "Kitap Okuma",
                headline = headline,
                description = desc,
                footnote = if (isEn)
                    "* Website standard: 1 book = 5 hours (300 Minutes), 1 page = ~1.2 Minutes."
                else
                    "* Web sitesi standardı: 1 kitap = 5 saat (300 Dakika), 1 sayfa = ~1.2 Dakika.",
                brandColor = category.brandColor,
                progress = progress,
                milestoneLabel = milestoneLabel
            )
        }

        FocusImpactCategory.LANGUAGE -> {
            val words = (totalMinutes / 1.2).roundToInt().coerceAtLeast(1)
            val lessons = totalMinutes / 15
            val hours = totalMinutes / 60.0
            val headline = when {
                hours >= 10.0 -> if (isEn) "A1 Language Track" else "A1 Temel Seviye Pratiği"
                lessons >= 1 -> if (isEn) "~$lessons Daily Lessons" else "~$lessons Günlük Dil Dersi"
                else -> if (isEn) "~$words New Words" else "~$words Yeni Kelime"
            }
            val desc = when {
                hours >= 10.0 -> {
                    if (isEn) "With your saved $formattedTime, you have unlocked time for foundational A1 language fluency!"
                    else "Kurtardığınız $formattedTime ile yeni bir dilde temel A1 pratiğini tamamlayacak serbest zaman açtınız!"
                }
                lessons >= 1 -> {
                    if (isEn) "With your saved $formattedTime, you have reclaimed time for ~$lessons daily language lessons."
                    else "Kurtardığınız $formattedTime ile ~$lessons tam günlük dil dersi yapacak odak cebinizde."
                }
                else -> {
                    if (isEn) "With your saved $formattedTime, you have earned time to learn ~$words new vocabulary words."
                    else "Kurtardığınız $formattedTime ile ~$words yeni yabancı kelime öğrenecek zaman kazandınız."
                }
            }
            val progress = when {
                hours >= 10.0 -> 1f
                lessons >= 1 -> (totalMinutes / 600.0).toFloat().coerceIn(0.05f, 1f)
                else -> (totalMinutes / 15.0).toFloat().coerceIn(0.05f, 1f)
            }
            val milestoneLabel = when {
                hours >= 10.0 -> if (isEn) "A1 Fluency Track Unlocked!" else "A1 Temel Seviye Aşıldı!"
                lessons >= 1 -> if (isEn) "Target: A1 Fluency Track (10 Hours)" else "Hedef: A1 Seviye Pratiği (10 saat)"
                else -> if (isEn) "Target: 1st Daily Lesson (15 Minutes)" else "Hedef: 1. Günlük Dil Dersi (15 Dakika)"
            }
            FocusImpactInsight(
                iconRes = R.drawable.ic_globe,
                title = if (isEn) "Language Learning" else "Dil Öğrenme",
                headline = headline,
                description = desc,
                footnote = if (isEn)
                    "* Standard: 1 word = ~1.2 Minutes, 1 daily lesson = 15 Minutes, A1 = 10 Hours."
                else
                    "* Standart: 1 kelime = ~1.2 Dakika, 1 günlük ders = 15 Dakika, A1 = 10 saat.",
                brandColor = category.brandColor,
                progress = progress,
                milestoneLabel = milestoneLabel
            )
        }

        FocusImpactCategory.WALK -> {
            val steps = totalMinutes * 110 // 110 steps/min
            val km = totalMinutes / 15.0
            val headline = when {
                km >= 1.0 -> {
                    val kmStr = if (km < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", km)
                    if (isEn) "~$kmStr km Walk (~$steps Steps)" else "~$kmStr km Yürüyüş (~$steps Adım)"
                }
                else -> {
                    val meters = totalMinutes * 65
                    if (isEn) "~$meters Meters (~$steps Steps)" else "~$meters Metre (~$steps Adım)"
                }
            }
            val desc = when {
                km >= 1.0 -> {
                    val kmStr = if (km < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", km)
                    if (isEn) "With your saved $formattedTime, you have earned healthy time for a ~$kmStr km brisk walk outdoors."
                    else "Kurtardığınız $formattedTime sayesinde açık havada ~$kmStr km tempolu yürüyüş yapacak sağlıklı bir vakit kazandınız."
                }
                else -> {
                    val meters = totalMinutes * 65
                    if (isEn) "With your saved $formattedTime, you have created a chance to walk ~$meters meters and refresh your mind."
                    else "Kurtardığınız $formattedTime ile ~$meters metre yürüyüş yapıp zihninizi tazeleyecek bir fırsat yarattınız."
                }
            }
            val progress = when {
                km >= 10.0 -> 1f
                km >= 1.0 -> (km / 10.0).toFloat().coerceIn(0.05f, 1f)
                else -> (totalMinutes / 15.0).toFloat().coerceIn(0.05f, 1f)
            }
            val milestoneLabel = when {
                km >= 10.0 -> if (isEn) "10 km City Walk Milestone Achieved!" else "10 km Şehir Yürüyüşü Tamamlandı!"
                km >= 1.0 -> if (isEn) "Target: 10 km City Walk" else "Hedef: 10 km Şehir Yürüyüşü"
                else -> if (isEn) "Target: 1 km Brisk Walk (15 Minutes)" else "Hedef: 1 km Tempolu Yürüyüş (15 Dakika)"
            }
            FocusImpactInsight(
                iconRes = R.drawable.ic_impact_walk,
                title = if (isEn) "Walking & Health" else "Yürüyüş & Sağlık",
                headline = headline,
                description = desc,
                footnote = if (isEn)
                    "* Standard: ~110 steps/Minute, 1 km = ~15 Minutes."
                else
                    "* Standart: ~110 adım/Dakika, 1 km = ~15 Dakika.",
                brandColor = category.brandColor,
                progress = progress,
                milestoneLabel = milestoneLabel
            )
        }

        FocusImpactCategory.MOVIE -> {
            val movies = totalMinutes / 105.0 // ~1.75 hours (105 min) per movie
            val headline = when {
                movies >= 1.0 -> {
                    val moviesStr = if (movies < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", movies)
                    if (isEn) "~$moviesStr Feature Films" else "~$moviesStr Uzun Metraj Film"
                }
                totalMinutes >= 45 -> if (isEn) "1 Documentary Episode" else "1 Bölüm Belgesel"
                totalMinutes >= 15 -> if (isEn) "1 Award-Winning Short Film" else "1 Ödüllü Kısa Film"
                else -> if (isEn) "Inspiring Culture Talk" else "İlham Verici Sanat & Kültür Konuşması"
            }
            val desc = when {
                movies >= 1.0 -> {
                    val moviesStr = if (movies < 1.15) "1" else String.format(java.util.Locale.US, "%.1f", movies)
                    if (isEn) "With your saved $formattedTime, you have unlocked relaxing time for ~$moviesStr masterpiece feature films!"
                    else "Kurtardığınız $formattedTime ile tam ~$moviesStr uzun metraj sinema filmi veya başyapıt izleyecek harika bir kültür vakti kazandınız!"
                }
                totalMinutes >= 45 -> {
                    if (isEn) "With your saved $formattedTime, you have earned quality focus time to enjoy 1 comprehensive documentary episode."
                    else "Kurtardığınız $formattedTime ile 1 tam bölüm ufuk açıcı belgesel izleyecek kaliteli bir kültür vakti kazandınız."
                }
                totalMinutes >= 15 -> {
                    if (isEn) "With your saved $formattedTime, you have gained time to watch an award-winning festival short film."
                    else "Kurtardığınız $formattedTime ile festival ödüllü kaliteli bir kısa film izleyecek sanatsal bir vakit kazandınız."
                }
                else -> {
                    if (isEn) "With your saved $formattedTime, you have gained focus time to watch an inspiring TED talk or culture mini-masterclass."
                    else "Kurtardığınız $formattedTime ile ufuk açıcı bir kültür/sanat videosu veya ilham verici bir TED konuşması izleyecek odak kazandınız."
                }
            }
            val progress = when {
                movies >= 1.0 -> ((totalMinutes % 105) / 105.0).toFloat().coerceIn(0.05f, 1f)
                totalMinutes >= 45 -> (totalMinutes / 105.0).toFloat().coerceIn(0.05f, 1f)
                totalMinutes >= 15 -> (totalMinutes / 45.0).toFloat().coerceIn(0.05f, 1f)
                else -> (totalMinutes / 15.0).toFloat().coerceIn(0.05f, 1f)
            }
            val milestoneLabel = when {
                movies >= 1.0 -> {
                    val nextMovie = (ceil(movies + 0.01)).toInt().coerceAtLeast(2)
                    if (isEn) "Target: $nextMovie Feature Films" else "Hedef: $nextMovie. Sinema Filmi"
                }
                totalMinutes >= 45 -> if (isEn) "Target: 1 Feature Film (105 Minutes)" else "Hedef: 1 Uzun Metraj Film (105 Dakika)"
                totalMinutes >= 15 -> if (isEn) "Target: 1 Documentary (45 Minutes)" else "Hedef: 1 Bölüm Belgesel (45 Dakika)"
                else -> if (isEn) "Target: 1 Short Film (15 Minutes)" else "Hedef: 1 Ödüllü Kısa Film (15 Dakika)"
            }
            FocusImpactInsight(
                iconRes = R.drawable.ic_impact_movie,
                title = if (isEn) "Cinema & Culture" else "Sinema & Kültür",
                headline = headline,
                description = desc,
                footnote = if (isEn)
                    "* Standard: 1 short film = ~15 Minutes, 1 documentary = ~45 Minutes, 1 movie = ~105 Minutes."
                else
                    "* Standart: 1 kısa film = ~15 Dakika, 1 belgesel = ~45 Dakika, 1 sinema filmi = ~105 Dakika.",
                brandColor = category.brandColor,
                progress = progress,
                milestoneLabel = milestoneLabel
            )
        }

        FocusImpactCategory.SKILL -> {
            val lessons = totalMinutes / 45
            val exercises = (totalMinutes / 10).coerceAtLeast(1)
            val hours = totalMinutes / 60.0
            val headline = when {
                hours >= 8.0 -> if (isEn) "Full Course Module" else "Tam Eğitim / Kurs Modülü"
                lessons >= 1 -> if (isEn) "~$lessons Skill Lessons" else "~$lessons Kapsamlı Beceri Dersi"
                else -> if (isEn) "~$exercises Practice Exercises" else "~$exercises Pratik Alıştırma"
            }
            val desc = when {
                hours >= 8.0 -> {
                    if (isEn) "With your saved $formattedTime, you have unlocked the dedicated focus to complete a full online course module!"
                    else "Kurtardığınız $formattedTime ile tam kapsamlı bir online beceri veya yazılım modülünü bitirecek muazzam bir odak elde ettiniz!"
                }
                lessons >= 1 -> {
                    if (isEn) "With your saved $formattedTime, you have built free time to complete ~$lessons comprehensive skill lessons."
                    else "Kurtardığınız $formattedTime ile ~$lessons tam beceri veya kodlama dersi tamamlayacak serbest zaman oluşturdunuz."
                }
                else -> {
                    if (isEn) "With your saved $formattedTime, you have gained focus time to solve ~$exercises practical exercises."
                    else "Kurtardığınız $formattedTime ile ~$exercises pratik alıştırma çözecek odak kazandınız."
                }
            }
            val progress = when {
                hours >= 8.0 -> 1f
                lessons >= 1 -> (totalMinutes / 480.0).toFloat().coerceIn(0.05f, 1f)
                else -> (totalMinutes / 45.0).toFloat().coerceIn(0.05f, 1f)
            }
            val milestoneLabel = when {
                hours >= 8.0 -> if (isEn) "Course Module Completed!" else "Tam Eğitim Modülü Tamamlandı!"
                lessons >= 1 -> if (isEn) "Target: Full Module 8 Hours" else "Hedef: Tam Eğitim Modülü (8 saat)"
                else -> if (isEn) "Target: 1 Full Lesson (45 Minutes)" else "Hedef: 1 Kapsamlı Ders (45 Dakika)"
            }
            FocusImpactInsight(
                iconRes = R.drawable.ic_impact_code,
                title = if (isEn) "Coding & Skills" else "Kodlama & Beceri",
                headline = headline,
                description = desc,
                footnote = if (isEn)
                    "* Standard: 1 exercise = ~10 Minutes, 1 lesson = ~45 Minutes, module = 8 Hours."
                else
                    "* Standart: 1 alıştırma = ~10 Dakika, 1 ders = ~45 Dakika, modül = 8 saat.",
                brandColor = category.brandColor,
                progress = progress,
                milestoneLabel = milestoneLabel
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
    val fallbackTopApp: String = "Instagram"
) {
    val effectiveInsta: Int
        get() = if (instagramBlocks == 0 && tiktokBlocks == 0 && youtubeBlocks == 0 && totalBlocks > 0 && fallbackTopApp == "Instagram") totalBlocks else instagramBlocks

    val effectiveTiktok: Int
        get() = if (instagramBlocks == 0 && tiktokBlocks == 0 && youtubeBlocks == 0 && totalBlocks > 0 && fallbackTopApp == "TikTok") totalBlocks else tiktokBlocks

    val effectiveYt: Int
        get() = if (instagramBlocks == 0 && tiktokBlocks == 0 && youtubeBlocks == 0 && totalBlocks > 0 && fallbackTopApp == "YouTube") totalBlocks else youtubeBlocks

    val topAppColor: Color
        get() = when {
            effectiveInsta >= effectiveTiktok && effectiveInsta >= effectiveYt && effectiveInsta > 0 -> Color(0xFF833AB4) // Instagram Purple
            effectiveTiktok >= effectiveInsta && effectiveTiktok >= effectiveYt && effectiveTiktok > 0 -> Color(0xFF00F2FE) // TikTok Neon Cyan
            effectiveYt >= effectiveInsta && effectiveYt >= effectiveTiktok && effectiveYt > 0 -> Color(0xFFFF0000) // YouTube Shorts Bright Red
            totalBlocks > 0 -> Color(0xFF00FF87) // Default active Green
            else -> Color(0xFF1E2A40)
        }

    val topAppName: String
        get() = when {
            effectiveInsta >= effectiveTiktok && effectiveInsta >= effectiveYt && effectiveInsta > 0 -> "Instagram"
            effectiveTiktok >= effectiveInsta && effectiveTiktok >= effectiveYt && effectiveTiktok > 0 -> "TikTok"
            effectiveYt >= effectiveInsta && effectiveYt >= effectiveTiktok && effectiveYt > 0 -> "YouTube"
            else -> "Engelleme Yok"
        }
}

// ------------------------------------------
// 4. SEKME: İLERLEME DURUMU & GERÇEK ZAMANLI İSTATİSTİKLER (ESKİ ROZETLER)
// ------------------------------------------
@Composable
fun ProgressStatusScreen(prefs: android.content.SharedPreferences) {
    val isEn = getAppLanguage(prefs) == "en"
    var totalBlocks by remember { mutableIntStateOf(prefs.getInt("total_blocks", 0)) }
    var streakDays by remember { mutableIntStateOf(prefs.getInt("streak_days", 0)) }
    var blocksInstagram by remember { mutableIntStateOf(prefs.getInt("blocks_instagram", 0)) }
    var blocksTiktok by remember { mutableIntStateOf(prefs.getInt("blocks_tiktok", 0)) }
    var blocksYoutube by remember { mutableIntStateOf(prefs.getInt("blocks_youtube", 0)) }
    var weeklyDetails by remember { mutableStateOf(List(7) { DayBlockDetail("", "", 0, 0, 0, 0) }) }
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
            totalBlocks = prefs.getInt("total_blocks", 0)
            streakDays = prefs.getInt("streak_days", 0)
            blocksInstagram = prefs.getInt("blocks_instagram", 0)
            blocksTiktok = prefs.getInt("blocks_tiktok", 0)
            blocksYoutube = prefs.getInt("blocks_youtube", 0)

            val globalTopApp = when {
                blocksInstagram >= blocksTiktok && blocksInstagram >= blocksYoutube -> "Instagram"
                blocksTiktok >= blocksInstagram && blocksTiktok >= blocksYoutube -> "TikTok"
                else -> "YouTube"
            }

            val calendar = java.util.Calendar.getInstance()
            calendar.firstDayOfWeek = java.util.Calendar.MONDAY
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            
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

                val dayInsta = if (dayTotal > 0 && rawInsta == 0 && rawTiktok == 0 && rawYt == 0) (dayTotal * 4) / 10 else rawInsta
                val dayTiktok = if (dayTotal > 0 && rawInsta == 0 && rawTiktok == 0 && rawYt == 0) (dayTotal * 4) / 10 else rawTiktok
                val dayYt = if (dayTotal > 0 && rawInsta == 0 && rawTiktok == 0 && rawYt == 0) (dayTotal - dayInsta - dayTiktok) else rawYt

                newWeeklyDetails.add(
                    DayBlockDetail(
                        dayName = dayShortNames[i],
                        dateStr = dayStr,
                        totalBlocks = dayTotal,
                        instagramBlocks = dayInsta,
                        tiktokBlocks = dayTiktok,
                        youtubeBlocks = dayYt,
                        fallbackTopApp = globalTopApp
                    )
                )
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            weeklyDetails = newWeeklyDetails

            delay(1000)
        }
    }

    val savedTimeStr = formatSavedFocusTime(totalBlocks, isEn)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Left Accent Header Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00F2FE))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isEn) "Analytics & Reports" else "Analiz & Raporlar",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE)
                )
                Text(
                    text = if (isEn) "Your saved focus time and block summary" else "Kurtarılan odak süreniz ve engelleme özetiniz",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. KATEGORİ: ODAK & ENGELLEME GENEL BAKIŞI
        Text(
            text = if (isEn) "FOCUS RECOVERY OVERVIEW" else "ODAK & ENGELLEME GENEL BAKIŞI",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Modern Hero Overview Card (Seamless 3-metric dashboard)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Metric 1: Saved Time
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF00FF87).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_target),
                                contentDescription = null,
                                tint = Color(0xFF00FF87),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = savedTimeStr,
                            fontSize = if (savedTimeStr.length > 12) 12.5.sp else 14.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FF87),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Saved Time" else "Kurtarılan Süre",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0xFF1E2A40).copy(alpha = 0.7f))
                    )

                    // Metric 2: Total Blocks
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF00F2FE).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield),
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isEn) "$totalBlocks Times" else "$totalBlocks Defa",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00F2FE),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Traps Broken" else "Tuzak Kırıldı",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0xFF1E2A40).copy(alpha = 0.7f))
                    )

                    // Metric 3: Streak
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFFFFB703).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tier_fire),
                                contentDescription = null,
                                tint = Color(0xFFFFB703),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isEn) "$streakDays Days" else "$streakDays Gün",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFB703),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isEn) "Active Streak" else "Aktif Seri",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subtle micro footnote inside card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141C2E).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tier_spark),
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEn) "Each block saves ~30-45s of video time and restores dopamine focus."
                               else "Her engelleme ortalama ~30-45 sn video süresini ve kaydırma döngüsünü durdurur.",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.75f),
                        lineHeight = 14.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 2. KATEGORİ: HAFTALIK ENGELLEME AKTİVİTESİ
        Text(
            text = if (isEn) "WEEKLY BLOCK ACTIVITY" else "HAFTALIK ENGELLEME AKTİVİTESİ",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        CyberWeeklyPillarsChart(isEn = isEn, weeklyDetails = weeklyDetails)

        Spacer(modifier = Modifier.height(22.dp))

        // 3. KATEGORİ: BAŞARIMLAR & KADEMELER
        Text(
            text = if (isEn) "ACHIEVEMENTS & TIERS" else "BAŞARIMLAR & KADEMELER",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        FocusJourneyOverviewCard(
            streakDays = streakDays,
            totalBlocks = totalBlocks,
            isEn = isEn,
            onOpenJourney = { showJourneyDialog = true },
            prefs = prefs
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 4. KATEGORİ: KAZANILAN ODAK DEĞERİ
        Text(
            text = if (isEn) "RECLAIMED FOCUS VALUE" else "KAZANILAN ODAK DEĞERİ",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        InteractiveFocusImpactCard(totalBlocks = totalBlocks, isEn = isEn)

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun InteractiveFocusImpactCard(totalBlocks: Int, isEn: Boolean) {
    var selectedCategory by remember { mutableStateOf(FocusImpactCategory.BOOK) }
    val impact = remember(selectedCategory, totalBlocks, isEn) {
        getFocusCategoryImpact(selectedCategory, totalBlocks, isEn)
    }
    val savedTimeStr = formatSavedFocusTime(totalBlocks, isEn)

    val activeColor by animateColorAsState(
        targetValue = selectedCategory.brandColor,
        animationSpec = tween(durationMillis = 350),
        label = "activeColorAnim"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = impact.progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 1. Üst Başlık & Serbest Zaman Vurgusu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(activeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hourglass),
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEn) "WHAT YOUR RECLAIMED TIME UNLOCKS" else "KAZANDIĞINIZ ODAKLA NELER MÜMKÜN?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = if (isEn) "Free time gained by breaking the scroll loop ($savedTimeStr):" else "Döngüyü kırdıkça kazandığınız serbest zaman ($savedTimeStr):",
                        fontSize = 10.5.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Teselli & Motivasyon Bilgilendirme Rozeti
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141C2E).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEn)
                        "You're using AwayDoomscrollin' now! Every block is a win, and your focus will steadily grow stronger over time."
                    else
                        "Artık AwayDoomscrollin' kullanıyorsunuz! Her engelleme bir kazanımdır, zamanla odağınız güçlenecek.",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 14.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Kategori Seçim Hapları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FocusImpactCategory.values().forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) cat.brandColor.copy(alpha = 0.18f) else Color(0xFF131A29),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) cat.brandColor else Color(0xFF1E2A40).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = cat.iconRes),
                                contentDescription = null,
                                tint = if (isSelected) cat.brandColor else Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isEn) cat.titleEn else cat.titleTr,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) cat.brandColor else Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Showcase Vitrin Paneli
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131A29),
                border = androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Avatar & Hero Title Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(activeColor.copy(alpha = 0.15f))
                                .border(1.dp, activeColor.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = impact.iconRes),
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = activeColor.copy(alpha = 0.15f),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    text = impact.title.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = activeColor,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = impact.headline,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Açıklayıcı Metin
                    Text(
                        text = impact.description,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 16.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Hedef / Kilometre Taşı İlerleme Çubuğu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = impact.milestoneLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "%${(animatedProgress * 100).toInt()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeColor
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF0F1523))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            activeColor.copy(alpha = 0.7f),
                                            activeColor
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Dipnot
            Text(
                text = impact.footnote,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun CyberWeeklyPillarsChart(isEn: Boolean = false, weeklyDetails: List<DayBlockDetail>) {
    val maxVal = (weeklyDetails.maxOfOrNull { it.totalBlocks } ?: 1).coerceAtLeast(1)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // 1. Üst Açıklama
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEn) "Daily Breakdown" else "Günlük Dağılım",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isEn) "Tap bars for details" else "Detaylar için sütuna dokunun",
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Renk Açıklama Rozetleri (Minimalist Dot Legend)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7 Kule Yan Yana (Tam Hizanlama & Simetri)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyDetails.forEachIndexed { index, detail ->
                    val total = detail.totalBlocks
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

                                    val instaRatio = instaCount.toFloat() / total
                                    val tiktokRatio = tiktokCount.toFloat() / total
                                    val ytRatio = ytCount.toFloat() / total

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
                                        text = if (isEn) "${selectedDetail.dayName} Breakdown" else "${selectedDetail.dayName} Günlük Dağılım",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = selectedDetail.topAppColor
                                    )
                                }

                                val daySavedTime = formatSavedFocusTime(selectedDetail.totalBlocks, isEn)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF00FF87).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_hourglass),
                                            contentDescription = null,
                                            tint = Color(0xFF00FF87),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = daySavedTime,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00FF87)
                                        )
                                    }
                                }
                            }

                            if (selectedDetail.totalBlocks > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val total = selectedDetail.totalBlocks
                                    val insta = selectedDetail.effectiveInsta
                                    val tiktok = selectedDetail.effectiveTiktok
                                    val yt = selectedDetail.effectiveYt

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
                                            Text(if (isEn) "$insta Times ($pct%)" else "$insta Defa (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                                            Text(if (isEn) "$tiktok Times ($pct%)" else "$tiktok Defa (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                                            Text(if (isEn) "$yt Times ($pct%)" else "$yt Defa (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isEn) "No blocks recorded for this day." else "Bu gün hiç engelleme kaydedilmedi.",
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
