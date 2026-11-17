package com.resolvecommunity.awaydoomscrollin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.content.pm.PackageManager
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
        // Configure the starting window before Activity content is attached. This keeps
        // the splash screen and the first onboarding frame on the same edge-to-edge path.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        AppText.init(this)
        preferSmoothUiRefreshRate()

        if (TelemetryManager.isTelemetryEnabled(this)) {
            TelemetryManager.sendTelemetryAsync(this)
        }

        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val initialOnboardingDone = prefs.getBoolean("onboarding_completed", false)
        ModeIntroNotice.classifyOnce(prefs)

        AccessibilityStreakPolicy.syncState(
            this,
            isAccessibilityServiceEnabled(this, AntiScrollService::class.java)
        )

        setContent {
            // Texts come from AppText in the app's own language. Switching
            // between two non-Turkish languages leaves every `isEn` unchanged,
            // so a static local makes the whole UI redraw on any switch.
            var uiLanguage by remember { mutableStateOf(getAppLanguage(prefs)) }
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == KEY_APP_LANGUAGE) uiLanguage = getAppLanguage(p)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            CompositionLocalProvider(LocalAppLanguage provides uiLanguage) {
            ZenTheme {
                var isOnboardingCompleted by remember { mutableStateOf(initialOnboardingDone) }
                var onboardingStartStep by remember { mutableIntStateOf(1) }
                var showRenewedTelemetryConsent by remember {
                    mutableStateOf(
                        initialOnboardingDone && TelemetryManager.requiresRenewedConsent(this@MainActivity)
                    )
                }
                // Once after an update. An install still owed the older modes
                // card sees that one first and this summary on the next launch.
                var showReleaseNotice by remember {
                    mutableStateOf(
                        initialOnboardingDone && !ModeIntroNotice.isPending(prefs) &&
                            ReleaseNotice.isPending(this@MainActivity, prefs)
                    )
                }

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
                        },
                        // The what's-new card waits for the consent dialog to close.
                        modeIntroAllowed = !showRenewedTelemetryConsent
                    )
                }

                if (showRenewedTelemetryConsent) {
                    val isEn = getAppLanguage(prefs) != "tr"
                    TelemetryConsentDialog(
                        isEn = isEn,
                        isRenewal = true,
                        onAccept = {
                            TelemetryManager.setTelemetryEnabled(this@MainActivity, true)
                            showRenewedTelemetryConsent = false
                        },
                        onDecline = {
                            TelemetryManager.setTelemetryEnabled(this@MainActivity, false)
                            showRenewedTelemetryConsent = false
                        }
                    )
                }

                if (isOnboardingCompleted && showReleaseNotice && !showRenewedTelemetryConsent) {
                    ReleaseNoticeDialog(
                        versionName = ReleaseNotice.versionName(this@MainActivity),
                        onDismiss = {
                            ReleaseNotice.markSeen(this@MainActivity, prefs)
                            showReleaseNotice = false
                        }
                    )
                }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferSmoothUiRefreshRate()
        val serviceEnabled = isAccessibilityServiceEnabled(this, AntiScrollService::class.java)
        // Only noted in the troubleshooting log; nothing is shown.
        ProtectionStopWatch.consumeUnexpectedStop(this, serviceEnabled)
        AccessibilityStreakPolicy.syncState(this, serviceEnabled)
    }

    /**
     * Advertise the fastest mode available at the current resolution. Android treats
     * this as a preference and can still cap it for the user's display setting, power
     * saving, temperature, or device policy; no fixed 120 Hz mode is forced.
     */
    private fun preferSmoothUiRefreshRate() {
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val currentMode = display.mode
        val preferredRate = display.supportedModes
            .asSequence()
            .filter { it.physicalWidth == currentMode.physicalWidth }
            .filter { it.physicalHeight == currentMode.physicalHeight }
            .maxOfOrNull { it.refreshRate }
            ?: return
        val attributes = window.attributes
        if (kotlin.math.abs(attributes.preferredRefreshRate - preferredRate) < 0.01f) return
        attributes.preferredRefreshRate = preferredRate
        window.attributes = attributes
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

    // Material's default line heights are fixed (bodyLarge: 24sp). Texts here
    // usually set a smaller fontSize without a lineHeight, so wrapped lines kept
    // the 24sp gap. Relative heights scale with whatever fontSize a text sets.
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(lineHeight = 1.4.em),
        bodyMedium = base.bodyMedium.copy(lineHeight = 1.4.em),
        bodySmall = base.bodySmall.copy(lineHeight = 1.4.em),
        labelLarge = base.labelLarge.copy(lineHeight = 1.4.em),
        labelMedium = base.labelMedium.copy(lineHeight = 1.4.em),
        labelSmall = base.labelSmall.copy(lineHeight = 1.4.em),
        titleLarge = base.titleLarge.copy(lineHeight = 1.3.em),
        titleMedium = base.titleMedium.copy(lineHeight = 1.3.em),
        titleSmall = base.titleSmall.copy(lineHeight = 1.3.em)
    )

    val korean = LocalAppLanguage.current == AppLanguage.KO.code
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (korean) typography.koreanLineBreaks() else typography,
        content = content
    )
}

/**
 * Korean wraps at the spaces between words, as Korean text is set, instead of
 * between any two syllables ("열 / 려 있는 것"). Android does this from 13 on
 * when the text is marked Korean; older versions ignore it.
 */
private fun Typography.koreanLineBreaks(): Typography {
    val lineBreak = androidx.compose.ui.text.style.LineBreak(
        strategy = androidx.compose.ui.text.style.LineBreak.Strategy.HighQuality,
        strictness = androidx.compose.ui.text.style.LineBreak.Strictness.Default,
        wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase
    )
    val locale = androidx.compose.ui.text.intl.LocaleList("ko")
    fun androidx.compose.ui.text.TextStyle.ko() = copy(lineBreak = lineBreak, localeList = locale)
    return copy(
        displayLarge = displayLarge.ko(), displayMedium = displayMedium.ko(), displaySmall = displaySmall.ko(),
        headlineLarge = headlineLarge.ko(), headlineMedium = headlineMedium.ko(), headlineSmall = headlineSmall.ko(),
        titleLarge = titleLarge.ko(), titleMedium = titleMedium.ko(), titleSmall = titleSmall.ko(),
        bodyLarge = bodyLarge.ko(), bodyMedium = bodyMedium.ko(), bodySmall = bodySmall.ko(),
        labelLarge = labelLarge.ko(), labelMedium = labelMedium.ko(), labelSmall = labelSmall.ko()
    )
}



/**
 * The interface language code. Texts not yet moved to string resources still
 * pick between English and Turkish inline; their `isEn` is "not Turkish", so
 * every other language shows English there, never Turkish.
 */
fun getAppLanguage(prefs: android.content.SharedPreferences): String = appLanguage(prefs).code

private fun telemetryDisclosureText(isEn: Boolean): String = AppText.get(R.string.main_you_can_help_us_improve_protection)

private fun telemetryRenewedConsentText(isEn: Boolean): String =
    telemetryDisclosureText(isEn) + "\n\n" + AppText.get(R.string.main_renewed_consent_youtube_curtain)

private fun accessibilityDisclosureText(isEn: Boolean): String = AppText.get(R.string.main_awaydoomscrollin_needs_accessibility_access_to_r)

private fun getAccessibilityPathGuidance(isEn: Boolean): String {
    val m = Build.MANUFACTURER.lowercase(java.util.Locale.US)
    return when {
        m.contains("honor") || m.contains("huawei") ->
            AppText.get(R.string.main_settings_accessibility_downloaded_services_awayd)

        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            AppText.get(R.string.main_settings_additional_settings_accessibility_downl)

        m.contains("samsung") ->
            AppText.get(R.string.main_settings_accessibility_installed_apps_awaydoomsc)

        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            AppText.get(R.string.main_settings_additional_settings_accessibility_downl)

        m.contains("vivo") || m.contains("iqoo") ->
            AppText.get(R.string.main_settings_shortcuts_accessibility_accessibility_d)

        else ->
            AppText.get(R.string.main_settings_accessibility_downloaded_apps_or_instal)
    }
}

private fun getAccessibilityGuidanceTip(isEn: Boolean): String {
    val m = Build.MANUFACTURER.lowercase(java.util.Locale.US)
    return when {
        m.contains("honor") || m.contains("huawei") ->
            AppText.get(R.string.main_tip_in_settings_find_it_under)

        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            AppText.get(R.string.main_tip_in_settings_find_it_under_2)

        m.contains("samsung") ->
            AppText.get(R.string.main_tip_in_settings_find_it_under_3)

        else ->
            AppText.get(R.string.main_tip_in_settings_find_it_under_4)
    }
}

