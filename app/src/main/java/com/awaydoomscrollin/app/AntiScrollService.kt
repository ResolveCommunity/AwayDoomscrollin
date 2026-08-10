package com.awaydoomscrollin.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AntiScrollService : AccessibilityService() {

    private val CHANNEL_ID = "streak_milestone_channel"

    private fun sendMilestoneNotification(streakDays: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val isEn = prefs.getString("app_language", null) == "en"

        val channelTitle = if (isEn) "Motivation Streaks" else "Motivasyon Serisi"
        val channelDesc = if (isEn) "Daily streak achievements and motivation notifications" else "Günlük seri başarıları ve motivasyon bildirimleri"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                channelTitle,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = channelDesc
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (isEn) "🔥 $streakDays Day Streak!" else "🔥 $streakDays Günlük Seri!"

        val message = if (isEn) {
            when (streakDays) {
                3 -> "Great job! You reached a 3-day streak. Your dopamine receptors are healing! 🔥"
                7 -> "1 Week without lowering the shield! You are back in control of your mind. 🔥"
                14 -> "2 Weeks! Showing incredible willpower. Keep going! 🔥"
                21 -> "21-Day rule! Your old habit is breaking. Congratulations! 🔥"
                30 -> "1 MONTH! Reached Unchained Mind level. You're a legend! 🔥"
                60 -> "2 MONTHS! Your focus is fully restored. Superb! 🔥"
                else -> return
            }
        } else {
            when (streakDays) {
                3 -> "Harika gidiyorsun! 3 günlük seriye ulaştın, dopamin reseptörlerin iyileşmeye başladı bile! 🔥"
                7 -> "1 Haftadır kalkanı indirmedin! Zihninin kontrolü tekrar sende. 🔥"
                14 -> "2 Hafta oldu! İnanılmaz bir irade örneği gösteriyorsun. 🔥"
                21 -> "21 Gün kuralı! Alışkanlığın kırılmaya başladı. Kutlarız! 🔥"
                30 -> "1 AY! Özgür Zihin seviyesine ulaştın. Sen bir efsanesin! 🔥"
                60 -> "2 AY! Zihnin tamamen yenilendi. Süpersin! 🔥"
                else -> return
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(1001, builder.build())
        }
    }

    fun showShieldStatusNotification(context: Context, title: String, message: String, notificationId: Int = 1002) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "shield_status_channel"

            val prefs = context.getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val isEn = prefs.getString("app_language", null) == "en"

            val channelTitle = if (isEn) "Shield & Permission Status" else "Kalkan ve İzin Durumu"
            val channelDesc = if (isEn) "Shield activity and accessibility notifications" else "Kalkan aktiflik ve erişilebilirlik bildirimleri"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelTitle,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = channelDesc
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            Log.e("AntiScrollService", "Notification posting error", e)
        }
    }

    private val TAG = "AntiScrollService"
    private var isPunishing = false
    private var currentTargetPackage = "com.instagram.android"
    private var lastPunishTime = 0L
    private var punishStartTime = 0L
    private var lastHomeActionTime = 0L

    private var lastFeedText = ""
    private var lastFeedChangePunishTime: Long = 0L
    private var forceStopClicked = false
    private var lastSettingsClickTime = 0L
    private var lastInstagramTransitionTime = 0L
    private var instagramLaunchTime = 0L
    private var lastUserTouchTime = 0L
    private var lastScreenType = ""
    private var lastScreenCheckTime = 0L
    private var lastAntiCheatTime = 0L
    private var lastScrollIndex = -1
    private var lastPackage = ""
    private var serviceStartTime = 0L

    private var lastReelsSignature = ""
    private var lastSignatureCheckTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceStartTime = System.currentTimeMillis()
        
        // Remote Config: Uzaktan engelleme kurallarını arka planda güncelle
        RemoteRuleManager.fetchRulesAsync(this)
        
        Log.d(TAG, "AntiScrollService başlatıldı. Uygulama ana sayfasına dönülüyor...")

        try {
            val info = serviceInfo
            if (info != null) {
                info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                this.serviceInfo = info
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting serviceInfo flags", e)
        }

        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val isEn = prefs.getString("app_language", null) == "en"

        showShieldStatusNotification(
            this,
            if (isEn) "🛡️ Shield Active!" else "🛡️ Koruma Kalkanı Devrede!",
            if (isEn) "AwayDoomscrollin' is guarding Instagram, TikTok, and YouTube." else "AwayDoomscrollin' nöbette. Instagram, TikTok ve YouTube koruma altında.",
            1001
        )

        // Cihaz yeni açılmadıysa (Boot süresi > 90s) kullanıcının Ayarlar'dan izni verip döndüğünü anla ve uygulamayı öne getir
        val isSystemBooting = android.os.SystemClock.elapsedRealtime() < 90_000
        if (!isSystemBooting) {
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Erişilebilirlik izni sonrası MainActivity başlatılamadı", e)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "AntiScrollService devre dışı bırakıldı (onUnbind). Bildirim gönderiliyor...")
        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val isEn = prefs.getString("app_language", null) == "en"

        showShieldStatusNotification(
            this,
            if (isEn) "⚠️ Protection Disabled!" else "⚠️ Koruma Kalkanı Devre Dışı Kaldı!",
            if (isEn) "Accessibility service turned off. Re-enable to keep your streak." else "AwayDoomscrollin' erişilebilirlik servisi kapatıldı. Serinizin bozulmaması için kalkanı tekrar açın.",
            1002
        )
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.w(TAG, "AntiScrollService sonlandırıldı (onDestroy). Bildirim gönderiliyor...")
        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val isEn = prefs.getString("app_language", null) == "en"

        showShieldStatusNotification(
            this,
            if (isEn) "⚠️ Shield Stopped!" else "⚠️ Kalkan Kapandı!",
            if (isEn) "Protection service stopped. Please check accessibility permissions in Settings." else "Koruma servisi durduruldu. Lütfen Ayarlar'dan erişilebilirlik iznini kontrol edin.",
            1003
        )
        super.onDestroy()
    }

    private fun isAppInForeground(targetPackage: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val activePkg = root.packageName?.toString() ?: return false
        return activePkg == targetPackage
    }

    private fun isSettingsApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.settings" || 
               lower == "com.samsung.android.settings" || 
               lower == "com.sec.android.app.settings" ||
               lower == "android"
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lower = pkg.lowercase()
        return lower == "com.sec.android.app.launcher" ||
               lower == "com.samsung.android.app.homestar" ||
               lower == "com.google.android.apps.nexuslauncher" ||
               (lower.contains("launcher") && !lower.contains("settings"))
    }

    private fun findClickableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isEnabled && current.isClickable) {
                return current
            }
            current = current.parent
            depth++
        }
        current = node
        depth = 0
        while (current != null && depth < 4) {
            if (current.isEnabled) {
                return current
            }
            current = current.parent
            depth++
        }
        return node
    }

    private fun clickAtCoordinates(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun clickNodeWithGesture(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val targetNode = findClickableNode(node)
        var actionClicked = false
        if (targetNode != null && targetNode.isEnabled) {
            actionClicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val x = rect.centerX().toFloat()
            val y = rect.centerY().toFloat()
            Log.d(TAG, "Samsung One UI fiziki dokunma (dispatchGesture) gönderiliyor: ($x, $y)")
            clickAtCoordinates(x, y)
            return true
        }

        return actionClicked
    }

    private fun findForceStopNodeInTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName?.toString() ?: ""
        val combined = "$text $desc $viewId".lowercase()

        if (combined.contains("kaldır") || combined.contains("uninstall")) {
            return null
        }

        if (combined.contains("durmaya") ||
            combined.contains("zorla") ||
            combined.contains("durdur") ||
            combined.contains("force") ||
            combined.contains("force_stop") ||
            combined.contains("button_force_stop")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findForceStopNodeInTree(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    private fun isNodeOrAncestorEnabled(node: AccessibilityNodeInfo?): Boolean {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isEnabled) return true
            curr = curr.parent
        }
        return false
    }

    private fun isCancelButtonInTree(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName?.toString() ?: ""
        val combined = "$text $desc $viewId".lowercase()

        if (combined.contains("iptal") || combined.contains("cancel") || viewId.endsWith("button2")) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isCancelButtonInTree(node.getChild(i))) return true
        }
        return false
    }

    private fun findDialogConfirmNodeInTree(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        val combinedText = "$text $desc".lowercase()

        // 1. "İptal" / "Cancel" olanları es geç
        if (combinedText.contains("iptal") || combinedText.contains("cancel") || viewId.endsWith("button2")) {
            // Skip
        } else {
            // 2. Başlık veya mesaj TextView'lerini es geç (alertTitle, message)
            val isTitleOrMessage = viewId.contains("title") || viewId.contains("message")

            if (!isTitleOrMessage) {
                val isButtonClassOrClickable = className.contains("Button", ignoreCase = true) ||
                                               node.isClickable ||
                                               (node.parent != null && node.parent.isClickable)
                
                val isPositiveButtonId = viewId.endsWith("button1") || viewId.contains("positive") || viewId.endsWith("confirm")

                val matchesText = combinedText == "tamam" ||
                                  combinedText == "ok" ||
                                  combinedText.contains("durmaya zorla") ||
                                  combinedText == "durdur" ||
                                  combinedText.contains("force stop")

                if ((isPositiveButtonId || matchesText) && isButtonClassOrClickable) {
                    return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val result = findDialogConfirmNodeInTree(node.getChild(i), depth + 1)
            if (result != null) return result
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()
        val rootNode = rootInActiveWindow

        if (packageName != currentTargetPackage && packageName.isNotEmpty()) {
            if (packageName == "com.instagram.android") {
                lastInstagramTransitionTime = currentTime
                lastFeedText = ""
                Log.d(TAG, "Instagram ön plana geldi, grace period sıfırlandı.")
            }
            currentTargetPackage = packageName
        }

        if (packageName == "com.instagram.android") {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || 
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
                event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
                lastUserTouchTime = currentTime
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Kullanıcı hızlıca sekmeler arası gezinirken (Örn: tester gibi spam yaparken)
                // lastScreenType'ın geride kalmaması (stale state) için, her tıklamada
                // veya pencere değişiminde zamanlayıcıyı sıfırlıyoruz. Böylece bir sonraki 
                // event'te anında güncel ekran tespit edilecek!
                lastScreenCheckTime = 0L
            }

            if (currentTime - lastScreenCheckTime > 200) {
                lastScreenCheckTime = currentTime
                
                val currentScreenType = when {
                    rootNode == null -> lastScreenType
                    isProfileScreen(rootNode) -> "SAFE"
                    isHomeScreenActive(rootNode) -> "HOME_OR_REELS"
                    isStrictlyReelsScreen(rootNode) || isReelsTabSelected(rootNode) -> "HOME_OR_REELS"
                    isExploreScreen(rootNode) -> "EXPLORE"
                    else -> "HOME_OR_REELS" // Instagram'da aksi kanıtlanmadıkça ekran akıştır (Home/Reels)
                }

                if (currentScreenType != lastScreenType && currentScreenType != "UNKNOWN") {
                    lastInstagramTransitionTime = currentTime
                    lastScreenType = currentScreenType
                    Log.d(TAG, "Ekran türü değişti: $lastScreenType. Grace period sıfırlandı.")
                }
            }
        }


        // 0. ANINDA ANA EKRAN KONTROLÜ: Eğer kullanıcı veya sistem Ana Ekrana (Launcher) geldiyse, ceza modunu anında sonlandır!
        if (isLauncherPackage(packageName)) {
            if (isPunishing) {
                Log.d(TAG, "Ana ekrana ($packageName) dönüldü, ceza modu anında sıfırlandı.")
                isPunishing = false
                forceStopClicked = false
            }
        }

        // GLOBAL GÜVENLİK (FAIL-SAFE): 4 saniyeden fazla kilitli kalırsa zorla aç
        if (isPunishing && currentTime - punishStartTime > 4000) {
            Log.d(TAG, "Ceza süresi doldu (Global Fail-Safe), kilit açılıyor.")
            isPunishing = false
            forceStopClicked = false
            lastPunishTime = currentTime
            lastHomeActionTime = currentTime
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // Paket değişimlerini takip et
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName == "com.instagram.android" && packageName != lastPackage) {
                lastInstagramTransitionTime = System.currentTimeMillis()
            }
            if (packageName != lastPackage) {
                lastScrollIndex = -1
                
                // Zaman takibi: Instagram'dan çıkıldıysa süreyi hesapla
                if (lastPackage == "com.instagram.android" && instagramLaunchTime > 0) {
                    val timeSpent = System.currentTimeMillis() - instagramLaunchTime
                    val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
                    
                    val currentDayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    val savedDay = prefs.getString("instagram_time_day", "")
                    
                    val previousTime = if (savedDay == currentDayString) {
                        prefs.getLong("instagram_daily_time_ms", 0L)
                    } else {
                        0L // Yeni gün, sıfırla
                    }
                    
                    prefs.edit()
                        .putLong("instagram_daily_time_ms", previousTime + timeSpent)
                        .putString("instagram_time_day", currentDayString)
                        .apply()
                        
                    instagramLaunchTime = 0L
                }

                if (packageName == "com.instagram.android") {
                    instagramLaunchTime = System.currentTimeMillis()
                    isPunishing = false
                }
            }
            lastPackage = packageName
        }

        // REELS SEKMESİNE YATAY KAYDIRMA (SWIPE) İLE GEÇİŞ KONTROLÜ
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && packageName == "com.instagram.android" && !isPunishing) {
            if (currentTime - lastSignatureCheckTime > 500) {
                lastSignatureCheckTime = currentTime
                val rootNode = rootInActiveWindow
                
                // İlk açılış animasyonlarını ve akış yüklenmesini es geç (4.5 saniye)
                if (currentTime - instagramLaunchTime > 4500) {
                    if (rootNode != null && isSafeScreen(rootNode)) {
                        // Profil sayfasındaki reels sekmesi gibi güvenli alanları es geç
                    } else if (rootNode != null && (isStrictlyReelsScreen(rootNode) || isReelsTabSelected(rootNode))) {
                        // ZERO TOLERANCE: Reels sekmesine girildiği an anında engelle!
                        if (currentTime - lastPunishTime > 3000 && currentTime - lastHomeActionTime > 3000) {
                            Log.d(TAG, "Reels sekmesine geçiş algılandı! Toleranssız engelleme tetiklendi.")
                            punishUser("com.instagram.android")
                        }
                    } else if (rootNode != null && hasLikeButton(rootNode) && isVideoViewer(rootNode) && hasBackButton(rootNode)) {
                        if (currentTime - lastPunishTime > 3000) {
                            Log.d(TAG, "Profil/Keşfet üzerinden Video Oynatıcı açıldı! Anında kilitleniyor.")
                            punishUser("com.instagram.android")
                        }
                    } else if (rootNode != null && isRefreshingSpinnerVisible(rootNode) && (lastScreenType == "HOME_OR_REELS" || lastScreenType == "EXPLORE")) {
                        if (currentTime - lastInstagramTransitionTime > 1200 && currentTime - lastPunishTime > 3000) {
                            Log.d(TAG, "Pull-to-Refresh Spinner tespit edildi! Anında kilitleniyor.")
                            punishUser("com.instagram.android")
                        }
                    }

                    if (rootNode != null && (lastScreenType == "HOME_OR_REELS" || lastScreenType == "EXPLORE")) {
                        val currentText = getFeedAllText(rootNode)
                        
                        if (lastFeedText.isNotEmpty() && currentText.isNotEmpty()) {
                            if (isTextChangePullToRefresh(lastFeedText, currentText)) {
                                // Sadece kullanıcı yakın zamanda dokunma/kaydırma yaptıysa (lastUserTouchTime < 3000) ve launch 4.5sn geçtiyse ceza ver!
                                if (currentTime - lastUserTouchTime < 3000 && currentTime - lastInstagramTransitionTime > 2000 && currentTime - lastPunishTime > 3000 && currentTime - lastFeedChangePunishTime > 3000) {
                                    Log.d(TAG, "Ana akış içeriği tamamen değişti (Pull-to-Refresh veya Scroll)! Anında kilitleniyor.")
                                    lastFeedChangePunishTime = currentTime
                                    punishUser("com.instagram.android")
                                }
                            }
                        }
                        
                        // SADECE ve SADECE ekranda gerçek bir akış varken (skeleton/yükleme ekranı değilken) lastFeedText'i güncelle!
                        val ignoreWords = setOf("beğen", "like", "yorum", "comment", "paylaş", "share", "kaydet", "save", "gönder", "send", "yanıtla", "reply", "reklam", "sponsorlu", "sponsored", "ana sayfa", "home", "reels", "profil", "profile", "mesajlar", "messages", "ara", "search")
                        val validWords = currentText.lowercase().split("\\s+".toRegex()).filter { it.length > 2 && !ignoreWords.contains(it) }
                        if (validWords.size > 5) {
                            lastFeedText = currentText
                        }
                    } else {
                        lastFeedText = ""
                    }
                }
            }
        }

        // KESİN KİLİT KONTROLÜ: Eğer ceza modundaysak:
        if (isPunishing) {
            if (isSettingsApp(packageName)) {
                handleSettingsApp(event)
                return
            }
            if (packageName == this.packageName) {
                // Bizim temizleyici aktivitemiz (ClearTaskActivity) çalışıyor, izin ver
                return
            }
            if (packageName == currentTargetPackage) {
                if (currentTime - punishStartTime < 400) {
                    // Settings açılırken gelen geçici ilk olay, yok say
                    return
                } else {
                    // Kullanıcı geri tuşuyla veya jestle Instagram'a geri kaçtı!
                    Log.d(TAG, "Kullanıcı Ayarlar'dan geri kaçmaya çalıştı ($packageName)! Süreç öldürülüp HOME fırlatılıyor.")
                    try {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        am.killBackgroundProcesses(currentTargetPackage)
                    } catch (e: Exception) {}

                    val now = System.currentTimeMillis()
                    isPunishing = false
                    lastPunishTime = now
                    lastHomeActionTime = now

                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
            if (isLauncherPackage(packageName)) {
                if (currentTime - punishStartTime > 1000) {
                    Log.d(TAG, "Ceza sırasında Ana Ekrana ($packageName) ulaşıldı. Kilit sıfırlanıyor.")
                    isPunishing = false
                    forceStopClicked = false
                }
                return
            }
            
            // Eğer 3.5 saniye geçtiyse ve başka paket araya girdiyse fail-safe olarak sonlandır
            if (currentTime - punishStartTime > 3500) {
                Log.d(TAG, "Ceza sırasında zaman aşımı ($packageName). HOME yönlendiriliyor.")
                val now = System.currentTimeMillis()
                isPunishing = false
                lastPunishTime = now
                lastHomeActionTime = now
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // Anti-Cheat: Kullanıcı Ayarlar'da erişilebilirlik anahtarına tıkladığında VEYA Samsung'un "Kapatılsın mı?" onay penceresi çıktığında anında yakala!
        if (isSettingsApp(packageName)) {
            val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val totalBlocks = prefs.getInt("total_blocks", 0)

            if (totalBlocks > 0 && currentTime - lastAntiCheatTime > 5000) {
                val rootNode = rootInActiveWindow
                if (isSwitchClickedInDetailPage(event, rootNode) || isSamsungTurnOffDialog(rootNode)) {
                    lastAntiCheatTime = currentTime
                    Log.d(TAG, "Anti-Cheat Samsung / Genel Onay Penceresi Yakalandı! Suçluluk ekranı açılıyor.")
                    val intent = Intent(this, AntiCheatGuiltActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            }
        }

        // 2. Instagram Aşaması - Kaydırma (Scroll) algılandığında
        if (packageName == "com.instagram.android") {
            if (!isAppInForeground("com.instagram.android")) return

            if (currentTime - lastPunishTime < 3500 || currentTime - lastHomeActionTime < 3500) return
            val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val isInstagramEnabled = prefs.getBoolean("is_instagram_enabled", true)
            if (!isInstagramEnabled) return
            
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val nodeText = event.text?.toString() ?: ""
                val nodeDesc = event.contentDescription?.toString() ?: ""
                val combined = "$nodeText $nodeDesc".lowercase()
                
                if (combined.contains("home") || combined.contains("ana sayfa") || combined.contains("akış") || combined.contains("yenile") || combined.contains("reels")) {
                    // Android sisteminde bir sekmeye tıklandığında, tıklama eventi fırlatılmadan hemen önce o sekmenin
                    // isSelected değeri 'true' yapılır. Bu yüzden tıklanan ikonun o anki seçili olma durumuna GÜVENEMEYİZ.
                    // Bunun yerine, kullanıcının tıklamadan BİR ÖNCEKİ saniyede hangi ekranda olduğunu tutan 
                    // 'lastScreenType' değişkenine bakıyoruz.
                    // Eğer kullanıcı zaten "HOME_OR_REELS" ekranındayken tekrar ana sayfa butonuna tıkladıysa, 
                    // bu %100 sayfayı yenilemek (pull-to-refresh) demektir!
                    if (lastScreenType == "HOME_OR_REELS") {
                        // Eğer ana sayfaya daha yeni geldiyse (son 2 saniye içinde), bu muhtemelen 
                        // sekme geçişi sırasında oluşan çift tıklama (double click) veya Android'in kendi
                        // fırlattığı mükerrer (duplicate) tıklama eventidir. Sadece gerçekten ana sayfada 
                        // vakit geçirip sonra butona basarsa ceza ver!
                        if (currentTime - lastInstagramTransitionTime > 2000) {
                            if (currentTime - lastPunishTime > 3000) {
                                Log.d(TAG, "Zaten ana sayfadayken ana sayfa butonuna tıklandı! Pull-to-refresh tetiklendi.")
                                punishUser("com.instagram.android")
                                return
                            }
                        } else {
                            Log.d(TAG, "Ana sayfa butonuna tıklandı ama geçiş yapalı 2 saniye bile olmadı (Double-click/Duplicate event). İzin verildi.")
                        }
                    } else {
                        Log.d(TAG, "Başka bir sayfadan (Örn: Profil) ana sayfaya geçiş için tıklandı. İzin verildi.")
                    }
                }
                
                val rootNode = rootInActiveWindow
                if (rootNode != null && isExploreScreen(rootNode)) {
                    val clickedClassName = event.className?.toString() ?: ""
                    val isSearchBar = combined.contains("ara") || combined.contains("search") || clickedClassName.contains("EditText")
                    
                    // Alt menü ikonlarına (Ana Sayfa, Profil, Reels vs.) tıklanmasına izin ver
                    val isBottomNav = combined.contains("ana sayfa") || combined.contains("home") || 
                                      combined.contains("profil") || combined.contains("profile") ||
                                      combined.contains("reels") || combined.contains("mesajlar") || 
                                      combined.contains("messages") || combined.contains("kamera") ||
                                      combined.contains("camera")

                    if (!isSearchBar && !isBottomNav) {
                        if (currentTime - lastPunishTime > 3000) {
                            Log.d(TAG, "Keşfet sayfasındaki bir videoya/içeriğe tıklandı! Engelleniyor.")
                            punishUser("com.instagram.android")
                            return
                        }
                    }
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                // Sekme geçişi ve açılış animasyonları (ViewPager) için 2.5 saniyelik kaydırma toleransı
                if (currentTime - lastInstagramTransitionTime < 2500) {
                    Log.d(TAG, "Sekme geçiş animasyonu (2.5sn tolerans). Scroll es geçildi.")
                    return
                }

                val rootNode = rootInActiveWindow
                val isCurrentlySafe = rootNode != null && isSafeScreen(rootNode)
                if (isCurrentlySafe || lastScreenType == "SAFE") {
                    Log.d(TAG, "Güvenli alan kaydırması tespit edildi. İzin verildi.")
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                    
                    // Yatay kaydırmaları görmezden gel (Fotoğraf galerisi kaydırma vs.)
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 5) {
                        return
                    }

                    // PULL-TO-REFRESH VEYA HERHANGİ BİR DİKEY KAYDIRMA (Ana Sayfa veya Reels ekranında):
                    if (lastScreenType == "HOME_OR_REELS" || lastScreenType == "EXPLORE") {
                        val sourceNode = event.source
                        if (isCommentListView(sourceNode)) {
                            Log.d(TAG, "Yorumlar listesi kaydırması tespit edildi. İzin verildi.")
                            return
                        }
                        if (lastScreenType != "EXPLORE" && rootNode != null && isVideoEndOverlayPresent(rootNode)) {
                            Log.d(TAG, "Video bitiş menüsü ('Tekrar izle' vb.) algılandı. İzin verildi.")
                            return
                        }
                        if (currentTime - lastPunishTime > 3000) {
                            Log.d(TAG, "Ana sayfada / Reels'te dikey hareket veya Pull-to-Refresh algılandı! Anında kilitleniyor.")
                            punishUser("com.instagram.android")
                            return
                        }
                    }
                } else {
                    if (lastScreenType == "HOME_OR_REELS" || lastScreenType == "EXPLORE") {
                        if (currentTime - lastPunishTime > 3000) {
                            Log.d(TAG, "Ana sayfada / Reels'te dikey hareket algılandı! Anında kilitleniyor.")
                            punishUser("com.instagram.android")
                            return
                        }
                    }
                }
            }
        }

        // 3. TikTok Aşaması
        if (packageName == "com.zhiliaoapp.musically") {
            if (!isAppInForeground("com.zhiliaoapp.musically")) return

            if (currentTime - lastPunishTime < 3500 || currentTime - lastHomeActionTime < 3500) return
            val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val isTiktokEnabled = prefs.getBoolean("is_tiktok_enabled", true)
            if (!isTiktokEnabled) return

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (currentTime - lastPunishTime > 3000) {
                    Log.d(TAG, "TikTok SCROLL DETECTED! Punishing TikTok...")
                    punishUser("com.zhiliaoapp.musically")
                }
            }
        }

        // 4. YouTube Shorts Aşaması
        if (packageName == "com.google.android.youtube") {
            if (!isAppInForeground("com.google.android.youtube")) return

            if (currentTime - lastPunishTime < 3500 || currentTime - lastHomeActionTime < 3500) return
            val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
            val isYoutubeEnabled = prefs.getBoolean("is_youtube_enabled", true)
            if (!isYoutubeEnabled) return
            
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val rootNode = rootInActiveWindow
                val isShorts = isYoutubeShortsScreen(rootNode)
                
                if (rootNode != null && isShorts) {
                    if (currentTime - lastPunishTime > 3000) {
                        Log.d(TAG, "YouTube Shorts punished!")
                        punishUser("com.google.android.youtube")
                    }
                }
            }
        }
    }

    private fun isSafeScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!node.isVisibleToUser) return false
        
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        
        if (text.equals("Birincil", ignoreCase = true) ||
            text.equals("Primary", ignoreCase = true) ||
            text.equals("Genel", ignoreCase = true) ||
            text.equals("General", ignoreCase = true) ||
            text.equals("İstekler", ignoreCase = true) ||
            text.equals("Requests", ignoreCase = true) ||
            text.equals("Mesajlar", ignoreCase = true) ||
            text.equals("Messages", ignoreCase = true) ||
            text.equals("Yorumlar", ignoreCase = true) ||
            text.equals("Comments", ignoreCase = true) ||
            text.equals("Galeri", ignoreCase = true) ||
            text.equals("Gallery", ignoreCase = true) ||
            text.equals("Film Rulosu", ignoreCase = true) ||
            text.equals("Camera Roll", ignoreCase = true) ||
            text.equals("Son kullanılanlar", ignoreCase = true) ||
            text.equals("Recents", ignoreCase = true) ||
            (desc.contains("Profil", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Profile", ignoreCase = true) && node.isSelected) ||
            text.contains("Profil", ignoreCase = true) && node.isSelected ||
            text.contains("Profile", ignoreCase = true) && node.isSelected ||
            text.contains("Ayarlar ve aktiviteler", ignoreCase = true) ||
            desc.contains("Ayarlar ve aktiviteler", ignoreCase = true) ||
            text.contains("Ayarlar ve hareketler", ignoreCase = true) ||
            desc.contains("Ayarlar ve hareketler", ignoreCase = true) ||
            text.contains("Settings and activity", ignoreCase = true) ||
            desc.contains("Settings and activity", ignoreCase = true) ||
            text.contains("Profili düzenle", ignoreCase = true) ||
            desc.contains("Profili Düzenle", ignoreCase = true) ||
            text.contains("Edit profile", ignoreCase = true) ||
            desc.contains("Edit profile", ignoreCase = true) ||
            text.contains("Profili paylaş", ignoreCase = true) ||
            desc.contains("Profili paylaş", ignoreCase = true) ||
            text.contains("Share profile", ignoreCase = true) ||
            desc.contains("Share profile", ignoreCase = true) ||
            (desc.contains("Izgara", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Grid", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Gönderiler", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Posts", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Olduğun fotoğraflar", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Etiketlendiğin", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Photos of you", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Yeniden paylaşılanlar", ignoreCase = true) && node.isSelected) ||
            (desc.contains("Reposts", ignoreCase = true) && node.isSelected) ||
            desc.contains("Geri", ignoreCase = true) ||
            desc.contains("Back", ignoreCase = true) ||
            desc.contains("İptal", ignoreCase = true) ||
            desc.contains("Cancel", ignoreCase = true) ||
            text.contains("İptal", ignoreCase = true) ||
            text.contains("Cancel", ignoreCase = true)) {
            return true
        }

        if (node.className?.toString()?.contains("EditText") == true) {
            val etText = text.lowercase()
            if (etText.contains("mesaj") || etText.contains("message")) {
                return true
            }
        }

        if (desc.equals("Yeni mesaj", ignoreCase = true) || 
            desc.equals("New message", ignoreCase = true) ||
            desc.equals("Görüntülü arama", ignoreCase = true) ||
            desc.equals("Video call", ignoreCase = true) ||
            desc.equals("Sesli arama", ignoreCase = true) ||
            desc.equals("Audio call", ignoreCase = true)) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isSafeScreen(node.getChild(i))) return true
        }
        return false
    }

    private fun isCommentListView(sourceNode: AccessibilityNodeInfo?): Boolean {
        if (sourceNode == null) return false

        var current: AccessibilityNodeInfo? = sourceNode
        var depth = 0
        while (current != null && depth < 5) {
            val viewId = current.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("comment") || 
                viewId.contains("layout_comment") || 
                viewId.contains("comment_thread") || 
                viewId.contains("comments_recycler") ||
                viewId.contains("comment_cell")) {
                return true
            }
            current = current.parent
            depth++
        }

        return false
    }

    private fun isHomeScreenActive(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        if ((desc.equals("Ana Sayfa", ignoreCase = true) || desc.equals("Home", ignoreCase = true)) && (node.isSelected || node.parent?.isSelected == true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (isHomeScreenActive(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isProfileScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val combined = "$text $desc".lowercase()
        
        if (combined.contains("profili düzenle") || 
            combined.contains("edit profile") || 
            combined.contains("profili paylaş") || 
            combined.contains("share profile") ||
            combined.contains("ayarlar ve aktiviteler") ||
            combined.contains("settings and activity")) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (isProfileScreen(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isDangerousScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        if (!node.isVisibleToUser) return false

        val desc = node.contentDescription?.toString() ?: ""
        
        // "Profili Paylaş" (Share Profile) butonunu tehlikeli saymamak için kontrol ekliyoruz.
        val isProfileButton = desc.contains("Profil", ignoreCase = true) || desc.contains("Profile", ignoreCase = true)
        
        if (!isProfileButton && (
            desc.contains("Beğen", ignoreCase = true) ||
            desc.contains("Like", ignoreCase = true) ||
            desc.contains("Kaydet", ignoreCase = true) ||
            desc.contains("Save", ignoreCase = true) ||
            desc.contains("Paylaş", ignoreCase = true) ||
            desc.contains("Share", ignoreCase = true) ||
            desc.contains("Repost", ignoreCase = true))) { 
            
            if (node.isClickable || node.parent?.isClickable == true || node.isSelected) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (isDangerousScreen(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun computeSimilarity(s1: String, s2: String): Double {
        val words1 = s1.lowercase().split(" ").filter { it.isNotBlank() }.toSet()
        val words2 = s2.lowercase().split(" ").filter { it.isNotBlank() }.toSet()
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun isRefreshingSpinnerVisible(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("yükleniyor") || desc.contains("loading") || desc.contains("yenileniyor") || desc.contains("refreshing") || desc.contains("güncelleniyor") || desc.contains("updating")) {
            return true
        }
        val className = node.className?.toString() ?: ""
        if (className.contains("Refresh", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (isRefreshingSpinnerVisible(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun findMainRecyclerView(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 30) return null
        val className = node.className?.toString() ?: ""
        if (className.contains("RecyclerView") || className.contains("ListView")) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.height() > 500) { // Geniş liste (Akış)
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val res = findMainRecyclerView(node.getChild(i), depth + 1)
            if (res != null) return res
        }
        return null
    }

    private fun getFeedAllText(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 30) return ""
        var textStr = ""
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        
        if (text.isNotBlank()) textStr += "$text "
        if (desc.isNotBlank()) textStr += "$desc "
        
        for (i in 0 until node.childCount) {
            textStr += getFeedAllText(node.getChild(i), depth + 1)
        }
        return textStr
    }

    private fun isTextChangePullToRefresh(oldText: String, newText: String): Boolean {
        if (oldText.isBlank() || newText.isBlank()) return false
        
        // Ortak UI kelimelerini (beğen, yorum yap, paylaş vb.) filtrele ki benzerlik oranını bozmasınlar.
        val ignoreWords = setOf("beğen", "like", "yorum", "comment", "paylaş", "share", "kaydet", "save", "gönder", "send", "yanıtla", "reply", "reklam", "sponsorlu", "sponsored")
        
        val oldWords = oldText.lowercase().split("\\s+".toRegex()).filter { it.length > 2 && !ignoreWords.contains(it) }.toSet()
        val newWords = newText.lowercase().split("\\s+".toRegex()).filter { it.length > 2 && !ignoreWords.contains(it) }.toSet()
        
        if (oldWords.isEmpty() || newWords.isEmpty()) return false
        
        val intersection = oldWords.intersect(newWords).size
        val union = oldWords.union(newWords).size
        val similarity = intersection.toDouble() / union.toDouble()
        
        // Eğer benzerlik yüksekse zaten refresh değildir (Örn: Sadece saniye değiştiyse)
        if (similarity >= 0.80) return false
        
        // Eski kelime sayısı çok azsa güvenilir değildir (Örn: Shimmer/iskelet yükleme ekranı)
        if (oldWords.size <= 5) return false
        
        // Subset (Alt küme) kontrolü:
        // Eğer yeni ekrandaki kelimelerin çoğu (%80'i veya daha fazlası) zaten eski ekranda varsa,
        // bu yeni bir post gelmesi (refresh) değil, muhtemelen videonun tam ekran olmasıyla UI elemanlarının gizlenmesi durumudur.
        val subsetRatio = intersection.toDouble() / newWords.size.toDouble()
        if (subsetRatio >= 0.80) {
            return false // Sadece kelimeler azaldı/gizlendi, ekrana yepyeni kelimeler girmedi.
        }
        
        return true
    }

    private fun isReelsTabSelected(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        
        if ((desc.equals("Reels", ignoreCase = true) || desc.equals("Reels tab", ignoreCase = true)) && (node.isSelected || node.parent?.isSelected == true)) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (isReelsTabSelected(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isStrictlyReelsScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        
        // Reels sekmesine özel üst kamera ikonu
        if (desc.contains("Reels kamerası", ignoreCase = true) || desc.contains("Reels camera", ignoreCase = true)) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (isStrictlyReelsScreen(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isExploreScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        
        if ((desc.contains("Ara ve Keşfet", ignoreCase = true) || 
             desc.contains("Search and explore", ignoreCase = true)) && (node.isSelected || node.parent?.isSelected == true)) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (isExploreScreen(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun hasLikeButton(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        if ((desc.equals("Beğen", ignoreCase = true) || 
             desc.equals("Like", ignoreCase = true) ||
             desc.equals("Beğenmekten vazgeç", ignoreCase = true) ||
             desc.equals("Unlike", ignoreCase = true)) && 
             (node.isClickable || node.parent?.isClickable == true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (hasLikeButton(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isVideoViewer(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("Sesi kapat", ignoreCase = true) || 
            desc.contains("Sesi aç", ignoreCase = true) ||
            desc.contains("Unmute", ignoreCase = true) ||
            desc.contains("Mute", ignoreCase = true) ||
            desc.contains("Sesi", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (isVideoViewer(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun hasBackButton(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.equals("Geri", ignoreCase = true) || 
            desc.equals("Back", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (hasBackButton(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun isVideoEndOverlayPresent(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 30) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combined = "$text $desc".lowercase()

        if (combined.contains("tekrar izle") || 
            combined.contains("watch again") || 
            combined.contains("izlemeye devam et") || 
            combined.contains("keep watching")) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isVideoEndOverlayPresent(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun punishUser(targetPackageName: String = "com.instagram.android") {
        val currentTime = System.currentTimeMillis()
        
        if (isPunishing && currentTime - punishStartTime > 3000) {
            Log.d(TAG, "isPunishing 3 saniyeden fazla takılı kaldı. Sıfırlanıyor!")
            isPunishing = false
        }
        
        // Cooldown and lock check: Acquire lock IMMEDIATELY to prevent duplicate scroll events from re-entering!
        if (isPunishing || (currentTime - lastPunishTime < 3500) || (currentTime - lastHomeActionTime < 3500)) return

        isPunishing = true
        lastPunishTime = currentTime
        punishStartTime = currentTime
        lastHomeActionTime = currentTime
        forceStopClicked = false
        currentTargetPackage = targetPackageName

        Log.d(TAG, "CEZA VERILIYOR: $targetPackageName Task Stack tamamen temizlenip yok edilecek!")
        
        // 1. ANINDA SİSTEM SÜREÇ ÖLDÜRÜCÜ (ActivityManager Kill)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(targetPackageName)
        } catch (e: Exception) {
            Log.e(TAG, "killBackgroundProcesses error in punishUser", e)
        }

        // 2. KESİN TASK SİLİCİ (Task Affinity Clear Strike):
        // Target app'in (Instagram/TikTok/YouTube) Android ActivityManager üzerindeki TÜM TASK STACK'İNİ RAM VE SON UYGULAMALARDAN ANINDA YOK EDER!
        val wiperClass = when {
            targetPackageName.contains("musically") -> ClearTaskTiktokActivity::class.java
            targetPackageName.contains("youtube") -> ClearTaskYoutubeActivity::class.java
            else -> ClearTaskInstagramActivity::class.java
        }

        try {
            val wiperIntent = Intent(this, wiperClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or 
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            startActivity(wiperIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Task Wiper başlatılırken hata", e)
        }

        // ClearTaskActivity artık Ayarlar sayfasını kendi içerisinden %100 güvenli şekilde başlatıyor.
        // Bu yüzden buradan ikinci bir Intent fırlatmaya gerek kalmadı.

        // --- BACK-END GAMIFICATION INTEGRATION ---
        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val currentBlocks = prefs.getInt("total_blocks", 0)
        val currentXp = prefs.getLong("user_xp", 150L)
        
        val currentDayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val dailyBlocksKey = "blocks_$currentDayString"
        val currentDailyBlocks = prefs.getInt(dailyBlocksKey, 0)
        
        // App-specific block counter
        val appBlocksKey = when {
            targetPackageName.contains("musically") -> "blocks_tiktok"
            targetPackageName.contains("youtube") -> "blocks_youtube"
            else -> "blocks_instagram"
        }
        val currentAppBlocks = prefs.getInt(appBlocksKey, 0)

        // Streak check
        val lastActiveDay = prefs.getString("last_active_day", "")
        var streakDays = prefs.getInt("streak_days", 0)
        if (lastActiveDay != currentDayString) {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
            val yesterdayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
            if (lastActiveDay == yesterdayStr) {
                streakDays += 1
                sendMilestoneNotification(streakDays)
            } else if (lastActiveDay.isNullOrEmpty()) {
                streakDays = 1
                sendMilestoneNotification(streakDays)
            }
        }

        val dailyAppKey = when {
            targetPackageName.contains("musically") -> "blocks_${currentDayString}_tiktok"
            targetPackageName.contains("youtube") -> "blocks_${currentDayString}_youtube"
            else -> "blocks_${currentDayString}_instagram"
        }
        val currentDailyAppBlocks = prefs.getInt(dailyAppKey, 0)

        // Canlı Kalkan Günlüğü İçin Kayıt
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val currentTimeStr = timeFormat.format(java.util.Date())
        val appName = when {
            targetPackageName.contains("musically") -> "TikTok"
            targetPackageName.contains("youtube") -> "YouTube Shorts"
            else -> "Instagram Reels"
        }
        val dailyCount = currentDailyBlocks + 1
        val newLog = "$currentTimeStr|$appName|$dailyCount"
        val oldLogs = prefs.getString("recent_shield_logs", "") ?: ""
        val logList = if (oldLogs.isEmpty()) mutableListOf() else oldLogs.split(";").toMutableList()
        logList.add(0, newLog)
        val updatedLogs = logList.take(10).joinToString(";")

        // Saatlik istatistik kaydı - Heatmap için
        val hourFormat = java.text.SimpleDateFormat("HH", java.util.Locale.getDefault())
        val currentHour = hourFormat.format(java.util.Date())
        val hourlyBlocksKey = "blocks_${currentDayString}_hour_${currentHour}"
        val currentHourlyBlocks = prefs.getInt(hourlyBlocksKey, 0)

        prefs.edit()
            .putInt("total_blocks", currentBlocks + 1)
            .putLong("user_xp", currentXp + 3L)
            .putInt(dailyBlocksKey, currentDailyBlocks + 1)
            .putInt(appBlocksKey, currentAppBlocks + 1)
            .putInt(dailyAppKey, currentDailyAppBlocks + 1)
            .putInt(hourlyBlocksKey, currentHourlyBlocks + 1)
            .putInt("streak_days", streakDays)
            .putString("last_active_day", currentDayString)
            .putString("recent_shield_logs", updatedLogs)
            .apply()

        // Telemetri açıksa canlı sunucuya anında engelleme raporu gönder
        TelemetryManager.sendTelemetryAsync(this, force = true)
        // ------------------------------------------
    }

    private fun isYoutubeShortsScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName?.toString() ?: ""

        if ((text.equals("Shorts", ignoreCase = true) || desc.equals("Shorts", ignoreCase = true)) && node.isSelected) {
            return true
        }
        
        if (viewId.contains("reel", ignoreCase = true) || viewId.contains("shorts", ignoreCase = true)) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isYoutubeShortsScreen(node.getChild(i))) return true
        }
        return false
    }

    private fun handleSettingsApp(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSettingsClickTime < 150) return

        val isDialogVisible = isCancelButtonInTree(rootNode)

        if (isDialogVisible) {
            val confirmNode = findDialogConfirmNodeInTree(rootNode)
            if (confirmNode != null) {
                Log.d(TAG, "Diyalog onay butonu bulundu, tıklanıyor...")
                clickNodeWithGesture(confirmNode)
                lastSettingsClickTime = currentTime
                
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    am.killBackgroundProcesses(currentTargetPackage)
                } catch (e: Exception) {}

                val now = System.currentTimeMillis()
                isPunishing = false
                lastPunishTime = now
                lastHomeActionTime = now

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 300)
                return
            }
        }

        val forceStopNode = findForceStopNodeInTree(rootNode)
        if (forceStopNode != null) {
            if (!isNodeOrAncestorEnabled(forceStopNode)) {
                Log.d(TAG, "Durmaya zorla butonu devre dışı (Uygulama zaten durdurulmuş). Ana ekrana dönülüyor.")
                val now = System.currentTimeMillis()
                isPunishing = false
                lastPunishTime = now
                lastHomeActionTime = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }

            Log.d(TAG, "Durmaya zorla butonu bulundu, tıklanıyor...")
            val clicked = clickNodeWithGesture(forceStopNode)
            if (clicked) {
                lastSettingsClickTime = currentTime
                forceStopClicked = true
                return
            }
        }

        if (!isDialogVisible && currentTime - punishStartTime > 3500) {
            Log.d(TAG, "Settings zaman aşımına uğradı (3.5s). Ana ekrana atılıyor.")
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(currentTargetPackage)
            } catch (e: Exception) {}
            val now = System.currentTimeMillis()
            isPunishing = false
            lastPunishTime = now
            lastHomeActionTime = now
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun extractReelsSignature(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = java.lang.StringBuilder()
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        
        val combined = "$text $desc".lowercase()
        val words = combined.split(Regex("\\s+"))
        for (word in words) {
            val cleanWord = word.replace(Regex("[^a-zğüşıöç]"), "")
            if (cleanWord.length > 4 && 
                cleanWord != "beğen" && cleanWord != "yorum" && cleanWord != "paylaş" && 
                cleanWord != "reels" && cleanWord != "gönder" && cleanWord != "kaydet" &&
                cleanWord != "like" && cleanWord != "comment" && cleanWord != "share" && cleanWord != "save" &&
                cleanWord != "anasayfa" && cleanWord != "profil" && cleanWord != "keşfet") {
                builder.append(cleanWord).append("_")
            }
        }
        
        for (i in 0 until node.childCount) {
            builder.append(extractReelsSignature(node.getChild(i)))
        }
        return builder.toString()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isPunishing) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun isSwitchClickedInDetailPage(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        val className = event.className?.toString() ?: ""
        val isSwitch = className.contains("Switch", ignoreCase = true) || 
                        className.contains("ToggleButton", ignoreCase = true) || 
                        className.contains("CheckBox", ignoreCase = true)
        
        if (!isSwitch) return false

        return isAwayDetailPage(rootNode)
    }

    private fun isAwayDetailPage(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        
        val talkBackNodes = rootNode.findAccessibilityNodeInfosByText("TalkBack")
        val selectToSpeakNodes = rootNode.findAccessibilityNodeInfosByText("Select to Speak")
        
        if ((talkBackNodes != null && talkBackNodes.isNotEmpty()) || 
            (selectToSpeakNodes != null && selectToSpeakNodes.isNotEmpty())) {
            return false
        }

        val ourAppNodes = rootNode.findAccessibilityNodeInfosByText("AwayDoomscrollin")
        return (ourAppNodes != null && ourAppNodes.isNotEmpty())
    }

    private fun isSamsungTurnOffDialog(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val nodes1 = rootNode.findAccessibilityNodeInfosByText("kapatılsın")
        val nodes2 = rootNode.findAccessibilityNodeInfosByText("Turn off")
        val nodes3 = rootNode.findAccessibilityNodeInfosByText("kapanacak")
        val nodes4 = rootNode.findAccessibilityNodeInfosByText("Durdurulsun")
        return (nodes1 != null && nodes1.isNotEmpty()) ||
               (nodes2 != null && nodes2.isNotEmpty()) ||
               (nodes3 != null && nodes3.isNotEmpty()) ||
               (nodes4 != null && nodes4.isNotEmpty())
    }
}
