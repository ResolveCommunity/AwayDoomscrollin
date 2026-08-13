import re

with open('app/src/main/java/com/awaydoomscrollin/app/AntiScrollService.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Replace the ENTIRE scroll block inside 'if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)'
scroll_regex = re.compile(r'if \(event\.eventType == AccessibilityEvent\.TYPE_VIEW_SCROLLED\) \{.*?// 3\. TikTok Aşaması', re.DOTALL)

new_scroll = '''if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val rootNode = rootInActiveWindow
                val sourceNode = event.source

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 5) {
                        return // Ignore horizontal swipes
                    }
                }

                // Check Tab Switch Grace Period (1.5s)
                val isHomeNow = rootNode != null && isHomeScreenActive(rootNode)
                if (isHomeNow && !wasHomeScreenActive) {
                    homeTabSwitchTime = currentTime
                    Log.d(TAG, "Ana Sayfaya Gecis algilandi, Grace Period baslatiliyor.")
                }
                wasHomeScreenActive = isHomeNow

                val isGracePeriod = (currentTime - homeTabSwitchTime < 1500) || (currentTime - instagramLaunchTime < 1500)
                
                if (isGracePeriod) {
                    Log.d(TAG, "Grace period devrede, scroll tolere edildi.")
                    return
                }

                if (isCommentListView(sourceNode)) {
                    return
                }

                if (rootNode != null && isVideoEndOverlayPresent(rootNode)) {
                    return
                }

                if (rootNode != null && (isHomeScreenActive(rootNode) || isReelsTabSelected(rootNode))) {
                    if (currentTime - lastPunishTime > 3000) {
                        Log.d(TAG, "Ana sayfada / Reels'te dikey hareket veya Pull-to-Refresh algılandı! Anında kilitleniyor.")
                        punishUser("com.instagram.android")
                        return
                    }
                }
            }
        }

        // 3. TikTok Aşaması'''

code = scroll_regex.sub(new_scroll, code)

with open('app/src/main/java/com/awaydoomscrollin/app/AntiScrollService.kt', 'w', encoding='utf-8') as f:
    f.write(code)