private fun openAccessibilitySettings(context: Context, isEn: Boolean = false) {
    Toast.makeText(
        context,
        getAccessibilityPathGuidance(isEn),
        Toast.LENGTH_LONG
    ).show()
    runCatching {
        context.startActivity(AccessibilitySettingsLink.intent(context, newTask = context !is android.app.Activity))
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
            AppText.get(R.string.main_your_preference_could_not_be_saved),
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
            Text(AppText.get(R.string.main_accessibility_access))
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
                    AppText.get(R.string.main_agree_and_open_settings),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppText.get(R.string.main_not_now))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * What's-new card shown once to installs updated from a version without
 * modes; see ModeIntroNotice. The user picks modes in the Apps tab; nothing
 * is changed for them here.
 */
@Composable
private fun ModeIntroDialog(
    isEn: Boolean,
    onChooseModes: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                // No version number: installs updated straight from v1.2.0 to a
                // later release see this card too.
                AppText.get(R.string.main_new_protection_modes),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = AppText.get(R.string.main_instagram_and_youtube_now_have_two),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onChooseModes,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AppText.get(R.string.main_choose_modes), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth(),
                    border = dialogButtonBorder(),
                    colors = dialogOutlinedButtonColors()
                ) {
                    Text(AppText.get(R.string.main_later), textAlign = TextAlign.Center)
                }
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/** The short summary of an update; see ReleaseNotice. */
@Composable
private fun ReleaseNoticeDialog(
    versionName: String,
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
            Text(
                AppText.get(R.string.release_notice_title, versionName),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = AppText.get(R.string.release_notice_body),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    text = AppText.get(R.string.release_notice_thanks),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppText.get(R.string.main_got_it), textAlign = TextAlign.Center)
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/**
 * Outlined buttons in dialogs. The theme's outline color is kept faint for
 * card edges and disappears on the dialog surface (about 1.3:1), so these
 * buttons draw a border that reaches ~3:1 against it.
 */
private val DialogButtonShape = RoundedCornerShape(12.dp)

@Composable
private fun dialogButtonBorder() =
    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

@Composable
private fun dialogOutlinedButtonColors() =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

@Composable
private fun TelemetryConsentDialog(
    isEn: Boolean,
    isRenewal: Boolean = false,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_handshake),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (isRenewal) {
                    AppText.get(R.string.main_review_usage_data_sharing)
                } else {
                    AppText.get(R.string.main_share_optional_usage_data)
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (isRenewal) telemetryRenewedConsentText(isEn) else telemetryDisclosureText(isEn),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            // Same height even when one label wraps to two lines.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Both choices look the same: declining is as easy to see as agreeing.
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = DialogButtonShape,
                    border = dialogButtonBorder(),
                    colors = dialogOutlinedButtonColors()
                ) {
                    Text(AppText.get(R.string.main_do_not_share), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = DialogButtonShape,
                    border = dialogButtonBorder(),
                    colors = dialogOutlinedButtonColors()
                ) {
                    Text(
                        AppText.get(R.string.main_agree_to_share),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

/** True only while the system really runs the service; see ProtectionServiceStatus. */
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean =
    ProtectionServiceStatus.read(context, serviceClass) == ProtectionServiceState.RUNNING

@Composable
fun rememberAccessibilityStatus(): Boolean =
    rememberProtectionServiceState() == ProtectionServiceState.RUNNING

@Composable
internal fun rememberProtectionServiceState(): ProtectionServiceState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var state by remember { mutableStateOf(ProtectionServiceStatus.read(context)) }
    var resumes by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = ProtectionServiceStatus.read(context)
                AccessibilityStreakPolicy.syncState(context, state == ProtectionServiceState.RUNNING)
                resumes++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Just after a restart, or right after the switch was turned on, the
    // system may not have bound the service yet: look again before saying so.
    LaunchedEffect(resumes, state) {
        if (state == ProtectionServiceState.LISTED_NOT_RUNNING) {
            delay(1500)
            state = ProtectionServiceStatus.read(context)
            if (state == ProtectionServiceState.RUNNING) AccessibilityStreakPolicy.syncState(context, true)
        }
    }

    return state
}

/**
 * Material shared-axis X: the old screen fades out quickly while the new one
 * slides a short, fixed distance and settles, so two screens never sit
 * half-transparent on top of each other.
 */
fun sharedAxisX(forward: Boolean, shiftPx: Int): ContentTransform {
    val slide = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
    return (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(slide) { if (forward) shiftPx else -shiftPx }) togetherWith
        (fadeOut(tween(90, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(slide) { if (forward) -shiftPx else shiftPx })
}

/**
 * One-time entrance for a setup step's header badge: it grows from 85% with a
 * soft spring (a slight overshoot) and fades in.
 */
fun Modifier.heroEntrance(delayMillis: Long = 120L): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val p = progress.value
        scaleX = 0.85f + 0.15f * p
        scaleY = 0.85f + 0.15f * p
        alpha = p.coerceIn(0f, 1f)
    }
}

/** Height of the floating bottom bar, so tab content can scroll behind it. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Scrolling tab body. The margins scroll with the content instead of
 * clipping it, and the end clears the floating bottom bar, so content no
 * longer stops on a hard line above the bar.
 */
fun Modifier.tabScroll(horizontal: Dp, vertical: Dp): Modifier = composed {
    verticalScroll(rememberScrollState())
        .padding(start = horizontal, end = horizontal, top = vertical,
            bottom = vertical + LocalBottomBarInset.current)
}

fun onboardingStepName(step: Int, isEn: Boolean): String = when (step) {
    1 -> AppText.get(R.string.main_welcome)
    2 -> AppText.get(R.string.main_how_it_works)
    3 -> AppText.get(R.string.main_scope_and_privacy)
    4 -> AppText.get(R.string.main_apps)
    else -> AppText.get(R.string.main_permissions)
}

fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(android.os.PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

/**
 * Shows Android's "Allow / Deny" dialog for running without battery
 * optimization. Falls back to the app info page if a vendor hides it.
 */
@android.annotation.SuppressLint("BatteryLife")
fun requestBatteryUnrestricted(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(request) }.onFailure { openBackgroundSettings(context) }
}

/**
 * Opens the app's info page: it holds Battery ("Unrestricted") on every
 * vendor and the Autostart switch on Xiaomi and POCO.
 */
fun openBackgroundSettings(context: Context) {
    val appInfo = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(appInfo) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * Xiaomi, Redmi and POCO (MIUI / HyperOS) stop apps in the background the way
 * a force stop does, and Android then switches the accessibility service off
 * until the user turns it on again: the most common support ticket.
 */
fun isXiaomiFamily(): Boolean {
    val m = Build.MANUFACTURER.lowercase(java.util.Locale.US)
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
}

/**
 * Xiaomi's Autostart list when this phone has it, the app's info page
 * otherwise; for places that cannot catch a failed start, like notifications.
 */
fun autostartIntent(context: Context): Intent {
    val autostart = Intent().setComponent(ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (autostartPageAvailable(context)) autostart else Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Asks the package manager whether the page exists. Intent.resolveActivity()
 * returns an explicit component unchecked, so it said "yes" on a Galaxy S23.
 * Needs the com.miui.securitycenter <queries> entry on Android 11+.
 */
private fun autostartPageAvailable(context: Context): Boolean = runCatching {
    context.packageManager.resolveActivity(
        Intent().setComponent(ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )), 0
    ) != null
}.getOrDefault(false)

/**
 * Whether this phone has Xiaomi's Autostart switch: the Autostart page is
 * there, or the maker is Xiaomi, Redmi or POCO (where app info holds the
 * switch if the page moved). Other phones have no such switch, and the button
 * only opened app info there (Galaxy S23).
 */
fun hasAutostartSetting(context: Context): Boolean =
    isXiaomiFamily() || autostartPageAvailable(context)

/** MIUI's Autostart list, where the app can be allowed to restart; app info otherwise. */
fun openAutostartSettings(context: Context) {
    val autostart = Intent().setComponent(ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(autostart) }.onFailure { openBackgroundSettings(context) }
}

/**
 * Optional step for phones whose battery manager stops background services
 * (common on Xiaomi, POCO and OnePlus), which turns protection off until a
 * restart. Refreshes when the user comes back from settings.
 */
@Composable
fun BackgroundRunCard(isEn: Boolean, modifier: Modifier = Modifier, stepNumber: Int? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var unrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    val autostart = remember { hasAutostartSetting(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) unrestricted = isBatteryUnrestricted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (unrestricted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stepNumber != null && !unrestricted) {
                    Text(
                        text = stepNumber.toString(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        painter = painterResource(id = if (unrestricted) R.drawable.ic_shield_check else R.drawable.ic_shield),
                        contentDescription = null,
                        tint = if (unrestricted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    AppText.get(R.string.main_run_in_the_background_optional),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Optional on most phones; the one sentence for Xiaomi, Redmi and POCO
            // is set apart in bold amber, without a box.
            Text(
                buildAnnotatedString {
                    append(AppText.get(R.string.main_on_phones_such_as_xiaomi_poco))
                    append(" ")
                    withStyle(SpanStyle(color = Color(0xFFFFB800), fontWeight = FontWeight.Bold)) {
                        append(AppText.get(R.string.main_background_xiaomi_recommended))
                    }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
            // Tested on a Xiaomi 14 (HyperOS 2): clearing Recents ends the app every
            // time, and only with Autostart on does Android restart protection. It is
            // off on a new installation and the app cannot read it.
            if (autostart) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    AppText.get(R.string.main_xiaomi_autostart_note),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { openAutostartSettings(context) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(AppText.get(R.string.main_xiaomi_autostart_button), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Secondary to Autostart above: on Xiaomi it opens the phone's own battery
            // page; protection came back without it once Autostart was on.
            OutlinedButton(
                onClick = {
                    if (!unrestricted) requestBatteryUnrestricted(context)
                    else openBackgroundSettings(context)
                },
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (unrestricted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (unrestricted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (unrestricted) AppText.get(R.string.main_battery_allowed) else AppText.get(R.string.main_battery_allow),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Installed protected apps, refreshed whenever the screen resumes. */
@Composable
fun rememberInstalledApps(): Set<ProtectedApp> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installed by remember { mutableStateOf(InstalledApps.available(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = InstalledApps.available(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return installed
}

fun protectedAppIcon(app: ProtectedApp): Int = when (app) {
    ProtectedApp.INSTAGRAM -> R.drawable.ic_instagram
    ProtectedApp.TIKTOK -> R.drawable.ic_tiktok
    ProtectedApp.YOUTUBE -> R.drawable.ic_youtube
}

/**
 * A supported app that is not installed (or is disabled) on this phone: dimmed,
 * with a neutral badge and a line saying what installing it changes. No switch,
 * because there is nothing to protect yet.
 */
@Composable
fun NotInstalledAppRow(
    isEn: Boolean,
    app: ProtectedApp,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    val name = InstalledApps.displayName(app)
    val badge = AppText.get(R.string.main_not_installed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.6f)
            .semantics(mergeDescendants = true) {
                contentDescription = AppText.get(R.string.main_x_not_installed, name)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(textColor.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = protectedAppIcon(app)),
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, textColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badge,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor.copy(alpha = 0.7f),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = AppText.get(R.string.main_not_on_this_phone_install_it),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
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
    val isEn = getAppLanguage(prefs) != "tr"
    val stepShiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.roundToPx() }

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
                // Margins and the navigation-bar inset scroll with the steps, so
                // content is not cut on a hard line above the bottom edge.
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(24.dp),
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
                        // Each segment fills from the left as its step is reached.
                        val fill by animateFloatAsState(
                            targetValue = if (index + 1 <= step) 1f else 0f,
                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                            label = "OnboardingStepFill$index"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.outline)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fill)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { if (forward) it / 2 else -it / 2 }) togetherWith
                            (fadeOut(tween(90)) + slideOutVertically(tween(260)) { if (forward) -it / 2 else it / 2 })
                    },
                    label = "OnboardingStepLabel"
                ) { labelStep ->
                    Text(
                        // Android drops the trailing space of a string resource, so
                        // "Adım 1 / 5 ·" and the step name were glued together.
                        text = AppText.get(R.string.main_step_x_5, labelStep).trimEnd() + " " +
                            onboardingStepName(labelStep, isEn),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Sayfa Geçişi
            AnimatedContent(
                targetState = step,
                transitionSpec = { sharedAxisX(forward = targetState > initialState, shiftPx = stepShiftPx) },
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
                    TextButton(
                        onClick = { step-- },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            AppText.get(R.string.main_back),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val navButtonVisualState = when {
                    step == 5 && isAccessibilityActive -> 3
                    step == 5 -> 2
                    step == 1 -> 0
                    else -> 1
                }
                // "Start setup" and "Finish setup" breathe gently to mark the
                // one action that matters on those screens.
                val breathe = navButtonVisualState == 0 || navButtonVisualState == 3
                val breathing = rememberInfiniteTransition(label = "SetupButtonBreath")
                val breathScale by breathing.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.035f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "SetupButtonScale"
                )

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
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                    modifier = Modifier
                        .scale(if (breathe) breathScale else 1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .animateContentSize()
                ) {
                    AnimatedContent(
                        targetState = navButtonVisualState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "BottomNavButtonTransition"
                    ) { visualState ->
                        val buttonText = when (visualState) {
                            3 -> AppText.get(R.string.main_finish_setup)
                            2 -> AppText.get(R.string.main_continue_without_permission)
                            0 -> AppText.get(R.string.main_start_setup)
                            else -> AppText.get(R.string.main_next)
                        }
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (visualState == 2) 14.sp else 15.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupReason(title: String, body: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.72f),
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SetupFact(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF00FF87),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun OnboardingStepOne(isEn: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Why AwayDoomscrollin': one calm card, plain type, no decorative icons.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = AppText.get(R.string.main_why_awaydoomscrollin),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = AppText.get(R.string.main_on_device_protection_for_feeds_and),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                SetupReason(
                    title = AppText.get(R.string.main_why_not_just_use_a_timer),
                    body = AppText.get(R.string.main_a_timer_limits_how_long_you)
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 14.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.06f))
                )

                SetupReason(
                    title = AppText.get(R.string.main_communication_stays_available),
                    body = AppText.get(R.string.main_messages_search_and_youtube_long_videos)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SetupFact(
                            title = AppText.get(R.string.main_any_language),
                            body = AppText.get(R.string.main_works_whatever_language_your_apps_use),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        SetupFact(
                            title = AppText.get(R.string.main_on_device),
                            body = AppText.get(R.string.main_protection_decisions_are_made_on_your),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SetupFact(
                            title = AppText.get(R.string.main_open_source),
                            body = AppText.get(R.string.main_the_source_code_can_be_reviewed),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        SetupFact(
                            title = AppText.get(R.string.main_no_ads),
                            body = AppText.get(R.string.main_no_ads_no_subscription_required),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
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
                            text = AppText.get(R.string.main_i_m_one_of_you),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = AppText.get(R.string.main_a_letter_from_resolve_community),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bölüm 1: Hikayenin Başlangıcı
                Text(
                    text = AppText.get(R.string.main_hi_i_m_the_independent_developer),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AppText.get(R.string.main_during_the_covid_19_pandemic_i),
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
                                text = AppText.get(R.string.main_what_i_noticed),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppText.get(R.string.main_i_realized_that_short_content_habits),
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
                    text = AppText.get(R.string.main_why_did_i_build_this_app),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AppText.get(R.string.main_even_when_i_was_busy_with),
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
                                text = AppText.get(R.string.main_why_short_content_feeds_never_end),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF85149)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppText.get(R.string.main_feeds_automatically_load_the_next_item),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bölüm 5: Çağrı & Kapanış
                Text(
                    text = AppText.get(R.string.main_awaydoomscrollin_was_created_to_make_room),
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
            modifier = Modifier.size(70.dp).heroEntrance(),
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
            text = AppText.get(R.string.main_how_does_protection_work),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = AppText.get(R.string.main_clear_boundaries_for_feeds_and_short),
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
                        text = AppText.get(R.string.main_how_it_works_2),
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
                    isEn = isEn,
                    modes = ProtectionCopy.instagramScope()
                )

                // 2. TikTok
                AppMechanismRow(
                    iconRes = R.drawable.ic_tiktok,
                    iconTint = Color(0xFF00F2FE),
                    title = "TikTok",
                    description = ProtectionCopy.tiktok()
                )

                // 3. YouTube Shorts
                AppMechanismRow(
                    iconRes = R.drawable.ic_youtube,
                    iconTint = Color(0xFFFF0055),
                    title = "YouTube",
                    isEn = isEn,
                    modes = ProtectionCopy.youtubeScope()
                )

                // 4. On-Device Shield
                AppMechanismRow(
                    iconRes = R.drawable.ic_shield,
                    iconTint = Color(0xFF00FF87),
                    title = AppText.get(R.string.main_on_device_protection),
                    description = AppText.get(R.string.main_screen_information_is_processed_on_your)
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
    description: String = "",
    isEn: Boolean = false,
    modes: ProtectionCopy.ModeScope? = null
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
                text = modes?.both ?: description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 15.sp
            )
            if (modes != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ModeScopeBlock(
                    label = AppText.get(R.string.main_balanced),
                    accent = Color(0xFF00FF87),
                    text = modes.balanced
                )
                Spacer(modifier = Modifier.height(6.dp))
                ModeScopeBlock(
                    label = AppText.get(R.string.main_strict),
                    accent = Color(0xFFFF4D6D),
                    text = modes.strict
                )
            }
        }
    }
}

/**
 * What protection covers in each app, behind one entry point: three app
 * buttons switch the content in place instead of a separate dialog per app.
 * Instagram and YouTube show both modes, the current one marked. Drawn in
 * the app's own card style with a fixed body height, so switching apps never
 * resizes the window or moves the buttons.
 */
@Composable
private fun ProtectionScopeDialog(
    isEn: Boolean,
    instagramMode: ProtectionMode,
    youtubeMode: ProtectionMode,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(ProtectedApp.INSTAGRAM) }
    val bodyText = Color.White.copy(alpha = 0.82f)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Anchored to the top: the card wraps its content (no empty space
        // under a short app) and grows downwards, so the title and the app
        // buttons never move when switching apps.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40)),
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 110.dp, bottom = 24.dp)
                .fillMaxWidth()
                .animateContentSize()
                // Taps on the card itself must not dismiss it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield),
                        contentDescription = null,
                        tint = Color(0xFFFFB703),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AppText.get(R.string.main_protection_scope),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = AppText.get(R.string.main_close),
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // A light track in the card's own tone: the buttons read as one
                // segmented control, not as a dark hole cut into the card.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(12.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf(
                        Triple(ProtectedApp.INSTAGRAM, R.drawable.ic_instagram, Color(0xFFE1306C)),
                        Triple(ProtectedApp.TIKTOK, R.drawable.ic_tiktok, Color(0xFF00F2FE)),
                        Triple(ProtectedApp.YOUTUBE, R.drawable.ic_youtube, Color(0xFFFF0055))
                    ).forEach { (app, iconRes, accent) ->
                        val isSelected = selected == app
                        Surface(
                            onClick = { selected = app },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.07f) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { this.selected = isSelected }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) accent else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = InstalledApps.displayName(app),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Wraps its content; scrolls only on very small screens.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val scope = when (selected) {
                        ProtectedApp.INSTAGRAM -> ProtectionCopy.instagramScope()
                        ProtectedApp.YOUTUBE -> ProtectionCopy.youtubeScope()
                        ProtectedApp.TIKTOK -> null
                    }
                    if (scope == null) {
                        val tiktok = ProtectionCopy.tiktokScope()
                        Text(
                            text = AppText.get(R.string.main_one_protection_setting),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppText.get(R.string.main_protection_takes_you_out_of_the),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = bodyText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ModeScopeBlock(
                            label = AppText.get(R.string.main_stopped),
                            accent = Color(0xFFFF4D6D),
                            text = tiktok.stopped
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ModeScopeBlock(
                            label = AppText.get(R.string.main_stays_open),
                            accent = Color(0xFF00FF87),
                            text = tiktok.open
                        )
                    } else {
                        val current = if (selected == ProtectedApp.INSTAGRAM) instagramMode else youtubeMode
                        Text(
                            text = AppText.get(R.string.main_both_modes),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scope.both,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = bodyText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val currentLabel = AppText.get(R.string.main_current_mode)
                        ModeScopeBlock(
                            label = AppText.get(R.string.main_balanced),
                            accent = Color(0xFF00FF87),
                            text = scope.balanced,
                            current = current == ProtectionMode.BALANCED,
                            currentLabel = currentLabel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ModeScopeBlock(
                            label = AppText.get(R.string.main_strict),
                            accent = Color(0xFFFF4D6D),
                            text = scope.strict,
                            current = current == ProtectionMode.STRICT,
                            currentLabel = currentLabel
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = ProtectionCopy.detectionNote(),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color.White.copy(alpha = 0.42f)
                )
            }
        }
        }
    }
}

/** One mode's additions, set apart with its own label and accent. */
@Composable
private fun ModeScopeBlock(
    label: String,
    accent: Color,
    text: String,
    // null: no mode choice involved; true/false: the user's current mode or not.
    current: Boolean? = null,
    currentLabel: String = ""
) {
    // The other mode stays readable but steps back behind the current one.
    val dimmed = current == false
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = if (current == true) 0.10f else if (dimmed) 0.03f else 0.06f),
        border = androidx.compose.foundation.BorderStroke(
            if (current == true) 1.2.dp else 0.8.dp,
            accent.copy(alpha = if (current == true) 0.7f else if (dimmed) 0.18f else 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent.copy(alpha = if (dimmed) 0.6f else 1f),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f)
                )
                if (current == true) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = currentLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.55f else 0.8f),
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
            modifier = Modifier.size(70.dp).heroEntrance(),
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
            text = AppText.get(R.string.main_what_it_can_and_cannot_do),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = AppText.get(R.string.main_here_s_what_protection_covers_and),
            fontSize = 12.sp,
            lineHeight = 16.sp,
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
                        AppText.get(R.string.main_what_protection_does),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF87),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // What each app covers is the previous step; this one is about
                // how protection works and how your information is handled.
                MatrixBullet(AppText.get(R.string.main_all_analysis_and_protection_happen_directly))
                MatrixBullet(AppText.get(R.string.main_protection_reads_the_structure_of_the))
                MatrixBullet(AppText.get(R.string.main_you_can_turn_protection_off_at))
                MatrixBullet(AppText.get(R.string.main_changes_to_instagram_tiktok_or_youtube))
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
                        AppText.get(R.string.main_what_it_never_does_or_collects),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                MatrixBullet(AppText.get(R.string.main_does_not_save_on_screen_text), isNegative = true)
                MatrixBullet(AppText.get(R.string.main_does_not_include_your_name_messages), isNegative = true)
                MatrixBullet(AppText.get(R.string.main_does_not_show_ads_or_sell), isNegative = true)
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
    val installedApps = rememberInstalledApps()
    var isInstaEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.INSTAGRAM)) }
    var isTiktokEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.TIKTOK)) }
    var isYoutubeEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.YOUTUBE)) }
    var instagramMode by remember { mutableStateOf(ProtectionPreferences.getMode(prefs, ProtectedApp.INSTAGRAM)) }
    var youtubeMode by remember { mutableStateOf(ProtectionPreferences.getMode(prefs, ProtectedApp.YOUTUBE)) }
    var showTelemetryDetails by remember { mutableStateOf(false) }
    var showTelemetryConsent by remember { mutableStateOf(false) }
    var isTelemetryOn by remember { mutableStateOf(TelemetryManager.isTelemetryEnabled(context)) }

    if (showTelemetryDetails) {
        ScrollableTextDialog(
            isEn = isEn,
            title = AppText.get(R.string.main_optional_usage_data),
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
            onDecline = {
                TelemetryManager.setTelemetryEnabled(context, false)
                isTelemetryOn = false
                showTelemetryConsent = false
            }
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(70.dp).heroEntrance(),
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
            text = AppText.get(R.string.main_apps_and_preferences),
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = AppText.get(R.string.main_choose_the_apps_where_protection_will),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Installed apps get a card; missing ones follow as NotInstalledAppRow.
        // 1. INSTAGRAM COMPACT CARD
        if (ProtectedApp.INSTAGRAM in installedApps) {
            CompactOnboardingAppCard(
                appName = "Instagram",
                subtitle = AppText.get(R.string.main_feeds_plus_reels_and_stories_by),
                iconRes = R.drawable.ic_instagram,
                brandColor = Color(0xFFE1306C),
                isEnabled = isInstaEnabled,
                isBeta = true,
                isEn = isEn,
                protectionMode = instagramMode,
                modeDescription = ProtectionCopy.modeSummary(ProtectedApp.INSTAGRAM, instagramMode),
                onModeChange = { mode ->
                    if (ProtectionPreferences.setMode(prefs, ProtectedApp.INSTAGRAM, mode)) {
                        instagramMode = mode
                    }
                },
                onToggle = { enabled ->
                    if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.INSTAGRAM, enabled)) {
                        isInstaEnabled = enabled
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2. TIKTOK COMPACT CARD
        if (ProtectedApp.TIKTOK in installedApps) {
            CompactOnboardingAppCard(
                appName = "TikTok",
                subtitle = AppText.get(R.string.main_video_feed_scrolling),
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
        }

        // 3. YOUTUBE SHORTS COMPACT CARD
        if (ProtectedApp.YOUTUBE in installedApps) {
            CompactOnboardingAppCard(
                appName = "YouTube",
                subtitle = AppText.get(R.string.main_home_feed_and_shorts),
                iconRes = R.drawable.ic_youtube,
                brandColor = Color(0xFFFF0055),
                isEnabled = isYoutubeEnabled,
                isBeta = true,
                isEn = isEn,
                protectionMode = youtubeMode,
                modeDescription = ProtectionCopy.modeSummary(ProtectedApp.YOUTUBE, youtubeMode),
                onModeChange = { mode ->
                    if (ProtectionPreferences.setMode(prefs, ProtectedApp.YOUTUBE, mode)) {
                        youtubeMode = mode
                    }
                },
                onToggle = { enabled ->
                    if (ProtectionPreferences.setEnabled(prefs, ProtectedApp.YOUTUBE, enabled)) {
                        isYoutubeEnabled = enabled
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        ProtectedApp.entries.filterNot(installedApps::contains).forEach { app ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                NotInstalledAppRow(
                    isEn = isEn,
                    app = app,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    textColor = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                            text = AppText.get(R.string.main_help_improve_protection_optional),
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
                        colors = appSwitchColors(MaterialTheme.colorScheme.primary)
                    )
                }

                Text(
                    text = AppText.get(R.string.main_if_you_turn_this_on_a),
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
                        text = AppText.get(R.string.main_see_exactly_what_is_shared),
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
    isEn: Boolean = false,
    protectionMode: ProtectionMode? = null,
    modeDescription: String? = null,
    onModeChange: ((ProtectionMode) -> Unit)? = null,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = appSwitchColors(brandColor)
                )
            }

            if (isEnabled && protectionMode != null && onModeChange != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OnboardingProtectionModeSelector(
                    isEn = isEn,
                    mode = protectionMode,
                    description = modeDescription.orEmpty(),
                    accent = brandColor,
                    onModeChange = onModeChange
                )
            }
        }
    }
}

@Composable
private fun OnboardingProtectionModeSelector(
    isEn: Boolean,
    mode: ProtectionMode,
    description: String,
    accent: Color,
    onModeChange: (ProtectionMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp)
    ) {
        Text(
            text = AppText.get(R.string.main_protection_mode),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Same segmented style as the Apps tab and the Analytics periods.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(10.dp))
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(ProtectionMode.BALANCED, ProtectionMode.STRICT).forEach { option ->
                val selected = mode == option
                val label = when (option) {
                    ProtectionMode.BALANCED -> AppText.get(R.string.main_balanced_2)
                    ProtectionMode.STRICT -> AppText.get(R.string.main_strict_2)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) accent.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .selectable(
                            selected = selected,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onModeChange(option) }
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
            }
        }
        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
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
            modifier = Modifier.size(70.dp).heroEntrance(),
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
            text = AppText.get(R.string.main_turn_on_protection),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = AppText.get(R.string.main_accessibility_is_required_for_protection_notific),
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
                    Text(AppText.get(R.string.main_accessibility_access), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    AppText.get(R.string.main_used_to_detect_protected_apps_and),
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
                                AppText.get(R.string.main_access_enabled),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            AppText.get(R.string.main_open_settings),
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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lightbulb),
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = getAccessibilityGuidanceTip(isEn),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (!isAccessibilityActive) {
            Spacer(modifier = Modifier.height(10.dp))

            // BAĞIMSIZ VE DİKKAT ÇEKİCİ KISITLANMIŞ AYAR & ÇÖZÜM KARTI
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F1523),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFFFB703).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB703).copy(alpha = 0.15f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lightbulb),
                                    contentDescription = null,
                                    tint = Color(0xFFFFB703),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = AppText.get(R.string.main_can_t_toggle_or_restricted_setting),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = AppText.get(R.string.main_if_android_shows_a_restricted_setting),
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFFFFB703).copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val guideUrl = AppText.get(R.string.main_https_awaydoomscrollin_com_en_guide)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guideUrl)))
                                } catch (_: Exception) {}
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_link_external),
                                contentDescription = null,
                                tint = Color(0xFFFFB703),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = AppText.get(R.string.main_open_guide),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB703)
                            )
                        }
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
                        Text(AppText.get(R.string.main_notifications_optional), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        AppText.get(R.string.main_receive_protection_status_and_permission_reminde),
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
                                    AppText.get(R.string.main_notifications_enabled),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                AppText.get(R.string.main_allow_notifications),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Numbered after Notifications, which only exists on Android 13+.
        BackgroundRunCard(
            isEn = isEn,
            stepNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 3 else 2
        )

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
                        text = AppText.get(R.string.main_privacy_terms),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F2FE)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppText.get(R.string.main_the_app_is_licensed_under_gpl),
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Each link gets at most half the row and wraps between words:
                // the Russian labels did not fit side by side, and the second one
                // was broken into single letters (Galaxy S23).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/privacy"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShrinkToFitLabel(
                                AppText.get(R.string.main_privacy_policy),
                                fontSize = 11.sp,
                                color = Color(0xFF00F2FE),
                                modifier = Modifier.weight(1f, fill = false)
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
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShrinkToFitLabel(
                                AppText.get(R.string.main_terms_of_use),
                                fontSize = 11.sp,
                                color = Color(0xFF00F2FE),
                                modifier = Modifier.weight(1f, fill = false)
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

/**
 * A short label that must stay whole: when it would break inside a word or
 * run past [maxLines], the font shrinks in half-point steps down to
 * [minFontSize]. Single long words ("конфиденциальности") were cut off or
 * split into letters on a Galaxy S23. Nothing changes where the text fits.
 */
@Composable
private fun ShrinkToFitLabel(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    var settled by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = size,
        lineHeight = (size.value * 1.25f).sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { layout ->
            val splitWord = (0 until layout.lineCount - 1).any { line ->
                val end = layout.getLineEnd(line)
                end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
            }
            if ((layout.hasVisualOverflow || splitWord) && size.value > minFontSize.value) {
                size = (size.value - 0.5f).sp
            } else {
                settled = true
            }
        }
    )
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
                    buttonText ?: AppText.get(R.string.main_got_it),
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
fun FeedbackSubmissionDialog(
    manufacturer: String,
    model: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE) }
    val isEn = getAppLanguage(prefs) != "tr"
    val appVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
    
    val categories = remember(isEn) {
        listOf(
            Triple("bug", R.drawable.ic_bug_report, AppText.get(R.string.main_bug)),
            Triple("idea", R.drawable.ic_lightbulb, AppText.get(R.string.main_idea)),
            Triple("general", R.drawable.ic_chat, AppText.get(R.string.main_general))
        )
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    var attachLog by remember { mutableStateOf(false) }
    val logOn = remember { SupportDiagnostics.isEnabled(context) }
    // A word or two ("send") cannot be answered; ask for a sentence.
    val isFormValid = feedbackText.trim().length >= 10

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
                    text = AppText.get(R.string.main_send_feedback),
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
                    placeholder = { Text(AppText.get(R.string.main_describe_your_issue_or_suggestion), fontSize = 11.5.sp, lineHeight = 15.sp) },
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

                // Most bug reports cannot be diagnosed without the log, and
                // most users never see our reply in an inbox they do not read.
                if (categories[selectedCategoryIndex].first == "bug") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = logOn) { attachLog = !attachLog }
                    ) {
                        Checkbox(
                            checked = attachLog && logOn,
                            onCheckedChange = { attachLog = it },
                            enabled = logOn
                        )
                        Text(
                            text = AppText.get(R.string.main_feedback_attach_log),
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (logOn) Color(0xFFFFB800)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (!logOn) {
                        Text(
                            text = AppText.get(R.string.main_feedback_log_off_note),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = AppText.get(R.string.main_feedback_reply_note),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
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
                        text = AppText.get(R.string.main_device_info_x_x_android_x, manufacturer, model, Build.VERSION.RELEASE),
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp,
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
                    val message = AppText.get(R.string.main_category_x_user_message_x_device, currentCat, feedbackText, manufacturer, model, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, appVersionName)
                    // Only when the user ticked the box, and only in the draft:
                    // nothing leaves the phone unless they send the email.
                    // The newest part is kept so the mailto link stays short
                    // enough for every mail app.
                    val body = if (attachLog && logOn) {
                        val report = SupportDiagnostics.buildReport(context, english = true)
                        message + "\n\n" + (if (report.length > 12_000) "…\n" + report.takeLast(12_000) else report)
                    } else message

                    try {
                        val mailtoUrl = "mailto:support@awaydoomscrollin.com" +
                                "?subject=" + Uri.encode(subject) +
                                "&body=" + Uri.encode(body)
                        
                        val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUrl)).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@awaydoomscrollin.com"))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        
                        context.startActivity(Intent.createChooser(mailIntent, AppText.get(R.string.main_send_email_with)))
                        
                        Toast.makeText(
                            context,
                            AppText.get(R.string.main_opening_your_email_app),
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        prefs.edit().putLong("last_feedback_time_ms", System.currentTimeMillis()).apply()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            AppText.get(R.string.main_mail_client_not_found_please_email),
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
                        text = AppText.get(R.string.main_send_email),
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
                Text(AppText.get(R.string.main_cancel), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
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
    val isEn = currentLang != "tr"

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
            title = AppText.get(R.string.main_optional_usage_data),
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
            onDecline = {
                TelemetryManager.setTelemetryEnabled(context, false)
                isTelemetryOnInAbout = false
                showTelemetryConsent = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .tabScroll(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            DashboardSectionHeader(
                title = AppText.get(R.string.main_about),
                subtitle = AppText.get(R.string.main_app_information_privacy_and_support)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. KATEGORİ: UYGULAMA KÜNYESİ
            Text(
                text = AppText.get(R.string.main_app_information),
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
                                    contentDescription = AppText.get(R.string.main_app_logo),
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
                                text = AppText.get(R.string.main_version_x, appVersion),
                                fontSize = 11.5.sp,
                                color = Color(0xFF00F2FE),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = AppText.get(R.string.main_break_the_scrolling_loop_without_losing),
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
                                    AppText.get(R.string.main_on_device),
                                    AppText.get(R.string.main_screen_content_stays_private)
                                ),
                                Color(0xFF00F2FE)
                            ),
                            Triple(
                                R.drawable.ic_slash_ban,
                                Pair(
                                    AppText.get(R.string.main_ad_free),
                                    AppText.get(R.string.main_no_ads_or_subscriptions)
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
                                    text = AppText.get(R.string.main_open_source),
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
                                        text = AppText.get(R.string.main_view_on_github),
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

            // 2. KATEGORİ: DİL SEÇİMİ
            Text(
                text = AppText.get(R.string.main_language),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // DİL SEÇİMİ: every interface language, each named in its own
            // language, three per row. A pick is the user's own choice and is
            // never changed by the app afterwards (see AppLanguage.resolve).
            AppLanguage.entries.chunked(3).forEachIndexed { rowIndex, rowLanguages ->
                if (rowIndex > 0) Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowLanguages.forEach { language ->
                        val selected = currentLang == language.code
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color(0xFF0F1523),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (selected) Color(0xFF00F2FE) else Color(0xFF1E2A40)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = selected,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                    onClick = {
                                        setAppLanguage(prefs, language)
                                        currentLang = language.code
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    text = language.nativeName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = if (selected) Color(0xFF00F2FE) else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    // A shorter last row keeps the same chip width as the rows above.
                    repeat(3 - rowLanguages.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. KATEGORİ: BAĞLANTILAR
            Text(
                text = AppText.get(R.string.main_links),
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
                                text = AppText.get(R.string.main_updates_and_app_information),
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
                                text = AppText.get(R.string.main_developer_and_other_projects),
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
                text = AppText.get(R.string.main_support_information),
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
                                AppText.get(R.string.main_send_feedback_or_report_a_bug),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // GİZLİLİK POLİTİKASI & KULLANIM ŞARTLARI YAN YANA (YARI YARIYA)
                    // Long labels (Russian, Italian, French) take two lines instead of
                    // being cut off; both buttons keep the same height.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/privacy"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
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
                                ShrinkToFitLabel(
                                    AppText.get(R.string.main_privacy_policy),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF00F2FE),
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://awaydoomscrollin.com/terms"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
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
                                ShrinkToFitLabel(
                                    AppText.get(R.string.main_terms_of_use),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF00F2FE),
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. KATEGORİ: REHBER
            Text(
                text = AppText.get(R.string.main_guide),
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
                            text = AppText.get(R.string.main_setup_and_protection_guide),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = AppText.get(R.string.main_review_setup_steps_protection_modes_and),
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

            // 6. KATEGORİ: TOPLULUK KATKISI
            Text(
                text = AppText.get(R.string.main_optional_usage_data_2),
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
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00F2FE).copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_handshake),
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppText.get(R.string.main_help_improve_protection),
                                fontSize = 13.5.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = AppText.get(R.string.main_optional_device_and_usage_summary),
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                lineHeight = 13.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
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
                            colors = appSwitchColors(Color(0xFF00F2FE))
                        )
                    }

                    Text(
                        text = AppText.get(R.string.main_if_you_turn_this_on_a_2),
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.68f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    Text(
                        text = if (isTelemetryOnInAbout) {
                            AppText.get(R.string.main_on_automatic_attempts_are_limited_to)
                        } else {
                            AppText.get(R.string.main_off_no_report_is_sent_to)
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
                            text = AppText.get(R.string.main_see_shared_data_and_retention),
                            color = Color(0xFF00F2FE),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 7. KATEGORİ: DESTEK
            Text(
                text = AppText.get(R.string.main_support),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            SupportLogCard(isEn = isEn)

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
    onReopenOnboarding: (Int) -> Unit,
    modeIntroAllowed: Boolean = true
) {
    val context = LocalContext.current
    val tabShiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.roundToPx() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var currentLang by remember { mutableStateOf(getAppLanguage(prefs)) }
    var showModeIntro by remember { mutableStateOf(ModeIntroNotice.isPending(prefs)) }

    if (showModeIntro && modeIntroAllowed) {
        ModeIntroDialog(
            isEn = currentLang != "tr",
            onChooseModes = {
                ModeIntroNotice.markSeen(prefs)
                showModeIntro = false
                selectedTab = 1
            },
            onLater = {
                ModeIntroNotice.markSeen(prefs)
                showModeIntro = false
            }
        )
    }
    var showAccessibilityConsent by remember { mutableStateOf(false) }
    
    val notificationQueue = remember { mutableStateListOf<UnlockedNotificationItem>() }
    var activeNotification by remember { mutableStateOf<UnlockedNotificationItem?>(null) }
    var showJourneyDialogFromNotification by remember { mutableStateOf(false) }
    var notificationTargetTab by remember { mutableIntStateOf(0) }

    val isEn = currentLang != "tr"

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
            val title = AppText.get(R.string.main_new_tier_x, currentTier.name)
            val desc = AppText.plural(R.plurals.tier_reached, currentTier.minDays,
                currentTier.level, currentTier.minDays)
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
                val title = AppText.get(R.string.main_achievement_unlocked_x, newAch.title)
                val desc = newAch.desc
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
                        label = AppText.get(R.string.main_home),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_apps,
                        label = AppText.get(R.string.main_apps),
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_analytics,
                        label = AppText.get(R.string.main_analytics),
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    CyberNavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_nav_about,
                        // Short form: the tab is a quarter of the bar.
                        label = AppText.get(R.string.nav_about),
                        isSelected = selectedTab == 3,
                        onClick = { selectedTab = 3 }
                    )
                }
            }
        }
    ) { paddingValues ->
        // Content runs to the screen bottom and slides behind the floating
        // bar; each tab adds the bar's height to the end of its scroll.
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomBarInset provides paddingValues.calculateBottomPadding()) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { sharedAxisX(forward = targetState > initialState, shiftPx = tabShiftPx) },
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
            }

            // Content fades into the background just above the floating bar,
            // and the area around and under the bar stays solid: no hard cut
            // line, and no content peeking out below the menu.
            val barInset = paddingValues.calculateBottomPadding()
            val fade = 32.dp
            val background = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(barInset + fade)
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0f),
                            fade / (barInset + fade) to background,
                            1f to background
                        )
                    )
            )

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
    val accent = Color(0xFF00F2FE)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.18f else 0.0f,
        animationSpec = tween(durationMillis = 250),
        label = "NavGlow"
    )
    // Border, tint and a slight icon lift follow the selection instead of snapping.
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.65f else 0.0f,
        animationSpec = tween(durationMillis = 250),
        label = "NavBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) accent else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(durationMillis = 250),
        label = "NavContent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "NavIconScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = glowAlpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = borderAlpha)),
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
                tint = contentColor,
                modifier = Modifier.size(19.dp).scale(iconScale)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
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
    serviceStuck: Boolean = false,
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
                    AppText.get(R.string.main_protection_on)
                } else {
                    AppText.get(R.string.main_permission_required)
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
                text = AppText.plural(R.plurals.blocks_count, todayInterventions),
                fontSize = if (todayInterventions >= 10_000) 32.sp else 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = AppText.get(R.string.main_protection_stepped_in_today),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = AppText.get(R.string.main_protection_is_off),
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                // HyperOS can stop the service while its switch still shows on;
                // only turning it off and on brings it back.
                text = AppText.get(
                    if (serviceStuck) R.string.main_protection_stuck_hint
                    else R.string.main_turn_on_accessibility_so_awaydoomscrollin_can
                ),
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
                        text = AppText.get(R.string.main_turn_on_protection_2),
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
        // Same range format and wording as the Analysis tab.
        val timeRange = PeakHourStats.formatRange(h)

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
                    text = AppText.get(R.string.main_busiest_hour_today_x, timeRange),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFF0055),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = RecentBlockTime.peakHourBlocks(cnt),
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
    val nameRes: Int,
    val iconRes: Int,
    val color: Color,
    val descRes: Int,
    val benefitRes: Int
) {
    /** Texts in the app's language (res/values*: tier_<level>_name/desc/benefit). */
    val name: String get() = AppText.get(nameRes)
    val desc: String get() = AppText.get(descRes)
    val neuroBenefit: String get() = AppText.get(benefitRes)
}

val ALL_FOCUS_TIERS = listOf(
    FocusTier(
        level = 1, minDays = 0, maxDays = 0,
        nameRes = R.string.tier_1_name,
        iconRes = R.drawable.ic_tier_seed,
        color = Color(0xFF00F2FE),
        descRes = R.string.tier_1_desc,
        benefitRes = R.string.tier_1_benefit
    ),
    FocusTier(
        level = 2, minDays = 1, maxDays = 2,
        nameRes = R.string.tier_2_name,
        iconRes = R.drawable.ic_tier_spark,
        color = Color(0xFF00FF87),
        descRes = R.string.tier_2_desc,
        benefitRes = R.string.tier_2_benefit
    ),
    FocusTier(
        level = 3, minDays = 3, maxDays = 5,
        nameRes = R.string.tier_3_name,
        iconRes = R.drawable.ic_tier_fire,
        color = Color(0xFFFF5500),
        descRes = R.string.tier_3_desc,
        benefitRes = R.string.tier_3_benefit
    ),
    FocusTier(
        level = 4, minDays = 6, maxDays = 9,
        nameRes = R.string.tier_4_name,
        iconRes = R.drawable.ic_tier_bolt,
        color = Color(0xFFFFB700),
        descRes = R.string.tier_4_desc,
        benefitRes = R.string.tier_4_benefit
    ),
    FocusTier(
        level = 5, minDays = 10, maxDays = 13,
        nameRes = R.string.tier_5_name,
        iconRes = R.drawable.ic_tier_water,
        color = Color(0xFF00E5FF),
        descRes = R.string.tier_5_desc,
        benefitRes = R.string.tier_5_benefit
    ),
    FocusTier(
        level = 6, minDays = 14, maxDays = 20,
        nameRes = R.string.tier_6_name,
        iconRes = R.drawable.ic_tier_crystal,
        color = Color(0xFFBD00FF),
        descRes = R.string.tier_6_desc,
        benefitRes = R.string.tier_6_benefit
    ),
    FocusTier(
        level = 7, minDays = 21, maxDays = 29,
        nameRes = R.string.tier_7_name,
        iconRes = R.drawable.ic_tier_brain,
        color = Color(0xFFFF2A85),
        descRes = R.string.tier_7_desc,
        benefitRes = R.string.tier_7_benefit
    ),
    FocusTier(
        level = 8, minDays = 30, maxDays = 44,
        nameRes = R.string.tier_8_name,
        iconRes = R.drawable.ic_tier_crown,
        color = Color(0xFFFFD700),
        descRes = R.string.tier_8_desc,
        benefitRes = R.string.tier_8_benefit
    ),
    FocusTier(
        level = 9, minDays = 45, maxDays = 59,
        nameRes = R.string.tier_9_name,
        iconRes = R.drawable.ic_tier_gem,
        color = Color(0xFF00F5D4),
        descRes = R.string.tier_9_desc,
        benefitRes = R.string.tier_9_benefit
    ),
    FocusTier(
        level = 10, minDays = 60, maxDays = 89,
        nameRes = R.string.tier_10_name,
        iconRes = R.drawable.ic_tier_monument,
        color = Color(0xFF9D4EDD),
        descRes = R.string.tier_10_desc,
        benefitRes = R.string.tier_10_benefit
    ),
    FocusTier(
        level = 11, minDays = 90, maxDays = 99,
        nameRes = R.string.tier_11_name,
        iconRes = R.drawable.ic_tier_galaxy,
        color = Color(0xFFFF0055),
        descRes = R.string.tier_11_desc,
        benefitRes = R.string.tier_11_benefit
    ),
    FocusTier(
        level = 12, minDays = 100, maxDays = Int.MAX_VALUE,
        nameRes = R.string.tier_12_name,
        iconRes = R.drawable.ic_tier_zen,
        color = Color(0xFFFFE600),
        descRes = R.string.tier_12_desc,
        benefitRes = R.string.tier_12_benefit
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
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val brandColor: Color,
    val currentVal: Int,
    val targetVal: Int
) {
    /** Texts in the app's language (res/values*: ach_<id>_title/desc). */
    val title: String get() = AppText.get(titleRes)
    val desc: String get() = AppText.get(descRes)
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
            titleRes = R.string.ach_ig_1_title,
            descRes = R.string.ach_ig_1_desc,
            iconRes = R.drawable.ic_instagram,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 1
        ),
        FocusAchievement(
            id = "ig_10",
            category = AchievementCategory.INSTAGRAM,
            titleRes = R.string.ach_ig_10_title,
            descRes = R.string.ach_ig_10_desc,
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 10
        ),
        FocusAchievement(
            id = "ig_50",
            category = AchievementCategory.INSTAGRAM,
            titleRes = R.string.ach_ig_50_title,
            descRes = R.string.ach_ig_50_desc,
            iconRes = R.drawable.ic_tier_bolt,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 50
        ),
        FocusAchievement(
            id = "ig_100",
            category = AchievementCategory.INSTAGRAM,
            titleRes = R.string.ach_ig_100_title,
            descRes = R.string.ach_ig_100_desc,
            iconRes = R.drawable.ic_tier_crown,
            brandColor = Color(0xFFE1306C),
            currentVal = blocksInsta,
            targetVal = 100
        ),

        // --- TIKTOK FEED ---
        FocusAchievement(
            id = "tt_1",
            category = AchievementCategory.TIKTOK,
            titleRes = R.string.ach_tt_1_title,
            descRes = R.string.ach_tt_1_desc,
            iconRes = R.drawable.ic_tiktok,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 1
        ),
        FocusAchievement(
            id = "tt_10",
            category = AchievementCategory.TIKTOK,
            titleRes = R.string.ach_tt_10_title,
            descRes = R.string.ach_tt_10_desc,
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 10
        ),
        FocusAchievement(
            id = "tt_50",
            category = AchievementCategory.TIKTOK,
            titleRes = R.string.ach_tt_50_title,
            descRes = R.string.ach_tt_50_desc,
            iconRes = R.drawable.ic_tier_crystal,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 50
        ),
        FocusAchievement(
            id = "tt_100",
            category = AchievementCategory.TIKTOK,
            titleRes = R.string.ach_tt_100_title,
            descRes = R.string.ach_tt_100_desc,
            iconRes = R.drawable.ic_tier_gem,
            brandColor = Color(0xFF00F2FE),
            currentVal = blocksTiktok,
            targetVal = 100
        ),

        // --- YOUTUBE SHORTS ---
        FocusAchievement(
            id = "yt_1",
            category = AchievementCategory.YOUTUBE,
            titleRes = R.string.ach_yt_1_title,
            descRes = R.string.ach_yt_1_desc,
            iconRes = R.drawable.ic_youtube,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 1
        ),
        FocusAchievement(
            id = "yt_10",
            category = AchievementCategory.YOUTUBE,
            titleRes = R.string.ach_yt_10_title,
            descRes = R.string.ach_yt_10_desc,
            iconRes = R.drawable.ic_shield,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 10
        ),
        FocusAchievement(
            id = "yt_50",
            category = AchievementCategory.YOUTUBE,
            titleRes = R.string.ach_yt_50_title,
            descRes = R.string.ach_yt_50_desc,
            iconRes = R.drawable.ic_target,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 50
        ),
        FocusAchievement(
            id = "yt_100",
            category = AchievementCategory.YOUTUBE,
            titleRes = R.string.ach_yt_100_title,
            descRes = R.string.ach_yt_100_desc,
            iconRes = R.drawable.ic_trophy,
            brandColor = Color(0xFFFF0000),
            currentVal = blocksYt,
            targetVal = 100
        ),

        // --- GENEL DİSİPLİN & SERİ ---
        FocusAchievement(
            id = "gen_1",
            category = AchievementCategory.GENERAL,
            titleRes = R.string.ach_gen_1_title,
            descRes = R.string.ach_gen_1_desc,
            iconRes = R.drawable.ic_tier_seed,
            brandColor = Color(0xFF00FF87),
            currentVal = totalBlocks,
            targetVal = 1
        ),
        FocusAchievement(
            id = "gen_streak_3",
            category = AchievementCategory.GENERAL,
            titleRes = R.string.ach_gen_streak_3_title,
            descRes = R.string.ach_gen_streak_3_desc,
            iconRes = R.drawable.ic_tier_fire,
            brandColor = Color(0xFFFF5500),
            currentVal = streakDays,
            targetVal = 3
        ),
        FocusAchievement(
            id = "gen_streak_7",
            category = AchievementCategory.GENERAL,
            titleRes = R.string.ach_gen_streak_7_title,
            descRes = R.string.ach_gen_streak_7_desc,
            iconRes = R.drawable.ic_tier_bolt,
            brandColor = Color(0xFFFFB700),
            currentVal = streakDays,
            targetVal = 7
        ),
        FocusAchievement(
            id = "gen_streak_21",
            category = AchievementCategory.GENERAL,
            titleRes = R.string.ach_gen_streak_21_title,
            descRes = R.string.ach_gen_streak_21_desc,
            iconRes = R.drawable.ic_tier_brain,
            brandColor = Color(0xFFFF2A85),
            currentVal = streakDays,
            targetVal = 21
        ),
        FocusAchievement(
            id = "gen_100_blocks",
            category = AchievementCategory.GENERAL,
            titleRes = R.string.ach_gen_100_blocks_title,
            descRes = R.string.ach_gen_100_blocks_desc,
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
    // The tier colour shows progress; the flame is the streak's icon everywhere
    // (Analytics uses it too). No pulsing border: a calm, static pill.
    val tier = getCurrentFocusTier(streakDays)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        color = tier.color.copy(alpha = 0.13f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, tier.color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tier_fire),
                contentDescription = null,
                tint = tier.color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = AppText.plural(R.plurals.streak_days_badge, streakDays),
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
                                    text = AppText.get(R.string.main_achievements),
                                    fontSize = 17.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = AppText.get(R.string.main_focus_tiers_special_achievements),
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
                            Triple(0, AppText.get(R.string.main_tiers), R.drawable.ic_tier_galaxy),
                            Triple(1, AppText.get(R.string.main_special_achievements), R.drawable.ic_trophy)
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
                                                        text = AppText.get(R.string.main_current_level_x, currentTier.level),
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
                                                            text = AppText.get(R.string.main_day_x, streakDays),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = currentTier.color,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = currentTier.name,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = currentTier.desc,
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
                                                    text = AppText.get(R.string.main_focus_at_this_level_x, currentTier.neuroBenefit),
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
                                                        text = AppText.get(R.string.main_next_2),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color.White.copy(alpha = 0.65f)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = AppText.get(R.string.main_level_x_x, nextTier.level, nextTier.name),
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
                                                    text = AppText.plural(R.plurals.days_left, remaining),
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
                                    text = AppText.get(R.string.main_tiers_roadmap),
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
                                                            text = "${tierItem.level}. ${tierItem.name}",
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
                                                            text = if (tierItem.maxDays == Int.MAX_VALUE) "${tierItem.minDays}+ ${AppText.get(R.string.main_days)}"
                                                                   else if (tierItem.minDays == tierItem.maxDays) "${tierItem.minDays} ${AppText.get(R.string.main_day)}"
                                                                   else "${tierItem.minDays}-${tierItem.maxDays} ${AppText.get(R.string.main_days)}",
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
                                                    text = tierItem.desc,
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
                                                        text = tierItem.neuroBenefit,
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
                                        Triple(AchievementCategory.ALL, AppText.get(R.string.main_all), null),
                                        Triple(AchievementCategory.INSTAGRAM, "Instagram", R.drawable.ic_instagram),
                                        Triple(AchievementCategory.TIKTOK, "TikTok", R.drawable.ic_tiktok),
                                        Triple(AchievementCategory.YOUTUBE, "YouTube", R.drawable.ic_youtube),
                                        Triple(AchievementCategory.GENERAL, AppText.get(R.string.main_discipline), R.drawable.ic_shield)
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
                                                            text = ach.title,
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
                                                                        text = AppText.get(R.string.main_unlocked),
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
                                                        text = ach.desc,
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
                                text = AppText.get(R.string.main_close),
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
    val serviceState = rememberProtectionServiceState()
    val isAccessibilityActive = serviceState == ProtectionServiceState.RUNNING
    val isEn = getAppLanguage(prefs) != "tr"
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
                .tabScroll(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Header: logo (its own frame, no extra box) + wordmark, streak badge on the right
            val streakDays = prefs.getInt("streak_days", 0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_splash_logo),
                        contentDescription = AppText.get(R.string.main_app_logo),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    // Soft vertical divider between the logo and the wordmark
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 9.dp)
                            .width(1.dp)
                            .height(26.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    Column {
                        // One text so the name can never break between its two colours.
                        // A longer streak badge ("12 DAYS", "100 DAYS") leaves less room:
                        // shrink the name until it fits instead of clipping its apostrophe.
                        var wordmarkSize by remember { mutableStateOf(19.sp) }
                        var wordmarkFits by remember { mutableStateOf(false) }
                        Text(
                            onTextLayout = { layout ->
                                if (layout.didOverflowWidth && wordmarkSize.value > 14f) {
                                    wordmarkSize *= 0.95f
                                } else {
                                    wordmarkFits = true
                                }
                            },
                            modifier = Modifier.drawWithContent { if (wordmarkFits) drawContent() },
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                    append("Away")
                                }
                                withStyle(SpanStyle(color = Color(0xFF00F2FE), fontWeight = FontWeight.ExtraBold)) {
                                    append("Doomscrollin'")
                                }
                            },
                            fontSize = wordmarkSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = "by Resolve Community",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                serviceStuck = serviceState == ProtectionServiceState.LISTED_NOT_RUNNING,
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

            // BORDERLESS SHIELD ACTIVITY LOG
            BorderlessShieldActivityLog(prefs = prefs)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BorderlessShieldActivityLog(prefs: android.content.SharedPreferences) {
    val isEn = getAppLanguage(prefs) != "tr"
    var rawLogs by remember { mutableStateOf(prefs.getString("recent_shield_logs", "") ?: "") }

    LaunchedEffect(Unit) {
        while (true) {
            rawLogs = prefs.getString("recent_shield_logs", "") ?: ""
            kotlinx.coroutines.delay(1500)
        }
    }

    val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    val logList = remember(rawLogs, isEn) {
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
                val blockMs = parts.getOrNull(3)?.toLongOrNull()
                val whenLabel = if (blockMs != null && blockMs > 0L) {
                    RecentBlockTime.label(blockMs, System.currentTimeMillis())
                } else {
                    AppText.get(R.string.main_at_x, time)
                }
                Triple(whenLabel, app, appCount)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
                text = AppText.get(R.string.main_recent_blocks),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (logList.isEmpty()) {
            Text(
                text = AppText.get(R.string.main_no_blocks_have_been_recorded_yet),
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                val displayList = logList.take(4)
                displayList.forEachIndexed { index, (whenLabel, app, _) ->
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
                                    text = whenLabel,
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

        // Xiaomi, Redmi and POCO: a small, quiet reminder at the end of Home.
        if (isXiaomiFamily()) {
            Spacer(modifier = Modifier.height(18.dp))
            XiaomiAutostartReminder()
        }
    }
}

/**
 * Tested on a Xiaomi 14 (HyperOS 2): clearing Recents ends the app, and only
 * with Autostart on does Android bring protection back. Autostart is off on a
 * new installation and the app cannot read it, so the reminder stays.
 */
@Composable
private fun XiaomiAutostartReminder() {
    val context = LocalContext.current
    val amber = Color(0xFFFFB800)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = amber.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, amber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                AppText.get(R.string.main_xiaomi_autostart_home),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                AppText.get(R.string.main_xiaomi_autostart_button),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = amber,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { openAutostartSettings(context) }
                    .padding(vertical = 4.dp)
            )
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
    val isEn = getAppLanguage(prefs) != "tr"
    val isProtectionRunning = rememberAccessibilityStatus()
    val installedApps = rememberInstalledApps()
    var isInstagramEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.INSTAGRAM)) }
    var isTiktokEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.TIKTOK)) }
    var isYoutubeEnabled by remember { mutableStateOf(ProtectionPreferences.isEnabled(prefs, ProtectedApp.YOUTUBE)) }
    var youtubeMode by remember { mutableStateOf(ProtectionPreferences.getMode(prefs, ProtectedApp.YOUTUBE)) }
    var instagramMode by remember { mutableStateOf(ProtectionPreferences.getMode(prefs, ProtectedApp.INSTAGRAM)) }
    var isInstagramSettingsOpen by remember { mutableStateOf(false) }
    var isYoutubeSettingsOpen by remember { mutableStateOf(false) }
    var pendingDisableApp by remember { mutableStateOf<String?>(null) }
    var showScopeDialog by remember { mutableStateOf(false) }

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

    if (showScopeDialog) {
        ProtectionScopeDialog(
            isEn = isEn,
            instagramMode = instagramMode,
            youtubeMode = youtubeMode,
            onDismiss = { showScopeDialog = false }
        )
    }

    if (pendingDisableApp != null) {
        val pendingAppName = when (pendingDisableApp) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "youtube" -> "YouTube"
            else -> AppText.get(R.string.main_this_app)
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
                        text = AppText.get(R.string.main_turn_off_protection),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            },
            text = { 
                Text(
                    AppText.get(R.string.main_x_short_content_protection_will_be, pendingAppName), 
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
                                ).also {
                                    if (it) {
                                        isYoutubeEnabled = false
                                        isYoutubeSettingsOpen = false
                                    }
                                }
                            }
                            else -> false
                        }

                        if (disabled) {
                            NotificationHelper.showShieldStatusNotification(
                                context,
                                AppText.get(R.string.main_x_protection_is_off, pendingAppName),
                                AppText.get(R.string.main_short_content_protection_is_disabled_and),
                                StatusNotificationPolicy.APP_OFF_ID
                            )
                        } else {
                            android.util.Log.e("ProtectionPreferences", "$pendingAppName koruma tercihi kaydedilemedi.")
                        }

                        pendingDisableApp = null
                    }
                ) {
                    Text(AppText.get(R.string.main_turn_off), color = Color(0xFFFF0055), fontWeight = FontWeight.ExtraBold)
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
                        Text(AppText.get(R.string.main_keep_protection_on), fontWeight = FontWeight.ExtraBold)
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
            .tabScroll(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        DashboardSectionHeader(
            title = AppText.get(R.string.main_protected_apps),
            subtitle = AppText.get(R.string.main_choose_apps_you_want_to_protect)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // One entry point for every app's scope (no per-app Scope chips).
        // The card carries its own title, so no section header above it.
        Surface(
            onClick = { showScopeDialog = true },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF161204),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
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
                            text = AppText.get(R.string.main_protection_scope_2),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFB703),
                            letterSpacing = 0.4.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = AppText.get(R.string.main_read),
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
                        text = AppText.get(R.string.main_what_is_covered_and_what_stays),
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
            text = AppText.get(R.string.main_apps_2),
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
            youtubeMode = youtubeMode,
            instagramMode = instagramMode,
            isInstagramSettingsOpen = isInstagramSettingsOpen,
            onInstagramSettingsClick = { isInstagramSettingsOpen = !isInstagramSettingsOpen },
            onInstagramModeChange = { mode ->
                if (ProtectionPreferences.setMode(prefs, ProtectedApp.INSTAGRAM, mode)) {
                    instagramMode = mode
                }
            },
            isYoutubeSettingsOpen = isYoutubeSettingsOpen,
            onYoutubeSettingsClick = { isYoutubeSettingsOpen = !isYoutubeSettingsOpen },
            onYoutubeModeChange = { mode ->
                if (ProtectionPreferences.setMode(prefs, ProtectedApp.YOUTUBE, mode)) {
                    youtubeMode = mode
                }
            },
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
            },
            installed = installedApps
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
    youtubeMode: ProtectionMode,
    instagramMode: ProtectionMode,
    isInstagramSettingsOpen: Boolean,
    onInstagramSettingsClick: () -> Unit,
    onInstagramModeChange: (ProtectionMode) -> Unit,
    isYoutubeSettingsOpen: Boolean,
    onYoutubeSettingsClick: () -> Unit,
    onYoutubeModeChange: (ProtectionMode) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    installed: Set<ProtectedApp> = ProtectedApp.entries.toSet()
) {
    // Installed apps first, then the ones missing on this phone; a divider
    // separates every two listed rows.
    var rowsShown = 0
    val divider = @Composable {
        if (rowsShown++ > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Item 1: Instagram
        if (ProtectedApp.INSTAGRAM in installed) {
            divider()
            BorderlessTargetAppRow(
                isEn = isEn,
                name = "Instagram",
                iconRes = R.drawable.ic_instagram,
                isChecked = isInstagramEnabled,
                isProtectionRunning = isProtectionRunning,
                brandColor = Color(0xFFE1306C),
                isBeta = true,
                onCheckedChange = { onToggleApp("instagram", it) }
            )
            if (isInstagramEnabled) {
                ProtectionModeDropdownButton(isEn, "Instagram", instagramMode,
                    isInstagramSettingsOpen, onInstagramSettingsClick, Color(0xFFE1306C)) {
                    InstagramModeSettingsCard(isEn, instagramMode, onInstagramModeChange)
                }
                InstagramOpenInMessagesRow(isEn)
            }
        }

        // Item 2: TikTok
        if (ProtectedApp.TIKTOK in installed) {
            divider()
            BorderlessTargetAppRow(
                isEn = isEn,
                name = "TikTok",
                iconRes = R.drawable.ic_tiktok,
                isChecked = isTiktokEnabled,
                isProtectionRunning = isProtectionRunning,
                brandColor = Color(0xFF00F2FE),
                isBeta = true,
                onCheckedChange = { onToggleApp("tiktok", it) }
            )
        }

        // Item 3: YouTube Shorts
        if (ProtectedApp.YOUTUBE in installed) {
            divider()
            BorderlessTargetAppRow(
                isEn = isEn,
                name = "YouTube",
                iconRes = R.drawable.ic_youtube,
                isChecked = isYoutubeEnabled,
                isProtectionRunning = isProtectionRunning,
                brandColor = Color(0xFFFF0000),
                isBeta = true,
                onCheckedChange = { onToggleApp("youtube", it) }
            )
            if (isYoutubeEnabled) {
                ProtectionModeDropdownButton(isEn, "YouTube", youtubeMode,
                    isYoutubeSettingsOpen, onYoutubeSettingsClick, Color(0xFFFF5555)) {
                    YouTubeModeSettingsCard(
                        isEn = isEn,
                        mode = youtubeMode,
                        onModeChange = onYoutubeModeChange
                    )
                }
            }
        }

        // Not installed: dimmed, no switch.
        ProtectedApp.entries.filterNot(installed::contains).forEach { app ->
            divider()
            NotInstalledAppRow(isEn, app, Modifier.padding(vertical = 12.dp))
        }
    }
}

// Opt-in and off by default: Instagram opened from outside skips the covered
// Home feed and goes to Messages. Same card style as the mode picker above.
@Composable
private fun InstagramOpenInMessagesRow(isEn: Boolean) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var on by remember { mutableStateOf(ProtectionPreferences.opensInstagramInMessages(prefs)) }
    val accent = Color(0xFFE1306C)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.025f))
            .toggleable(
                value = on,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = { value ->
                    if (ProtectionPreferences.setOpensInstagramInMessages(prefs, value)) on = value
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = AppText.get(R.string.main_open_in_messages),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = AppText.get(R.string.main_when_you_open_instagram_it_goes),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = appSwitchColors(accent)
        )
    }
}

@Composable
private fun ProtectionModeDropdownButton(
    isEn: Boolean,
    appName: String,
    mode: ProtectionMode,
    expanded: Boolean,
    onClick: () -> Unit,
    accent: Color,
    // The mode choice opens inside the same card, under the header.
    content: @Composable () -> Unit
) {
    val modeLabel = when (mode) {
        ProtectionMode.BALANCED -> AppText.get(R.string.main_balanced_2)
        ProtectionMode.STRICT -> AppText.get(R.string.main_strict_2)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.025f))
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = AppText.get(R.string.main_x_protection_mode, appName)
                    stateDescription = modeLabel + if (expanded) {
                        AppText.get(R.string.main_expanded)
                    } else {
                        AppText.get(R.string.main_collapsed)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppText.get(R.string.main_protection_mode),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(modeLabel, color = accent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    rotationZ = if (expanded) 180f else 0f
                }
            )
        }
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(0.8.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            Box(modifier = Modifier.padding(10.dp)) { content() }
        }
    }
}

