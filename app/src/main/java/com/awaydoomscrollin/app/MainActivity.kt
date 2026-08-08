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
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Remote Config & Telemetry: Uzaktan kuralları çek ve anonim analitiği kontrol et
        RemoteRuleManager.fetchRulesAsync(this)
        TelemetryManager.sendTelemetryAsync(this)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
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
    val resolved = if (deviceLang.startsWith("tr")) "tr" else "tr"
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
                            (if (isEn) "Finish & Start 🛡️" else "Kurulumu Tamamla 🛡️") 
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
        // HİKAYE VE GELİŞTİRİCİ MEKTUBU KARTI
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Başlık Alanı
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🤝", fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEn) "I Am One of You." else "Ben de Sizden Biriyim.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEn) "A Letter from Resolve Community" else "Resolve Community'den Bir Mektup",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bölüm 1: Hikayenin Başlangıcı
                Text(
                    text = if (isEn) "Hello, I am an independent developer behind Resolve Community and creator of AwayDoomscrollin'." else "Merhaba, ben Resolve Community adına AwayDoomscrollin' uygulamasını geliştiren bağımsız bir geliştiriciyim.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn) 
                        "Ever since the COVID-19 pandemic, almost my entire day was spent in front of computer and phone screens. Life outside was complicated; playing games or scrolling through feeds for hours felt more enjoyable and safe. The real reason was escaping reality."
                    else 
                        "COVID-19 pandemisinden beri günümün neredeyse tamamı bilgisayar ve telefon ekranı karşısında geçiyordu. Dışarıdaki hayat karmaşıktı; sosyalleşmek yerine ekran başında oyun oynamak veya saatlerce akış kaydırmak daha keyifli ve güvenli geliyordu. Sanırım asıl sebebim, gerçek hayattan kaçmaktı.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bölüm 2: Farkındalık & Sağlık Etkileri
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isEn) "⚠️ Painful Truth I Faced:" else "⚠️ Yüzleştiğim Acı Gerçek:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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

                Spacer(modifier = Modifier.height(14.dp))

                // Bölüm 3: Çözüm Arayışı
                Text(
                    text = if (isEn) "Why Did I Build This App Under Resolve Community?" else "Bu Uygulamayı Neden Geliştirdim?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn) 
                        "Even when playing PC games, my eyes would wander to my phone screen, watching Reels and jumping around. I built AwayDoomscrollin' under Resolve Community to solve this addiction. I use it actively myself and can confidently say it's much more effective than other well-being apps."
                    else 
                        "Bilgisayarda oyun oynarken bile gözüm telefona kayıyor, bir yandan Reels izleyip oradan oraya hopluyordum. Bu bağımlılığı çözmek için Resolve Community çatısı altında AwayDoomscrollin'ı geliştirdim. Şu an kendim de aktif kullanıyorum ve diğer tüm well-being uygulamalarından çok daha etkili olduğunu rahatlıkla söyleyebilirim.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bölüm 4: Algoritmanın Tuzağı (Madde Madde)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF85149).copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isEn) "🎯 How the Algorithm Trap Works" else "🎯 Algoritmanın Tuzağı Nasıl Çalışır?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF85149)
                        )
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
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bölüm 5: Çağrı & Kapanış
                Text(
                    text = if (isEn) 
                        "My sole purpose is to study productively and make quality time for myself. Once you break free from scrolling addiction, you'll see how fruitful life becomes.\n\nNever stop trying even if you fail sometimes! (I failed many times too, but this is my 2nd attempt at not giving up, and this time we'll succeed together...)"
                    else 
                        "Tek amacım verimli ders çalışabilmek ve kendime kaliteli zaman ayırmak. Ekran ve kaydırma bağımlılığından bir kez kurtulduğunuzda, hayatınızın ne kadar verimli geçtiğini göreceksiniz.\n\nHer ne kadar bazen başarısız olsanız da çabalamaktan asla vazgeçmeyin! (Ben de defalarca başarısız oldum, ancak bu benim 2. pes etmeyişim ve bu sefer birlikte başaracağız...)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 19.sp
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
            text = if (isEn) "First Opened Video Is Free,\nScrolls Are Blocked Instantly!" else "İlk Açılan Video Serbesttir,\nKaydırdığın An Engeller!",
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
                Text(
                    text = if (isEn) "⚙️ Full Working Logic:" else "⚙️ Tam Çalışma Mantığı:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEn) 
                        "• The first Reels video or post you open can be watched freely.\n• The instant you SWIPE UP with your finger, the scrolling trap is detected.\n• The system opens Settings, Force Stops Instagram, and immediately throws you to the Home Screen." 
                    else 
                        "• Karşınıza çıkan ilk Reels veya Gönderi serbestçe izlenebilir.\n• Ancak parmağınızla yukarı KAYDIRILDIĞI AN tuzak algılanır.\n• Sistem Ayarlar'a girip Instagram'ı Zorla Durdurur ve sizi anında Ana Ekrana fırlatır.",
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
            Text(
                "👆",
                fontSize = 32.sp,
                modifier = Modifier.alpha(alphaVal)
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
        Text("🎮 ${if (isEn) "Interactive Simulator" else "İnteraktif Simülatör"}", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                                    Text("❤️", fontSize = 18.sp, modifier = Modifier.clickable { simulatorState = SimulatorState.SAFE_ZONE })
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
                                        Text(if (isEn) "👇 Try scrolling down!" else "👇 Akışı kaydırmayı dene!", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                
                                // Bottom Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(Color(0xFF000000)),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🏠", fontSize = 20.sp)
                                    Text("🔍", fontSize = 20.sp, modifier = Modifier.clickable { simulatorState = SimulatorState.SAFE_ZONE })
                                    
                                    // Guided Reels Button with Bouncing Hand
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("▶️", fontSize = 20.sp, modifier = Modifier.clickable { simulatorState = SimulatorState.BLOCKED_REELS })
                                        Text("👆", fontSize = 24.sp, modifier = Modifier.offset(x = 12.dp, y = handOffsetY.dp).padding(top = 28.dp))
                                    }
                                    
                                    Text("👤", fontSize = 20.sp, modifier = Modifier.clickable { simulatorState = SimulatorState.SAFE_ZONE })
                                }
                            }
                        }
                        SimulatorState.BLOCKED_REELS -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                            ) {
                                Text("🚫", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    if (isEn) "Reels Blocked!" else "Reels Engellendi!",
                                    color = Color(0xFFF85149),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isEn) "Entering Reels forces an instant close. We break the infinite scrolling loop at its root." 
                                    else "Reels sekmesine girdiğiniz an uygulama kapatılır. Sonsuz kaydırma döngüsünü, daha başlamadan kökünden kırıyoruz.",
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
                                Text("🚫", fontSize = 48.sp)
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
                                Text("✅", fontSize = 48.sp)
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
                    Text("⚙️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEn) "ACTIVE SHIELD (WHAT IT CAN DO)" else "AKTİF KALKAN (YAPABİLDİKLERİ)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "Allows the first opened video/post, blocks the instant you scroll." else "İlk açılan videoya veya gönderiye izin verir, kaydırıldığı an algılar.")
                MatrixBullet(if (isEn) "Force stops the app from Settings ruthlessly." else "Uygulamayı Ayarlar'dan acımasızca zorla durdurur.")
                MatrixBullet(if (isEn) "Throws you to Home Screen to pull you out of the scroll trance." else "Sizi Ana Ekrana fırlatarak transtan çıkarır.")
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
                    Text("🛡️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEn) "PRIVACY GUARANTEE (WHAT IT CANNOT DO)" else "GİZLİLİK GARANTİSİ (YAPAMADIKLARI)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(if (isEn) "100% Offline: Your data never leaves your device." else "Sıfır İnternet İzni: Verileriniz cihaz dışına çıkamaz.", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT record your messages, passwords, or photos." else "Mesajlarınızı, şifrelerinizi ve fotoğraflarınızı kaydetmez.", isNegative = true)
                MatrixBullet(if (isEn) "Does NOT drain battery or memory in the background." else "Arka planda pilinizi ve belleğinizi tüketmez.", isNegative = true)
            }
        }
    }
}

