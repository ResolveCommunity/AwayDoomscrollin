package com.awaydoomscrollin.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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

        val title = if (isEn) "$streakDays Day Streak!" else "$streakDays Günlük Seri!"

        val message = if (isEn) {
            when (streakDays) {
                3 -> "Great job! You reached a 3-day streak. Your dopamine receptors are healing!"
                7 -> "1 Week without lowering the shield! You are back in control of your mind."
                14 -> "2 Weeks! Showing incredible willpower. Keep going!"
                21 -> "21-Day rule! Your old habit is breaking. Congratulations!"
                30 -> "1 MONTH! Reached Unchained Mind level. You're a legend!"
                60 -> "2 MONTHS! Your focus is fully restored. Superb!"
                else -> return
            }
        } else {
            when (streakDays) {
                3 -> "Harika gidiyorsun! 3 günlük seriye ulaştın, dopamin reseptörlerin iyileşmeye başladı bile!"
                7 -> "1 Haftadır kalkanı indirmedin! Zihninin kontrolü tekrar sende."
                14 -> "2 Hafta oldu! İnanılmaz bir irade örneği gösteriyorsun."
                21 -> "21 Gün kuralı! Alışkanlığın kırılmaya başladı. Kutlarız!"
                30 -> "1 AY! Özgür Zihin seviyesine ulaştın. Sen bir efsanesin!"
                60 -> "2 AY! Zihnin tamamen yenilendi. Süpersin!"
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
    private var lastPunishTime = 0L
    private var lastHomeActionTime = 0L
    
    private var instagramLaunchTime = 0L
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
        
        Log.d(TAG, "AntiScrollService başlatıldı.")

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
            if (isEn) "Shield Active!" else "Koruma Kalkanı Devrede!",
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
            if (isEn) "Protection Disabled!" else "Koruma Kalkanı Devre Dışı Kaldı!",
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
            if (isEn) "Shield Stopped!" else "Kalkan Kapandı!",
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


    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lower = pkg.lowercase()
        return lower == "com.sec.android.app.launcher" ||
               lower == "com.samsung.android.app.homestar" ||
               lower == "com.google.android.apps.nexuslauncher" ||
               (lower.contains("launcher") && !lower.contains("settings"))
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()

        // Paket değişimlerini takip et
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
                }
            }
            lastPackage = packageName
        }

        // REELS SEKMESİNE YATAY KAYDIRMA (SWIPE) İLE GEÇİŞ KONTROLÜ
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && packageName == "com.instagram.android") {
            if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return

            if (currentTime - lastSignatureCheckTime > 500) {
                lastSignatureCheckTime = currentTime
                val rootNode = rootInActiveWindow
                
                // İlk açılış animasyonlarını es geç (1.5 saniye)
                if (currentTime - instagramLaunchTime > 1500) {
                    if (rootNode != null && isStrictlyReelsScreen(rootNode) && !isSafeScreen(rootNode)) {
                        if (currentTime - lastPunishTime > 2500 && currentTime - lastHomeActionTime > 2500) {
                            Log.d(TAG, "Yatay Swipe ile Reels sekmesine geçiş algılandı! Engelleme tetiklendi.")
                            punishUser("com.instagram.android")
                        }
                    }
                }
            }
        }


        // 1. Instagram Aşaması - Kaydırma ve Tıklama algılandığında
        if (packageName == "com.instagram.android") {
            if (!isAppInForeground("com.instagram.android")) return
            if (currentTime - lastPunishTime < 2500 || currentTime - lastHomeActionTime < 2500) return
            if (!ProtectionPreferences.isEnabled(this, ProtectedApp.INSTAGRAM)) return
            
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val nodeText = event.text.joinToString(" ")
                val nodeDesc = event.contentDescription?.toString() ?: ""
                val combined = "$nodeText $nodeDesc".lowercase()
                
                if (combined.contains("home") || combined.contains("ana sayfa") || combined.contains("akış") || combined.contains("yenile") || combined.contains("reels")) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null && isDangerousScreen(rootNode) && !isSafeScreen(rootNode)) {
                        if (currentTime - lastPunishTime > 2500) {
                            Log.d(TAG, "Refresh / Reels açığı (Click) yakalandı!")
                            punishUser("com.instagram.android")
                            return
                        }
                    }
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val className = event.className?.toString() ?: ""
                
                if (!className.contains("RecyclerView") && !className.contains("ViewPager") && 
                    !className.contains("ListView") && !className.contains("ScrollView") && 
                    !className.contains("SwipeRefreshLayout") && !className.contains("Layout")) {
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                    
                    // Yatay kaydırmaları görmezden gel (Fotoğraf galerisi kaydırma vs.)
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 5) {
                        return
                    }

                    // Sıfır piksellik sahte dokunma titremelerini yoksay
                    if (deltaY == 0 && deltaX == 0) {
                        return
                    }
                } else {
                    if (event.toIndex == -1 || event.toIndex == lastScrollIndex) return
                    lastScrollIndex = event.toIndex
                }

                val rootNode = rootInActiveWindow
                val sourceNode = event.source

                if (isCommentListView(sourceNode) || (rootNode != null && isSafeScreen(rootNode))) {
                    return
                }

                if (rootNode != null && !isDangerousScreen(rootNode)) {
                    return
                }
                
                if (currentTime - lastPunishTime > 2500) {
                    punishUser("com.instagram.android")
                }
            }
        }

        // 2. TikTok Aşaması
        if (packageName == "com.zhiliaoapp.musically") {
            if (!isAppInForeground("com.zhiliaoapp.musically")) return
            if (currentTime - lastPunishTime < 2500 || currentTime - lastHomeActionTime < 2500) return
            if (!ProtectionPreferences.isEnabled(this, ProtectedApp.TIKTOK)) return

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (currentTime - lastPunishTime > 2500) {
                    Log.d(TAG, "TikTok SCROLL DETECTED! Intervening TikTok...")
                    punishUser("com.zhiliaoapp.musically")
                }
            }
        }

        // 3. YouTube Shorts Aşaması
        if (packageName == "com.google.android.youtube") {
            if (!isAppInForeground("com.google.android.youtube")) return
            if (currentTime - lastPunishTime < 2500 || currentTime - lastHomeActionTime < 2500) return
            if (!ProtectionPreferences.isEnabled(this, ProtectedApp.YOUTUBE)) return
            
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val rootNode = rootInActiveWindow
                val isShorts = isYoutubeShortsScreen(rootNode)
                
                if (rootNode != null && isShorts) {
                    if (currentTime - lastPunishTime > 2500) {
                        Log.d(TAG, "YouTube Shorts SCROLL DETECTED! Intervening YouTube...")
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
            text.equals("Yeni gönderi", ignoreCase = true) ||
            text.equals("New post", ignoreCase = true) ||
            text.equals("Galeri", ignoreCase = true) ||
            text.equals("Gallery", ignoreCase = true) ||
            text.equals("Hikaye", ignoreCase = true) ||
            text.equals("Story", ignoreCase = true) ||
            text.equals("Film Rulosu", ignoreCase = true) ||
            text.equals("Camera Roll", ignoreCase = true) ||
            text.equals("Son kullanılanlar", ignoreCase = true) ||
            text.equals("Recents", ignoreCase = true)) {
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

    private fun isDangerousScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!node.isVisibleToUser) return false

        val desc = node.contentDescription?.toString() ?: ""
        
        if (desc.contains("Ana Sayfa", ignoreCase = true) ||
            desc.contains("Home", ignoreCase = true) ||
            desc.contains("Ara ve Keşfet", ignoreCase = true) ||
            desc.contains("Search and explore", ignoreCase = true) ||
            desc.contains("Reels", ignoreCase = true) ||
            desc.contains("Instagram", ignoreCase = true) ||
            desc.contains("Beğen", ignoreCase = true) ||
            desc.contains("Like", ignoreCase = true) ||
            desc.contains("Kaydet", ignoreCase = true) ||
            desc.contains("Save", ignoreCase = true) ||
            desc.contains("Paylaş", ignoreCase = true) ||
            desc.contains("Share", ignoreCase = true) ||
            desc.contains("Repost", ignoreCase = true)) { 
            
            if (node.isClickable || node.parent?.isClickable == true || node.isSelected) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (isDangerousScreen(node.getChild(i))) return true
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

    private fun isStrictlyReelsScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString() ?: ""
        
        // Reels butonu seçiliyse kesinlikle Reels sekmesiyiz
        if (desc.contains("Reels", ignoreCase = true) && node.isSelected) {
            return true
        }
        
        // Reels sekmesine özel üst kamera ikonu
        if (desc.contains("Reels kamerası", ignoreCase = true) || desc.contains("Reels camera", ignoreCase = true)) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (isStrictlyReelsScreen(node.getChild(i))) return true
        }
        return false
    }

    private fun punishUser(targetPackageName: String = "com.instagram.android") {
        if (!ProtectionPreferences.isPackageEnabled(this, targetPackageName)) {
            Log.d(TAG, "$targetPackageName koruması kapalı; engelleme isteği yok sayıldı.")
            return
        }

        val currentTime = System.currentTimeMillis()
        
        // Cooldown check: 2.5s debounce
        if ((currentTime - lastPunishTime < 2500) || (currentTime - lastHomeActionTime < 2500)) return

        lastPunishTime = currentTime
        lastHomeActionTime = currentTime

        Log.d(TAG, "MÜDAHALE EDİLİYOR: $targetPackageName temizleniyor...")
        
        // 1. ANINDA Geri Eylemi (Video oynatıcı katmanını anında geri sar/kapat)
        performGlobalAction(GLOBAL_ACTION_BACK)

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


        // 4. ANINDA ANA EKRANA DÖNÜŞ (HOME)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 5. KULLANICIYA KISA VE ETKİLİ "KAYDIRMA UYARISI" BİLDİRİMİ GÖNDER (Kilit/Pop-up yok)
        val prefs = getSharedPreferences("away_doomscroll_prefs", Context.MODE_PRIVATE)
        val isEn = prefs.getString("app_language", null) == "en"
        val appName = when {
            targetPackageName.contains("musically") -> "TikTok"
            targetPackageName.contains("youtube") -> "YouTube Shorts"
            else -> if (isEn) "Instagram Reels & Feed" else "Instagram Reels & Akış"
        }
        val alertTitle = if (isEn) "Scroll Alert!" else "Kaydırma Uyarısı!"
        val alertMessage = if (isEn) {
            "Doomscrolling interrupted on $appName. Take control of your time!"
        } else {
            "$appName üzerindeki sonsuz kaydırma durduruldu. Zihninin ve vaktinin kontrolünü eline al!"
        }
        showShieldStatusNotification(this, alertTitle, alertMessage, 1004)

        // --- BACK-END GAMIFICATION INTEGRATION ---
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

        // Canlı Kalkan Günlüğü İçin Kayıt (Uygulamanın günlük müdahale sayısı ile)
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val currentTimeStr = timeFormat.format(java.util.Date())
        val appDailyCount = currentDailyAppBlocks + 1
        val newLog = "$currentTimeStr|$appName|$appDailyCount"
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

        // Telemetri açıksa ortak 24 saatlik deneme aralığına tabi bir snapshot iste.
        TelemetryManager.sendTelemetryAsync(this)
        // ------------------------------------------
    }

    private fun isYoutubeShortsScreen(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
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
}