// The mode picker only says what differs between the two modes; the full
// scope (what both modes cover, what stays open) lives in the scope dialog.
@Composable
private fun InstagramModeSettingsCard(
    isEn: Boolean,
    mode: ProtectionMode,
    onModeChange: (ProtectionMode) -> Unit
) {
    val detail = ProtectionCopy.modeSummary(ProtectedApp.INSTAGRAM, mode)
    CompactProtectionModeSettings(isEn, mode, detail, onModeChange, Color(0xFFE1306C))
}

@Composable
fun YouTubeModeSettingsCard(
    isEn: Boolean,
    mode: ProtectionMode,
    onModeChange: (ProtectionMode) -> Unit
) {
    val detail = ProtectionCopy.modeSummary(ProtectedApp.YOUTUBE, mode)
    CompactProtectionModeSettings(isEn, mode, detail, onModeChange, Color(0xFFFF5555))
}

@Composable
private fun CompactProtectionModeSettings(
    isEn: Boolean,
    mode: ProtectionMode,
    detail: String,
    onModeChange: (ProtectionMode) -> Unit,
    accent: Color
) {
    Column(Modifier.fillMaxWidth()) {
        // Same segmented style as the Analytics period selector.
        Row(
            Modifier.fillMaxWidth()
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(10.dp))
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (option in listOf(ProtectionMode.BALANCED, ProtectionMode.STRICT)) {
                val selected = mode == option
                val label = when (option) {
                    ProtectionMode.BALANCED -> AppText.get(R.string.main_balanced_2)
                    ProtectionMode.STRICT -> AppText.get(R.string.main_strict_2)
                }
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) accent.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .selectable(selected = selected,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onModeChange(option) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accent else Color.White.copy(alpha = 0.60f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(detail, fontSize = 10.5.sp, lineHeight = 15.sp,
            color = Color.White.copy(alpha = 0.60f))
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
                // No "On/Off" line under the name: the switch already shows
                // it, and screen readers hear it from the switch's state.
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = AppText.get(R.string.main_x_protection, name)
                stateDescription = when {
                    !isChecked -> AppText.get(R.string.main_off)
                    isProtectionRunning -> AppText.get(R.string.main_on_protection_is_running)
                    else -> AppText.get(R.string.main_on_accessibility_permission_required)
                }
            },
            colors = appSwitchColors(brandColor)
        )
    }
}