@Composable
fun MatrixBullet(text: String, isNegative: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = if (isNegative) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
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
    var isInstaEnabled by remember { mutableStateOf(prefs.getBoolean("is_instagram_enabled", true)) }
    var isTiktokEnabled by remember { mutableStateOf(prefs.getBoolean("is_tiktok_enabled", true)) }
    var isYoutubeEnabled by remember { mutableStateOf(prefs.getBoolean("is_youtube_enabled", true)) }
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
                Text("📱", fontSize = 32.sp)
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
            onToggle = { enabled ->
                isInstaEnabled = enabled
                prefs.edit().putBoolean("is_instagram_enabled", enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. TIKTOK COMPACT CARD
        CompactOnboardingAppCard(
            appName = "TikTok",
            subtitle = if (isEn) "Short Video Feed (BETA)" else "Kısa Video Akışı (BETA)",
            iconRes = R.drawable.ic_tiktok,
            brandColor = Color(0xFF00F2FE),
            isEnabled = isTiktokEnabled,
            isBeta = true,
            onToggle = { enabled ->
                isTiktokEnabled = enabled
                prefs.edit().putBoolean("is_tiktok_enabled", enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. YOUTUBE SHORTS COMPACT CARD
        CompactOnboardingAppCard(
            appName = "YouTube Shorts",
            subtitle = if (isEn) "Shorts Screen Only (BETA)" else "Shorts Ekranı (BETA)",
            iconRes = R.drawable.ic_youtube,
            brandColor = Color(0xFFFF0055),
            isEnabled = isYoutubeEnabled,
            isBeta = true,
            onToggle = { enabled ->
                isYoutubeEnabled = enabled
                prefs.edit().putBoolean("is_youtube_enabled", enabled).apply()
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
                    Text("ℹ️", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Protection Scope Info (Safe Areas)" else "Kalkan Kapsamı Bilgisi (Güvenli Alanlar)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text("→", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                    Text(
                        text = if (isEn) "📊 Anonymous Analytics (Telemetry)" else "📊 Anonim Geliştirici Analitiği",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEn) "Helps us improve shielding by sharing total block counts anonymously." else "Uygulamayı geliştirmemize yardımcı olmak için anonim engelleme verilerini paylaşır.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
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
                "• Instagram: Reels feeds and infinite scroll streams are blocked immediately. DMs (Messages) and Comment sections remain 100% safe and accessible.\n\n" +
                "• TikTok (BETA): Video scrolling feed is detected and closed. Inbox and Direct Messages remain safe.\n\n" +
                "• YouTube Shorts (BETA): Only YouTube Shorts video screen is blocked. Normal video playback and Search function remain untouched."
            } else {
                "AwayDoomscrollin' Koruması:\n\n" +
                "• Instagram: Reels ve sonsuz akışlar algılandığında kalkan anında devreye girer. DM (Mesajlar) ve Yorumlar alanı %100 güvenlidir.\n\n" +
                "• TikTok (BETA): Kısa video akışı algılanıp kapatılır. Gelen kutusu ve mesajlar güvenlidir.\n\n" +
                "• YouTube Shorts (BETA): Sadece Shorts ekranı engellenir. Normal video izleme ve Arama aramaları kesinlikle engellenmez."
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
                Text("🛡️", fontSize = 34.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isEn) "Activate Your Guardian" else "Gardiyanını Aktif Et",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isEn) "Complete the 3 permissions below for shield protection:" else "Sistemin durdurma yapabilmesi için aşağıdaki 3 izni tamamlayın:",
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
                    Text(
                        text = if (isAccessibilityActive) "✓" else "1",
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
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
                    Text(
                        if (isEn) (if (isAccessibilityActive) "Permission Granted ✓" else "1. Grant Accessibility Permission") else (if (isAccessibilityActive) "İzin Verildi ✓" else "1. Erişilebilirlik İznini Aç"),
                        fontWeight = FontWeight.Bold
                    )
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
                    Text(
                        text = if (isIgnoringBattery) "✓" else "2",
                        fontWeight = FontWeight.Bold,
                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
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
                    Text(
                        if (isEn) (if (isIgnoringBattery) "Restriction Removed ✓" else "2. Disable Battery Restrictions") else (if (isIgnoringBattery) "Kısıtlama Kaldırıldı ✓" else "2. Pil Optimizasyonunu Kaldır"),
                        fontWeight = FontWeight.Bold
                    )
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
                        Text(
                            text = if (hasNotificationPermission) "✓" else "3",
                            fontWeight = FontWeight.Bold,
                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
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
                        Text(
                            if (isEn) (if (hasNotificationPermission) "Notifications Allowed ✓" else "3. Allow Notifications") else (if (hasNotificationPermission) "Bildirimlere İzin Verildi ✓" else "3. Bildirimlere İzin Ver"),
                            fontWeight = FontWeight.Bold
                        )
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
                Text(
                    text = if (isEn) "🔒 Privacy & Legal Compliance" else "🔒 Gizlilik Beyanı ve Sözleşmeler",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00F2FE)
                )
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
                        Text(
                            text = if (isEn) "Privacy Policy 🔗" else "Gizlilik Politikası 🔗",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE)
                        )
                    }
                    Text("•", fontSize = 12.sp, color = Color.Gray)
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/terms"))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isEn) "Terms of Service 🔗" else "Kullanım Şartları 🔗",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE)
                        )
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
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE) }
    val isEn = getAppLanguage(prefs) == "en"
    
    var feedbackCategory by remember { mutableStateOf(if (isEn) "🐛 Bug" else "🐛 Hata") }
    var feedbackText by remember { mutableStateOf("") }
    val isFormValid = feedbackText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💬", fontSize = 20.sp)
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
                    val categories = if (isEn) listOf("🐛 Bug", "💡 Idea", "💬 General") else listOf("🐛 Hata", "💡 Öneri", "💬 Genel")
                    categories.forEach { cat ->
                        val isSelected = feedbackCategory == cat
                        Surface(
                            onClick = { feedbackCategory = cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF070A12) else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
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
                Text(
                    text = if (isEn) "🔒 Device info ($manufacturer $model, Android ${Build.VERSION.RELEASE}) will be attached automatically." else "🔒 Cihaz bilgisi ($manufacturer $model, Android ${Build.VERSION.RELEASE}) otomatik eklenecektir.",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isFormValid) return@Button

                    val subject = "AwayDoomscrollin' [$feedbackCategory] - $manufacturer $model"
                    val body = if (isEn) {
                        "Category: $feedbackCategory\n\nUser Message:\n$feedbackText\n\n------------------------------\nDevice Info: $manufacturer $model (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\nApp Version: v1.0.0"
                    } else {
                        "Kategori: $feedbackCategory\n\nKullanıcı Mesajı:\n$feedbackText\n\n------------------------------\nCihaz Bilgisi: $manufacturer $model (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})\nUygulama Sürümü: v1.0.0"
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
                            if (isEn) "📧 Opening email client..." else "📧 E-posta uygulamanız açılıyor...",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        prefs.edit().putLong("last_feedback_time_ms", System.currentTimeMillis()).apply()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            if (isEn) "❌ Mail client not found. Please email support@awaydoomscrollin.com" else "❌ E-posta uygulaması bulunamadı. Lütfen support@awaydoomscrollin.com adresine yazın.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = isFormValid,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isEn) "Send Email 📧" else "E-Posta Gönder 📧",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF070A12),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
fun AboutScreen(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf<Boolean>(false) }

    var currentLang by remember { mutableStateOf<String>(getAppLanguage(prefs)) }
    val isEn = currentLang == "en"

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
                        .width(3.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF00F2FE))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEn) "About & Contact" else "Hakkında & İletişim",
                        fontSize = 24.sp,
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

            // 1. UYGULAMA KÜNYESİ KARTI
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "AwayDoomscrollin'",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) "Version 1.0.0 • Open Source" else "Sürüm 1.0.0 • Açık Kaynak",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                            text = "🇹🇷 Türkçe",
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
                            text = "🇬🇧 English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEn) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RESMİ WEB PORTALLARI KARTI (awaydoomscrollin.com & resolvecommunity.com)
            val infiniteTransition = rememberInfiniteTransition(label = "WhiteGlow")
            val whiteGlowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "WhiteGlowAlpha"
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = whiteGlowAlpha)),
                tonalElevation = 8.dp,
                shadowElevation = 6.dp,
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
                                Text("🌐", fontSize = 18.sp)
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
                        Text("↗", fontSize = 15.sp, color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold)
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
                                Text("👥", fontSize = 18.sp)
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
                        Text("↗", fontSize = 15.sp, color = Color(0xFF00FF87), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. ANA EYLEM KARTI (Şık ve Tek Parça)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // ÖNCELİKLİ BUTON: GERİ BİLDİRİM & HATA BİLDİR
                    Button(
                        onClick = { showFeedbackDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color(0xFF070A12))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💡", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEn) "Send Feedback & Bug Report" else "Geri Bildirim & Hata Bildir",
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
                                Text("🔒", fontSize = 13.sp)
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
                                Text("📜", fontSize = 13.sp)
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

                    // ÜÇÜNCÜL BUTON: GITHUB
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
                            Text("🐙", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEn) "GitHub Source Code" else "GitHub Açık Kaynak",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. TELEMETRİ / ANONİM GELİŞTİRİCİ ANALİTİĞİ KARTI
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
                        Text(
                            text = if (isEn) "📊 Anonymous Telemetry" else "📊 Anonim Kullanım Telemetrisi",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) "Helps developer optimize blocking rules by sending anonymous usage stats." else "Engelleme algoritmalarını iyileştirmemiz için anonim kullanım verilerini paylaşır.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
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
                    text = "© 2026 Resolve Community • AwayDoomscrollin'",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isEn) "Developed by Resolve Community" else "Resolve Community Tarafından Geliştirildi",
                    fontSize = 10.5.sp,
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
    
    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "app_language") {
                currentLang = getAppLanguage(sharedPreferences)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val isEn = currentLang == "en"

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
                        icon = "🏠",
                        label = if (isEn) "Home" else "Ana Sayfa",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        icon = "⚙️",
                        label = if (isEn) "Apps" else "Uygulamalar",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        icon = "📈",
                        label = if (isEn) "Progress" else "İlerleme",
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        icon = "ℹ️",
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
        }
    }
}

@Composable
fun CyberNavItem(
    modifier: Modifier = Modifier,
    icon: String,
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
            Text(
                text = icon,
                fontSize = if (isSelected) 18.sp else 16.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
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
fun CyberRectangularShieldPanel(
    isEn: Boolean = false,
    isAccessibilityActive: Boolean,
    totalBlocks: Int,
    savedTimeStr: String,
    onActivateClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LedPulse")
    val ledAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ledAlpha"
    )

    val mainColor = if (isAccessibilityActive) Color(0xFF00FF87) else Color(0xFFFF0055)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(2.dp, mainColor),
        tonalElevation = 10.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Pulsing LED + Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(if (isAccessibilityActive) ledAlpha else 1f)
                        .background(color = mainColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isEn) 
                        (if (isAccessibilityActive) "🛡️ SYSTEM SHIELD ACTIVE" else "⚠️ SYSTEM SHIELD INACTIVE") 
                    else 
                        (if (isAccessibilityActive) "🛡️ SİSTEM KORUMASI AKTİF" else "⚠️ SİSTEM KORUMASI İNAKTİF"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = mainColor,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divider line with subtle glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(mainColor.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isAccessibilityActive) {
                // Integrated Stats Inside Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isEn) "Time Saved" else "Kurtarılan Zaman",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = savedTimeStr,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FF87)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isEn) "Total Blocks" else "Toplam Engelleme",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isEn) "$totalBlocks Times" else "$totalBlocks Defa",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00F2FE)
                        )
                    }
                }
            } else {
                Text(
                    text = if (isEn) 
                        "Enable accessibility permission to automatically block Reels/Shorts traps." 
                    else 
                        "Otomatik engelleme ve Reels/Shorts tuzaklarını durdurmak için erişilebilirlik iznini aktifleştirin.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActivateClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF0055),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isEn) "ACTIVATE SHIELD" else "KALKANI AKTİFLEŞTİR", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun FieryGlowingStreakBadge(streakDays: Int, isEn: Boolean) {
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

    val (badgeColor, emojiIcon) = when {
        streakDays == 0 -> Pair(Color(0xFF00F2FE), "🛡️")       // Fresh Shield Cyan
        streakDays in 1..3 -> Pair(Color(0xFFFF5500), "🔥")    // Fiery Orange
        streakDays in 4..6 -> Pair(Color(0xFFFFB700), "⚡")    // Electric Gold Yellow
        streakDays in 7..13 -> Pair(Color(0xFF00FF87), "🟢")   // Cyber Emerald
        streakDays in 14..20 -> Pair(Color(0xFFBD00FF), "🔮")  // Overdrive Purple
        streakDays in 21..29 -> Pair(Color(0xFFFFD700), "👑")  // Legendary Solar Gold
        else -> Pair(Color(0xFFFF0055), "🌌")                 // Mythic Crimson Master (30+ Days)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = glowAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emojiIcon, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isEn) "$streakDays DAY" else "$streakDays GÜN",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = badgeColor
            )
        }
    }
}

// ------------------------------------------
// 1. SEKME: ANA SAYFA (HOME SCREEN)
// ------------------------------------------
@Composable
fun HomeScreen(onReopenOnboarding: () -> Unit, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val (isAccessibilityActive, isIgnoringBattery) = rememberPermissionStatus()
    val isEn = getAppLanguage(prefs) == "en"
    var totalBlocks by remember { mutableIntStateOf(prefs.getInt("total_blocks", 0)) }
    
    LaunchedEffect(Unit) {
        while(true) {
            totalBlocks = prefs.getInt("total_blocks", 0)
            kotlinx.coroutines.delay(2000)
        }
    }

    val savedMins = totalBlocks * 2
    val savedTimeStr = if (isEn) 
        (if (savedMins >= 60) "${savedMins / 60}h ${savedMins % 60}m" else "$savedMins Min") 
    else 
        (if (savedMins >= 60) "${savedMins / 60}s ${savedMins % 60}dk" else "$savedMins Dk")
    val currentLevel = (totalBlocks / 10) + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ultra-Sade Tek Satır Header: [Logo 32dp] + Away (Beyaz) + Doomscrollin' (Cyan) + Fiery Streak Badge (Top Right)
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
                        .size(32.dp)
                        .clip(CircleShape)
                )

                // Soft White Vertical Divider Line
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color.White.copy(alpha = 0.4f))
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Away",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Doomscrollin'",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00F2FE)
                        )
                    }
                    Text(
                        text = "by Resolve Community",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Compact Dynamic Fiery & Glowing Streak Badge (Top Right)
            FieryGlowingStreakBadge(streakDays = streakDays, isEn = isEn)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Integrated Cyber Rectangular Shield Panel
        CyberRectangularShieldPanel(
            isEn = isEn,
            isAccessibilityActive = isAccessibilityActive,
            totalBlocks = totalBlocks,
            savedTimeStr = savedTimeStr,
            onActivateClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // In-App Update Banner (GitHub Releases / F-Droid Update Check)
        val remoteConfig = remember { RemoteRuleManager.getConfig(context) }
        val currentVersionCode = 1

        if (remoteConfig.latestVersionCode > currentVersionCode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
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
                            Text("🚀", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "New Version ${remoteConfig.latestVersionName} Available!" else "Yeni Sürüm ${remoteConfig.latestVersionName} Mevcut!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF00F2FE)
                            )
                        }
                        if (remoteConfig.updateChangelog.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = remoteConfig.updateChangelog,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteConfig.updateUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE))
                    ) {
                        Text(
                            text = if (isEn) "Update" else "Güncelle",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF070A12),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // ⚡ Günün Kritik Saati Kartı
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

        if (peakHour.first >= 0 && peakHour.second >= 1) {
            Spacer(modifier = Modifier.height(16.dp))
            val h = peakHour.first
            val cnt = peakHour.second
            val savedMinsH = cnt * 2
            val timeRange = "${h.toString().padStart(2, '0')}:00 - ${(h + 1).toString().padStart(2, '0')}:00"
            val peakMsg = if (isEn)
                "⚡ Peak hour today: $timeRange — $cnt blocks, ~${savedMinsH} min saved!"
            else
                "⚡ Bugünün zirve saati: $timeRange — $cnt engelleme, ~${savedMinsH} dk kurtarıldı!"
            val warningMsg = if (isEn) {
                when {
                    h in 22..23 || h == 0 -> "Late-night scroll risk. Protect your sleep! 🌙"
                    h in 6..9 -> "Morning scroll trap spotted. Start fresh! ☀️"
                    h in 12..14 -> "Lunch break vulnerability. Stay sharp! 💪"
                    else -> "Watch out for this hour tomorrow. 🎯"
                }
            } else {
                when {
                    h in 22..23 || h == 0 -> "Gece geç saatte scroll riski. Uykunuzu koruyun! 🌙"
                    h in 6..9 -> "Sabah scroll tuzağı saptandı. Güne temiz başlayın! ☀️"
                    h in 12..14 -> "Öğle arası savunmasızlığı. Odaklanın! 💪"
                    else -> "Yarın bu saate dikkat edin. 🎯"
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFF0055).copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "PEAK VULNERABILITY HOUR" else "GÜNÜN KRİTİK SAATİ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF0055),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = peakMsg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = warningMsg,
                        fontSize = 11.5.sp,
                        color = Color(0xFFFFB703),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Canlı Kalkan Günlüğü Kartı
        CyberShieldActivityLogCard(prefs = prefs)


        Spacer(modifier = Modifier.height(30.dp))

        TextButton(onClick = onReopenOnboarding) {
            Text(
                text = if (isEn) "Setup & Preview Guide" else "Kurulum ve Önizleme Rehberi",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CyberShieldActivityLogCard(prefs: android.content.SharedPreferences) {
    val isEn = getAppLanguage(prefs) == "en"
    var rawLogs by remember { mutableStateOf(prefs.getString("recent_shield_logs", "") ?: "") }

    LaunchedEffect(Unit) {
        while (true) {
            rawLogs = prefs.getString("recent_shield_logs", "") ?: ""
            kotlinx.coroutines.delay(1500)
        }
    }

    val totalBlocks = remember(rawLogs) { prefs.getInt("total_blocks", 0) }

    val logList = remember(rawLogs, totalBlocks) {
        if (rawLogs.isBlank()) emptyList()
        else {
            val entries = rawLogs.split(";").filter { it.isNotBlank() }
            entries.mapIndexed { index, entry ->
                val parts = entry.split("|")
                val time = parts.getOrNull(0) ?: "--:--"
                val app = parts.getOrNull(1) ?: "Instagram Reels"
                val eventNum = Math.max(1, totalBlocks - index)
                Triple(time, app, eventNum)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚨", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEn) "LIVE SHIELD LOG" else "CANLI KALKAN GÜNLÜĞÜ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (logList.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF070A12),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isEn) 
                                "No traps blocked yet. Your focus is safe!" 
                            else 
                                "Henüz engellenen bir tuzak yok. Zihniniz ve odağınız güvende!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logList.take(4).forEach { (time, app, eventNum) ->
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

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF070A12),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        painter = painterResource(id = drawableRes),
                                        contentDescription = app,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = app,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = if (isEn) "Time $time" else "Saat $time",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (isEn) "BLOCK #$eventNum" else "ENGEL #$eventNum",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accentColor,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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

// ------------------------------------------
// 2. SEKME: MODLAR & HEDEF UYGULAMALAR
// ------------------------------------------
@Composable
fun ModesAndAppsScreen(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val isEn = getAppLanguage(prefs) == "en"
    var isInstagramEnabled by remember { mutableStateOf(prefs.getBoolean("is_instagram_enabled", true)) }
    var isTiktokEnabled by remember { mutableStateOf(prefs.getBoolean("is_tiktok_enabled", true)) }
    var isYoutubeEnabled by remember { mutableStateOf(prefs.getBoolean("is_youtube_enabled", true)) }
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
                title = if (isEn) "Instagram Protection Scope" else "Instagram Koruma Kapsamı",
                content = if (isEn) 
                    "Instantly blocks scrolling through Instagram Reels, Explore feed, and home posts.\n\n💬 Important Note: Your DMs (Messages) are NOT blocked, you can chat with friends freely."
                else 
                    "Instagram'daki Reels videolarını, Keşfet akışını ve ana sayfadaki gönderileri aşağı/yukarı kaydırmayı anında engeller.\n\n💬 Önemli Not: DM (Mesajlar) ekranınız engellenmez, arkadaşlarınızla rahatça mesajlaşabilirsiniz.",
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
                    "Blocks short videos on TikTok and prevents scrolling.\n\n💬 Important Note: DM (Messaging) remains fully accessible."
                else 
                    "TikTok'taki kısa videoları engeller ve kaydırma yapmanıza izin vermez.\n\n💬 Önemli Not: Sadece DM (Mesajlaşma) kullanımına izin verir.",
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
                    "Only blocks YouTube Shorts videos; does not interfere with general YouTube browsing or searches.\n\n💡 Why Leave Long Videos Alone?\nWhen you choose long-form, high-quality videos over short fast-consumed clips, your brain's attention span lengthens and your lowered boredom threshold gradually heals."
                else 
                    "Sadece YouTube Shorts videolarını engeller; genel YouTube kullanımınıza ve aramalara karışmaz.\n\n💡 Neden Uzun Videolara Karışmıyoruz?\nKısa ve hızlı tüketilen videolar yerine uzun ve nitelikli videoları tercih ettiğinizde beyninizin odaklanma süresi uzayacak ve sahte dopamin nedeniyle düşen sıkılma eşiğiniz zamanla yeniden iyileşecektir.",
                iconRes = R.drawable.ic_youtube,
                iconTint = Color(0xFFFF0055),
                onDismiss = { activeAppInfoDialog = null }
            )
        }
    }

    if (showPhilosophyDialog) {
        ScrollableTextDialog(
            isEn = isEn,
            title = if (isEn) "🛡️ Why So Unforgiving?" else "🛡️ Neden Bu Kadar Acımasız?",
            content = if (isEn) 
                "Other 'digital wellbeing' apps offer flexibility like '15 minutes per day' or '3 scroll chances'. In addiction psychology, this is called the 'Bargaining Phase'.\n\nIf you tell your brain 'You can watch only 3 videos', it exploits those 3 videos to the last drop. When time runs out, you feel empty and angry. In years-long screen addictions, any flexibility is abused.\n\nWe do NOT bargain with addiction.\n\nThere is no 'just one video' or 'just 5 minutes'. The INSTANT you try to enter a doomscrolling feed (like Reels or Shorts), the system pulls the plug without excuses. No time limits, no bargaining. Your brain learns 'If I enter, I get kicked out instantly', breaking the fake dopamine loop at its root."
            else 
                "Diğer 'dijital refah' uygulamaları genellikle size 'Günde 15 dakika' veya '3 kaydırma hakkı' gibi esneklikler veya gece modları sunar. Ancak bağımlılık psikolojisinde buna 'Pazarlık Evresi' denir.\n\nEğer beyninize 'Sadece 3 video izleyebilirsin' derseniz, beyin o 3 videoyu son damlasına kadar sömürür. Hak bittiğinde ise müthiş bir boşluk hissine ve öfkeye kapılırsınız. Yıllarca süren ekran bağımlılıklarında (3+ yıl), kişi kendine bırakılan her esnekliği istismar eder ve günün sonunda sınırları aşarak yine kendini suçlarken bulur.\n\nBiz bağımlılıkla pazarlık yapmıyoruz.\n\n'Sadece bir video' veya 'sadece 5 dakika' diye bir şey yoktur. Kaydırma batağına (Reels/Shorts) girmeye çalıştığınız an, sistem mazeret kabul etmeden fişi çeker. Zaman sınırı yok, pazarlık payı yok. Beyniniz 'Girersem anında atılırım' şartlanmasını çok hızlı öğrenir ve o sonsuz dopamin döngüsü fiziksel olarak kökünden kırılmış olur.",
            onDismiss = { showPhilosophyDialog = false }
        )
    }

    if (pendingDisableApp != null) {
        AlertDialog(
            onDismissRequest = { pendingDisableApp = null },
            title = { 
                Text(if (isEn) "🛑 SHIELD ALARM: STREAK WILL BE BROKEN!" else "🛑 SIS-ALARM: SERİ BOZULACAK!", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF0055), letterSpacing = 1.sp) 
            },
            text = { 
                Text(
                    if (isEn) 
                        "⚠️ WARNING: Disabling any app protection shield will INSTANTLY RESET YOUR DAILY STREAK to 0!\n\nThe instant the shield is lowered, your streak drops to 0 days and all your hard work is reset. Do you still want to surrender?" 
                    else 
                        "⚠️ DİKKAT: Herhangi bir uygulamanın koruma kalkanını kapatırsanız GÜNLÜK SERİNİZ (STREAK) ANINDA SIFIRLANIR!\n\nKalkan indirildiği an seriniz 0 güne düşecek ve tüm emeğiniz sıfırlanacaktır. Yine de pes etmek istiyor musunuz?", 
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        val appName = when (pendingDisableApp) {
                            "instagram" -> "Instagram Reels"
                            "tiktok" -> "TikTok"
                            "youtube" -> "YouTube Shorts"
                            else -> "Hedef Uygulama"
                        }
                        when (pendingDisableApp) {
                            "instagram" -> {
                                isInstagramEnabled = false
                                prefs.edit()
                                    .putBoolean("is_instagram_enabled", false)
                                    .putInt("streak_days", 0)
                                    .apply()
                            }
                            "tiktok" -> {
                                isTiktokEnabled = false
                                prefs.edit()
                                    .putBoolean("is_tiktok_enabled", false)
                                    .putInt("streak_days", 0)
                                    .apply()
                            }
                            "youtube" -> {
                                isYoutubeEnabled = false
                                prefs.edit()
                                    .putBoolean("is_youtube_enabled", false)
                                    .putInt("streak_days", 0)
                                    .apply()
                            }
                        }
                        
                        AntiScrollService().showShieldStatusNotification(
                            context,
                            if (isEn) "🛑 $appName Shield Lowered!" else "🛑 $appName Kalkanı İndirildi!",
                            if (isEn) "$appName protection shield disabled. Your daily streak has been reset to 0." else "$appName koruma kalkanı kapatıldı. Günlük seriniz 0'a düştü.",
                            1004
                        )

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
                    Text(if (isEn) "Keep Shield & Streak 🛡️" else "Seriyi Koru, Açık Kalsın 🛡️", fontWeight = FontWeight.ExtraBold)
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
                    .width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00F2FE))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isEn) "Shield Controls" else "Koruma Kalkanları",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE)
                )
                Text(
                    text = if (isEn) "Choose platforms to guard against doomscrolling" else "Engellenmesini istediğin sonsuz akışları seç",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Cyber Protocol Card (Gold Neon)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1700),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB703)),
            tonalElevation = 8.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPhilosophyDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛡️", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "SHIELD PROTOCOL" else "SİS KORUMA PROTOKOLÜ",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFB703),
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB703).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isEn) "Read →" else "Oku →",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB703),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEn) 
                            "The system does not bargain with addiction. Click to read our zero-compromise philosophy." 
                        else 
                            "Sistem bağımlılıkla pazarlık yapmaz. Felsefemizi ve tavizsiz çalışma mantığımızı okumak için tıklayın.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isEn) "🚫 ACTIVE TARGET SHIELDS" else "🚫 AKTİF HEDEF KALKANLARI",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Instagram Card (Magenta/Purple Glow)
        CyberTargetAppCard(
            isEn = isEn,
            name = "Instagram Reels",
            iconRes = R.drawable.ic_instagram,
            isChecked = isInstagramEnabled,
            brandColor = Color(0xFFE1306C),
            blockCount = blocksInstagram,
            onInfoClick = { activeAppInfoDialog = "instagram" }
        ) { checked ->
            if (!checked) {
                pendingDisableApp = "instagram"
            } else {
                isInstagramEnabled = true
                prefs.edit().putBoolean("is_instagram_enabled", true).apply()
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // TikTok Card (Neon Cyan Glow)
        CyberTargetAppCard(
            isEn = isEn,
            name = if (isEn) "TikTok Feed" else "TikTok Akışı",
            iconRes = R.drawable.ic_tiktok,
            isChecked = isTiktokEnabled,
            brandColor = Color(0xFF00F2FE),
            blockCount = blocksTiktok,
            isBeta = true,
            onInfoClick = { activeAppInfoDialog = "tiktok" }
        ) { checked ->
            if (!checked) {
                pendingDisableApp = "tiktok"
            } else {
                isTiktokEnabled = true
                prefs.edit().putBoolean("is_tiktok_enabled", true).apply()
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        // YouTube Shorts Card (Neon Red Glow)
        CyberTargetAppCard(
            isEn = isEn,
            name = "YouTube Shorts",
            iconRes = R.drawable.ic_youtube,
            isChecked = isYoutubeEnabled,
            brandColor = Color(0xFFFF0000),
            blockCount = blocksYoutube,
            isBeta = true,
            onInfoClick = { activeAppInfoDialog = "youtube" }
        ) { checked ->
            if (!checked) {
                pendingDisableApp = "youtube"
            } else {
                isYoutubeEnabled = true
                prefs.edit().putBoolean("is_youtube_enabled", true).apply()
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ModeSelectionCard(
    title: String,
    desc: String,
    isSelected: Boolean,
    isComingSoon: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = if (isComingSoon) { {} } else onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isComingSoon) 0.5f else 1.0f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isComingSoon) {
                Text("❌", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (isComingSoon) {
                        Spacer(modifier = Modifier.width(8.dp))
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
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), lineHeight = 15.sp)
            }
        }
    }
}

@Composable
fun CyberTargetAppCard(
    isEn: Boolean = false,
    name: String,
    iconRes: Int,
    isChecked: Boolean,
    brandColor: Color,
    blockCount: Int,
    isBeta: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(
            if (isChecked) 1.5.dp else 1.dp,
            if (isChecked) brandColor else Color(0xFF1E2A40)
        ),
        tonalElevation = 6.dp,
        shadowElevation = if (isChecked) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Üst Satır: İkon + Uygulama Adı/Beta + Şalter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isChecked) brandColor.copy(alpha = 0.15f) else Color(0xFF1E2A40).copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = if (isChecked) brandColor else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isBeta) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFB703).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703))
                            ) {
                                Text(
                                    text = "BETA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFB703),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

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

            Spacer(modifier = Modifier.height(8.dp))

            // Alt Satır: Durum Yazısı + Bilgi Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isChecked) 
                        (if (isEn) "⚡ Shield Guarding ($blockCount Blocks)" else "⚡ Kalkan Nöbette ($blockCount Engelleme)") 
                    else 
                        (if (isEn) "⚪ Protection Disabled" else "⚪ Koruma Kapalı"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isChecked) brandColor else Color.Gray,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(12.dp))

                if (onInfoClick != null) {
                    Surface(
                        onClick = onInfoClick,
                        shape = RoundedCornerShape(8.dp),
                        color = brandColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, brandColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = brandColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isEn) "Info & Scope" else "Bilgi & Kapsam",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandColor
                            )
                        }
                    }
                }
            }
        }
    }
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
                Text("❌", fontSize = 14.sp)
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
// 4. SEKME: 📈 İLERLEME DURUMU & GERÇEK ZAMANLI İSTATİSTİKLER (ESKİ ROZETLER)
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

    val savedMinutes = totalBlocks * 2
    val savedTimeStr = if (isEn) {
        if (savedMinutes >= 60) {
            val hours = savedMinutes / 60
            val mins = savedMinutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours} Hours"
        } else {
            "$savedMinutes Min"
        }
    } else {
        if (savedMinutes >= 60) {
            val hours = savedMinutes / 60
            val mins = savedMinutes % 60
            if (mins > 0) "${hours}s ${mins}dk" else "${hours} Saat"
        } else {
            "$savedMinutes Dk"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Left Accent Header Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00F2FE))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isEn) "Progress & Report" else "İlerleme & Rapor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE)
                )
                Text(
                    text = if (isEn) "Your saved time and block summary" else "Kurtarılan zamanınız ve engelleme özetiniz",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        // Compact Cyber Note Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF00FF87).copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💡", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEn) "Each block saves an average of ~2 mins of focus." else "Her engelleme ortalama ~2 dk zaman kazandırır.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF87)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3 Ana Önemli Gösterge Kartı (100% Eşit Yükseklik & Tam Simetrik)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Kurtarılan Zaman Kartı
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00FF87).copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏳", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Saved Time" else "Kurtarılan", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(
                        text = savedTimeStr,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00FF87),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(if (isEn) "Total Focus" else "Toplam Odak", fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                }
            }

            // 2. Engelleme Sayısı Kartı
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00F2FE).copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Blocks" else "Engelleme", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(
                        text = if (isEn) "$totalBlocks Times" else "$totalBlocks Defa",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00F2FE),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(if (isEn) "Traps Broken" else "Tuzak Kırıldı", fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                }
            }

            // 3. Günlük Seri Kartı
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFB703).copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Streak" else "Seri", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(
                        text = if (isEn) "$streakDays Days" else "$streakDays Gün",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB703),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(if (isEn) "Active Shield" else "Aktif Kalkan", fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Haftalık Nöbet Kuleleri Grafiği
        CyberWeeklyPillarsChart(isEn = isEn, weeklyDetails = weeklyDetails)

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // 24-Saatlik Doomscroll Isı Haritası
        DoomscrollHourlyHeatmap(isEn = isEn, prefs = prefs)

        Spacer(modifier = Modifier.height(20.dp))

        // Uygulama Bazlı Temiz Özet Kartı
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = if (isEn) "📱 App Breakdown" else "📱 Uygulama Bazlı Özet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                // App Row 1: Instagram
                SimpleAppStatCard(
                    isEn = isEn,
                    appName = "Instagram",
                    iconRes = R.drawable.ic_instagram,
                    count = blocksInstagram,
                    color = Color(0xFF833AB4)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // App Row 2: TikTok
                SimpleAppStatCard(
                    isEn = isEn,
                    appName = "TikTok",
                    iconRes = R.drawable.ic_tiktok,
                    count = blocksTiktok,
                    color = Color(0xFF00F2FE)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // App Row 3: YouTube Shorts
                SimpleAppStatCard(
                    isEn = isEn,
                    appName = "YouTube Shorts",
                    iconRes = R.drawable.ic_youtube,
                    count = blocksYoutube,
                    color = Color(0xFFFF0000)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}



@Composable
fun DoomscrollHourlyHeatmap(
    isEn: Boolean = false,
    prefs: android.content.SharedPreferences
) {
    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    // Bugünün saatlik blok verilerini oku
    val hourlyData = remember {
        (0..23).map { h ->
            val key = "blocks_${todayStr}_hour_${h.toString().padStart(2, '0')}"
            prefs.getInt(key, 0)
        }
    }

    // Gerçek veri yoksa demo mock data kullan (ilk açılışta canlı görünüm)
    val hasRealData = hourlyData.any { it > 0 }
    val displayData = if (hasRealData) hourlyData else listOf(
        0, 0, 1, 0, 0, 0, 2, 4, 3, 1, 0, 2,
        5, 3, 1, 0, 2, 4, 8, 6, 3, 9, 5, 2
    )

    val maxVal = displayData.maxOrNull()?.takeIf { it > 0 } ?: 1

    var selectedHour by remember { mutableIntStateOf(-1) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F1523),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Başlık
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🕒", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEn) "24-HOUR DOOMSCROLL HEATMAP" else "24 SAATLİK DOOMSCROLL ISI HARİTASI",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00F2FE),
                        letterSpacing = 0.8.sp
                    )
                    if (!hasRealData) {
                        Text(
                            text = if (isEn) "Demo preview — blocks will appear here" else "Demo önizleme — engellemeler burada görünecek",
                            fontSize = 9.sp,
                            color = Color(0xFFFFB703).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Seçili saat detay bilgisi
            if (selectedHour >= 0) {
                val sH = selectedHour
                val sBlocks = displayData[sH]
                val sSaved = sBlocks * 2
                val sRange = "${sH.toString().padStart(2, '0')}:00 - ${(sH + 1).toString().padStart(2, '0')}:00"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF070A12),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0055).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🕒 $sRange  |  ${if (isEn) "$sBlocks Blocks  |  ~${sSaved} Min Saved" else "$sBlocks Engelleme  |  ~${sSaved} Dk Kurtarıldı"}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 24 Hücrelik Grid (4 satır × 6 sütun)
            val rows = listOf(
                (0..5).toList(),
                (6..11).toList(),
                (12..17).toList(),
                (18..23).toList()
            )
            val rowLabels = listOf("00–05", "06–11", "12–17", "18–23")

            rows.forEachIndexed { rowIdx, hours ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rowLabels[rowIdx],
                        fontSize = 8.5.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.width(32.dp)
                    )
                    hours.forEach { h ->
                        val count = displayData[h]
                        val intensity = count.toFloat() / maxVal.toFloat()
                        val cellColor = when {
                            count == 0 -> Color(0xFF0F1523)
                            count <= 3 -> Color(0xFF00F2FE).copy(alpha = 0.20f + intensity * 0.30f)
                            count <= 7 -> Color(0xFF00FF87).copy(alpha = 0.40f + intensity * 0.30f)
                            else -> Color(0xFFFF0055).copy(alpha = 0.65f + intensity * 0.35f)
                        }
                        val borderColor = if (selectedHour == h) Color(0xFFFFB703) else Color(0xFF1E2A40)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(cellColor)
                                .border(
                                    width = if (selectedHour == h) 1.5.dp else 0.5.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clickable { selectedHour = if (selectedHour == h) -1 else h },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = h.toString().padStart(2, '0'),
                                fontSize = 7.5.sp,
                                color = if (count > 0) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                                fontWeight = if (count > 3) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (rowIdx < rows.size - 1) Spacer(modifier = Modifier.height(3.dp))
            }

            // Renk Lejantı
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(Color(0xFF0F1523), Color(0xFF1E2A40), if (isEn) "Clean" else "Temiz"),
                    Triple(Color(0xFF00F2FE).copy(alpha = 0.35f), Color(0xFF00F2FE).copy(alpha = 0.5f), "1–3"),
                    Triple(Color(0xFF00FF87).copy(alpha = 0.6f), Color(0xFF00FF87).copy(alpha = 0.7f), "4–7"),
                    Triple(Color(0xFFFF0055).copy(alpha = 0.8f), Color(0xFFFF0055), "8+")
                ).forEach { (bg, border, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(bg)
                                .border(0.5.dp, border, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleAppStatCard(
    isEn: Boolean = false,
    appName: String,
    iconRes: Int,
    count: Int,
    color: Color
) {
    val savedMins = count * 3
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = appName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isEn) "$count Blocks" else "$count Engelleme",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isEn) "($savedMins Min)" else "($savedMins Dk)",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CyberBadgeCard(
    isEn: Boolean = false,
    icon: String,
    title: String,
    desc: String,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isUnlocked) Color(0xFFFFB703) else Color(0xFF1E2A40)
    val bgColor = if (isUnlocked) Color(0xFF0F1523) else Color(0xFF0A0E18)
    val iconAlpha = if (isUnlocked) 1f else 0.4f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(if (isUnlocked) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isUnlocked) 6.dp else 0.dp,
        shadowElevation = if (isUnlocked) 4.dp else 0.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 26.sp, modifier = Modifier.alpha(iconAlpha))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isUnlocked) Color(0xFFFFB703).copy(alpha = 0.2f) else Color(0xFF1E2A40)
                ) {
                    Text(
                        text = if (isUnlocked) (if (isEn) "UNLOCKED" else "KAZANILDI") else (if (isEn) "LOCKED" else "KİLİTLİ"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isUnlocked) Color(0xFFFFB703) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 10.sp,
                color = if (isUnlocked) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.35f),
                lineHeight = 13.sp
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
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00F2FE).copy(alpha = 0.6f)),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 1. Üst Başlık (Sol Konuma Sabitlenmiş)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isEn) "📊 WEEKLY GUARD TOWERS" else "📊 HAFTALIK NÖBET KULELERİ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00F2FE),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isEn) "Tap towers for daily breakdown" else "Detay için kulelere dokunun",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Renk Açıklama Rozetleri (Legend - Geniş & Rahat Düzlem)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Instagram Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF833AB4).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF833AB4).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF833AB4), CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Instagram", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // TikTok Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00F2FE), CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("TikTok", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // YouTube Shorts Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF0000).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF0000), CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("YouTube", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        // 1. ÜST SLOT: Sayı (Sabit 22.dp Yükseklik - Tam Yatay Hizalama)
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

                        // 2. ORTA SLOT: Çubuk Kanvası (Sabit 105.dp Yükseklik - Alt Taban Hizalama)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 24.dp else 18.dp)
                                    .height(if (total > 0) currentPillarHeightDp else 6.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(Color(0xFF1E2A40))
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
                                        // Alt - Instagram (Mor)
                                        if (instaRatio > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(instaRatio)
                                                    .background(Color(0xFF833AB4))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. ALT SLOT: Gün Adı (Sabit 20.dp Yükseklik - Tam Yatay Hizalama)
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
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF070A12),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, selectedDetail.topAppColor.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 1. SATIR: Gün Başlığı
                            Text(
                                text = if (isEn) "📅 ${selectedDetail.dayName} Daily Summary" else "📅 ${selectedDetail.dayName} Günlük Özet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = selectedDetail.topAppColor
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. SATIR: Rozet Etiketleri (Geniş & Rahat)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (isEn) "🛡️ ${selectedDetail.totalBlocks} Blocks" else "🛡️ ${selectedDetail.totalBlocks} Engelleme",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00F2FE),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00FF87).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = if (isEn) "⏳ ${selectedDetail.totalBlocks * 3} Min Saved" else "⏳ ${selectedDetail.totalBlocks * 3} Dk Kurtarıldı",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (selectedDetail.totalBlocks > 0) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val total = selectedDetail.totalBlocks
                                    val insta = selectedDetail.effectiveInsta
                                    val tiktok = selectedDetail.effectiveTiktok
                                    val yt = selectedDetail.effectiveYt

                                    if (insta > 0) {
                                        val pct = (insta * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📸 Instagram:", fontSize = 11.5.sp, color = Color(0xFF833AB4), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp))
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E2A40))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFF833AB4)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$insta Times ($pct%)" else "$insta Defa (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (tiktok > 0) {
                                        val pct = (tiktok * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🎵 TikTok:", fontSize = 11.5.sp, color = Color(0xFF00F2FE), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp))
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E2A40))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFF00F2FE)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isEn) "$tiktok Times ($pct%)" else "$tiktok Defa (%$pct)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (yt > 0) {
                                        val pct = (yt * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("▶️ YouTube:", fontSize = 11.5.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp))
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFFF0000))) {
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

@Composable
fun XpTaskRow(icon: String, title: String, xpReward: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = xpReward,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun WeeklyBarChart(weeklyData: List<Int>) {
    val days = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val maxBlocks = weeklyData.maxOrNull()?.coerceAtLeast(1) ?: 1

    val todayIndex = java.util.Calendar.getInstance().let { 
        val dow = it.get(java.util.Calendar.DAY_OF_WEEK)
        if (dow == java.util.Calendar.SUNDAY) 6 else dow - 2 
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val blocks = weeklyData[index]
            val timeMins = blocks * 3
            val heightRatio = blocks.toFloat() / maxBlocks.toFloat()
            val finalHeightRatio = if (blocks > 0) (heightRatio * 0.8f + 0.1f) else 0.05f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (blocks > 0) {
                    val timeStr = if (timeMins >= 60) "${timeMins / 60}s\n${timeMins % 60}d" else "${timeMins}d"
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Spacer(modifier = Modifier.height(18.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight(finalHeightRatio)
                        .width(16.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (index == todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = day,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

// ------------------------------------------
// 5. SEKME: HAKKINDA (ABOUT)
// ------------------------------------------


    @Composable
    fun StreakBanner(streakDays: Int) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFF6D00).copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFFFF6D00).copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "$streakDays GÜNLÜK SERİ!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF6D00)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kalkan aktif kaldığı sürece serin büyümeye devam edecek. Harikasın!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    @Composable
    fun AppDistributionChart(
        instagramBlocks: Int,
        tiktokBlocks: Int,
        youtubeBlocks: Int
    ) {
        val total = (instagramBlocks + tiktokBlocks + youtubeBlocks).coerceAtLeast(1)
        val instaWeight = instagramBlocks.toFloat() / total
        val tiktokWeight = tiktokBlocks.toFloat() / total
        val youtubeWeight = youtubeBlocks.toFloat() / total

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📱 Uygulama Dağılım Oranı",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    if (instagramBlocks > 0) {
                        Box(
                            modifier = Modifier
                                .weight(instaWeight)
                                .fillMaxHeight()
                                .background(Color(0xFF833AB4))
                        )
                    }
                    if (tiktokBlocks > 0) {
                        Box(
                            modifier = Modifier
                                .weight(tiktokWeight)
                                .fillMaxHeight()
                                .background(Color(0xFF00F2FE))
                        )
                    }
                    if (youtubeBlocks > 0) {
                        Box(
                            modifier = Modifier
                                .weight(youtubeWeight)
                                .fillMaxHeight()
                                .background(Color(0xFFFF0000))
                        )
                    }
                    if (instagramBlocks == 0 && tiktokBlocks == 0 && youtubeBlocks == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartLegend(
                        color = Color(0xFF833AB4),
                        label = "Instagram (%${(instaWeight * 100).toInt()})"
                    )
                    ChartLegend(
                        color = Color(0xFF00F2FE),
                        label = "TikTok (%${(tiktokWeight * 100).toInt()})"
                    )
                    ChartLegend(
                        color = Color(0xFFFF0000),
                        label = "YouTube (%${(youtubeWeight * 100).toInt()})"
                    )
                }
            }
        }
    }



@Composable
fun AppStatDetailRow(
        appName: String,
        iconRes: Int,
        blockCount: Int,
        color: Color,
        timeSpentMs: Long = 0L
    ) {
        val savedMins = blockCount * 3
        val timeStr =
            if (savedMins >= 60) "${savedMins / 60}s ${savedMins % 60}dk" else "${savedMins} dk"

        val spentMins = timeSpentMs / 60000
        val spentStr =
            if (spentMins >= 60) "${spentMins / 60}s ${spentMins % 60}dk" else "${spentMins} dk"

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$blockCount Engelleme Yapıldı",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        // Aktif sureyi 0 olsa bile goster, ki kullanici degisikligi fark edebilsin!
                        Text(
                            "Aktif Süre: $spentStr",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(timeStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                    Text(
                        "+${blockCount * 3} XP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