data class DayBlockDetail(
    val dayName: String,
    val dateStr: String,
    val totalBlocks: Int,
    val instagramBlocks: Int,
    val tiktokBlocks: Int,
    val youtubeBlocks: Int,
    val instagramProtectionMs: Long,
    val youtubeProtectionMs: Long = 0L
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
    val isEn = getAppLanguage(prefs) != "tr"
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
    var youtubeProtectionTodayMs by remember { mutableLongStateOf(YouTubeProtectionMetrics.todayMs(prefs)) }
    var youtubeProtectionTotalMs by remember { mutableLongStateOf(YouTubeProtectionMetrics.totalMs(prefs)) }
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
    var monthlyYoutubeMs by remember { mutableLongStateOf(0L) }
    // 0 Today, 1 This week, 2 This month, 3 All time
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
            youtubeProtectionTodayMs = YouTubeProtectionMetrics.todayMs(prefs)
            youtubeProtectionTotalMs = YouTubeProtectionMetrics.totalMs(prefs)
            allTimeInstagramBlocks = prefs.getInt("blocks_instagram", 0)
            allTimeTiktokBlocks = prefs.getInt("blocks_tiktok", 0)
            allTimeYoutubeBlocks = prefs.getInt("blocks_youtube", 0)

            val calendar = java.util.Calendar.getInstance()
            calendar.firstDayOfWeek = java.util.Calendar.MONDAY
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            
            // Short weekday names straight from the platform, in the app's
            // language (Mon / Pzt / Lun / Пн), Monday first.
            val dayLocale = java.util.Locale.forLanguageTag(AppText.language().code)
            val dayShortNames = java.time.DayOfWeek.entries.map { day ->
                day.getDisplayName(java.time.format.TextStyle.SHORT, dayLocale)
                    .removeSuffix(".")
                    .replaceFirstChar { it.titlecase(dayLocale) }
            }
            
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
                        instagramProtectionMs = InstagramProtectionMetrics.dayMs(prefs, dayStr),
                        youtubeProtectionMs = if (dayStr == today) youtubeProtectionTodayMs
                            else YouTubeProtectionMetrics.dayMs(prefs, dayStr)
                    )
                )
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            weeklyDetails = newWeeklyDetails

            // Bu ay: takvim ayının 1'inden bugüne
            val monthCal = java.util.Calendar.getInstance()
            val daysThisMonth = monthCal.get(java.util.Calendar.DAY_OF_MONTH)
            var mTotal = 0
            var mInsta = 0
            var mTiktok = 0
            var mYt = 0
            var mInstaTime = 0L
            var mYoutubeTime = 0L
            for (i in 0 until daysThisMonth) {
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
                mYoutubeTime += if (i == 0) youtubeProtectionTodayMs
                    else YouTubeProtectionMetrics.dayMs(prefs, dStr)
            }
            monthlyBlocks = mTotal
            monthlyInstagramBlocks = mInsta
            monthlyTiktokBlocks = mTiktok
            monthlyYoutubeBlocks = mYt
            monthlyInstagramMs = mInstaTime
            monthlyYoutubeMs = mYoutubeTime

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
            .tabScroll(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        DashboardSectionHeader(
            title = AppText.get(R.string.main_protection_analytics),
            subtitle = AppText.get(R.string.main_blocks_streaks_and_achievements)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Seri ve başarımlar: dönemden bağımsız, en üstte, tek kart
        val achievementsLabel = AppText.get(R.string.main_achievements_and_streak)
        Surface(
            onClick = { showJourneyDialog = true },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F1523),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = AppText.get(R.string.main_x_x_day_streak, achievementsLabel, streakDays)
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB703).copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tier_fire),
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppText.plural(R.plurals.streak_days_label, streakDays),
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFB703)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AppText.get(R.string.main_see_your_achievements_and_progress),
                        fontSize = 10.5.sp,
                        lineHeight = 13.5.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = Color(0xFFFFB703),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tek dönem seçici: Bugün / Bu hafta / Bu ay / Tüm zamanlar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1523), RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                R.string.period_today, R.string.period_this_week,
                R.string.period_this_month, R.string.period_all_time
            ).map { AppText.get(it) }
            tabs.forEachIndexed { index, label ->
                val isSelected = selectedTimeframeTab == index
                // Surface's own onClick clips the ripple to the rounded shape.
                Surface(
                    onClick = { selectedTimeframeTab = index },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF161E30) else Color.Transparent,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.35f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { selected = isSelected }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00FF87) else Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val weekInsta = weeklyDetails.sumOf { it.effectiveInsta }
        val weekTiktok = weeklyDetails.sumOf { it.effectiveTiktok }
        val weekYt = weeklyDetails.sumOf { it.effectiveYt }
        val periodBlocks = when (selectedTimeframeTab) {
            0 -> todayInterventions
            1 -> weeklyDetails.sumOf { it.chartTotal }
            2 -> monthlyBlocks
            else -> totalBlocks
        }
        val periodInstaBlocks = when (selectedTimeframeTab) {
            0 -> todayInstagramBlocks
            1 -> weekInsta
            2 -> monthlyInstagramBlocks
            else -> allTimeInstagramBlocks
        }
        val periodTiktokBlocks = when (selectedTimeframeTab) {
            0 -> todayTiktokBlocks
            1 -> weekTiktok
            2 -> monthlyTiktokBlocks
            else -> allTimeTiktokBlocks
        }
        val periodYoutubeBlocks = when (selectedTimeframeTab) {
            0 -> todayYoutubeBlocks
            1 -> weekYt
            2 -> monthlyYoutubeBlocks
            else -> allTimeYoutubeBlocks
        }
        val periodInstaMs = when (selectedTimeframeTab) {
            0 -> instagramProtectionTodayMs
            1 -> weeklyDetails.sumOf { it.instagramProtectionMs }
            2 -> monthlyInstagramMs
            else -> instagramProtectionTotalMs
        }
        val periodYoutubeMs = when (selectedTimeframeTab) {
            0 -> youtubeProtectionTodayMs
            1 -> weeklyDetails.sumOf { it.youtubeProtectionMs }
            2 -> monthlyYoutubeMs
            else -> youtubeProtectionTotalMs
        }

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
                    // A quiet period is the goal, not an empty chart.
                    val quietPeriod = periodBlocks == 0
                    Icon(
                        painter = painterResource(
                            id = if (quietPeriod) R.drawable.ic_check else R.drawable.ic_slash_ban
                        ),
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                quietPeriod -> when (selectedTimeframeTab) {
                                    0 -> AppText.get(R.string.main_no_attempts_today)
                                    1 -> AppText.get(R.string.main_no_attempts_this_week)
                                    2 -> AppText.get(R.string.main_no_attempts_this_month)
                                    else -> AppText.get(R.string.main_no_attempts_yet)
                                }
                                selectedTimeframeTab == 0 ->
                                    AppText.plural(R.plurals.blocks_count, todayInterventions)
                                else ->
                                    AppText.plural(R.plurals.blocks_count, periodBlocks)
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = when {
                                quietPeriod ->
                                    AppText.get(R.string.main_nothing_in_your_protected_apps_needed)
                                selectedTimeframeTab == 0 -> AppText.get(R.string.main_today_across_all_protected_apps)
                                selectedTimeframeTab == 1 -> AppText.get(R.string.main_this_week_from_monday_to_today)
                                selectedTimeframeTab == 2 -> AppText.get(R.string.main_this_month_from_the_1st_to)
                                else -> AppText.get(R.string.main_since_protection_was_first_turned_on)
                            },
                            fontSize = 10.5.sp,
                            lineHeight = 13.5.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppText.get(R.string.main_by_app),
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
                    // Curtain time sits in its own app's tile: no second row
                    // repeating the app names, and nothing to truncate.
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_instagram,
                        label = "Instagram",
                        count = periodInstaBlocks,
                        color = Color(0xFFE1306C),
                        curtainMs = periodInstaMs,
                        isEn = isEn
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_tiktok,
                        label = "TikTok",
                        count = periodTiktokBlocks,
                        color = Color(0xFF00F2FE),
                        isEn = isEn
                    )
                    TodayAppMetric(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_youtube,
                        label = "YouTube",
                        count = periodYoutubeBlocks,
                        color = Color(0xFFFF0000),
                        curtainMs = periodYoutubeMs,
                        isEn = isEn
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hourglass),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = AppText.get(R.string.main_measured_curtain_time_tiktok_has_no),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                // Günlük dağılım yalnızca "Bu hafta" sekmesinde, aynı kartın içinde
                if (selectedTimeframeTab == 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CyberWeeklyPillarsChart(isEn = isEn, weeklyDetails = weeklyDetails, embedded = true)
                }
            }
        }

        // Seçili dönemde en çok deneme yapılan saat
        val peakHour = remember(selectedTimeframeTab, periodBlocks) {
            val today = InstagramProtectionMetrics.dateString(System.currentTimeMillis())
            val days: Set<String>? = when (selectedTimeframeTab) {
                0 -> setOf(today)
                1 -> weeklyDetails.map { it.dateStr }.toSet()
                2 -> {
                    val monthPrefix = today.substring(0, 8)
                    val dayOfMonth = today.substring(8, 10).toInt()
                    (1..dayOfMonth).map { monthPrefix + it.toString().padStart(2, '0') }.toSet()
                }
                else -> null
            }
            PeakHourStats.busiestHour(prefs.all, days)
        }
        if (peakHour != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1523), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lightbulb),
                    contentDescription = null,
                    tint = Color(0xFF00F2FE),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // The period is named so the hour is never read as "today".
                        text = PeakHourStats.formatRange(peakHour.hour).let { range ->
                            when (selectedTimeframeTab) {
                                0 -> AppText.get(R.string.main_busiest_hour_today_x_2, range)
                                1 -> AppText.get(R.string.main_busiest_hour_this_week_x, range)
                                2 -> AppText.get(R.string.main_busiest_hour_this_month_x, range)
                                else -> AppText.get(R.string.main_busiest_hour_overall_x, range)
                            }
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = RecentBlockTime.peakHourBlocks(peakHour.blocks),
                        fontSize = 10.5.sp,
                        lineHeight = 13.5.sp,
                        color = Color.White.copy(alpha = 0.55f)
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
    color: Color,
    // Measured curtain time; null for TikTok, which has no curtain.
    curtainMs: Long? = null,
    isEn: Boolean = false
) {
    val duration = curtainMs?.let { formatMeasuredDuration(it) }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFF070A12),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f)),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(label).append(": ")
                append(AppText.plural(R.plurals.blocks_count, count))
                if (duration != null) {
                    append(AppText.get(R.string.main_curtain_time_x, duration))
                }
            }
        }
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
            // Every tile keeps the same height, with or without a curtain time.
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.height(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (duration != null) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hourglass),
                        contentDescription = null,
                        tint = color.copy(alpha = 0.8f),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = duration,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = 0.9f),
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = "—",
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
fun CyberWeeklyPillarsChart(
    isEn: Boolean = false,
    weeklyDetails: List<DayBlockDetail>,
    // Inside the stats card: no card of its own, no title repeating the
    // selected tab and no legend repeating the per-app tiles above it.
    embedded: Boolean = false
) {
    val maxVal = (weeklyDetails.maxOfOrNull { it.chartTotal } ?: 1).coerceAtLeast(1)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showChartInfo by remember { mutableStateOf(false) }

    if (showChartInfo) {
        ScrollableTextDialog(
            isEn = isEn,
            title = AppText.get(R.string.main_how_the_weekly_chart_works),
            content = AppText.get(R.string.main_each_bar_shows_that_day_s),
            iconRes = R.drawable.ic_info,
            iconTint = Color(0xFF00F2FE),
            onDismiss = { showChartInfo = false }
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (embedded) Color.Transparent else Color(0xFF0F1523),
        border = if (embedded) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A40).copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(if (embedded) 0.dp else 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (embedded) {
                    Text(
                        text = AppText.get(R.string.main_by_day),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 0.8.sp
                    )
                } else {
                    Text(
                        text = AppText.get(R.string.main_this_week),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = { showChartInfo = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = AppText.get(R.string.main_how_the_chart_works),
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Renk Açıklama Rozetleri (Minimalist Dot Legend)
            if (!embedded) Row(
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
                            AppText.get(R.string.main_older_records),
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
                                // A day's total belongs to no single app.
                                color = if (total > 0) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f)
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
                            // Summary heading stays separate from duration metrics.
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
                                        text = AppText.get(R.string.main_x_summary, selectedDetail.dayName),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = selectedDetail.topAppColor
                                    )
                                }

                            }

                            Spacer(Modifier.height(12.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color(0xFF070A12), RoundedCornerShape(11.dp))
                                    .padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = AppText.get(R.string.main_curtain_time),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                WeeklyCurtainDurationRow(
                                    "Instagram", R.drawable.ic_instagram,
                                    selectedDetail.instagramProtectionMs, Color(0xFFE1306C), isEn
                                )
                                WeeklyCurtainDurationRow(
                                    "YouTube", R.drawable.ic_youtube,
                                    selectedDetail.youtubeProtectionMs, Color(0xFFFF5555), isEn
                                )
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
                                            Text(AppText.get(R.string.main_x_x, insta, pct), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                                            Text(AppText.get(R.string.main_x_x_2, tiktok, pct), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                                            Text(AppText.get(R.string.main_x_x_3, yt, pct), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (uncategorized > 0) {
                                        val pct = (uncategorized * 100) / total
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(95.dp)) {
                                                Box(modifier = Modifier.size(13.dp).background(Color(0xFF64748B), CircleShape))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    AppText.get(R.string.main_older),
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF0F1523))) {
                                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Color(0xFF64748B)))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(AppText.get(R.string.main_x_x_4, uncategorized, pct), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = AppText.get(R.string.main_nothing_was_blocked_on_this_day),
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
private fun WeeklyCurtainDurationRow(
    appName: String,
    iconRes: Int,
    durationMs: Long,
    accent: Color,
    isEn: Boolean
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), contentDescription = null,
            tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(appName, modifier = Modifier.weight(1f), fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(formatMeasuredDuration(durationMs), fontSize = 12.sp,
            fontWeight = FontWeight.Bold, color = accent)
    }
}
