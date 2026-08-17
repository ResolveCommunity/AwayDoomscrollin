/**
 * AwayDoomscrollin' - Official Web Site JavaScript Logic
 * Handles language detection (Browser language -> TR or EN default),
 * Endless "bırak." / "now." typing loop animation,
 * Reels video simulator with instant scroll blockage,
 * Interactive Doomscroll Time Calculator, FAQ Accordion, Feedback Form,
 * and Privacy Policy & Terms of Service Modal logic.
 */

// Global FAQ Accordion Toggle function (Available immediately at script parse time)
window.toggleFaq = function(itemEl) {
    if (!itemEl) return;
    const isActive = itemEl.classList.contains('active');
    
    const allFaqs = document.querySelectorAll('.faq-item');
    for (let i = 0; i < allFaqs.length; i++) {
        allFaqs[i].classList.remove('active');
    }

    if (!isActive) {
        itemEl.classList.add('active');
    }
};

// Global Legal Modal Controller (Privacy Policy & Terms of Service)
window.openLegalModal = function(tab) {
    const legalOverlay = document.getElementById('legalModalOverlay');
    const tabPrivacyBtn = document.getElementById('tabPrivacyBtn');
    const tabTermsBtn = document.getElementById('tabTermsBtn');
    const privacyContent = document.getElementById('privacyContent');
    const termsContent = document.getElementById('termsContent');

    if (tab === 'privacy') {
        if (tabPrivacyBtn) tabPrivacyBtn.classList.add('active');
        if (tabTermsBtn) tabTermsBtn.classList.remove('active');
        if (privacyContent) privacyContent.classList.add('active');
        if (termsContent) termsContent.classList.remove('active');
    } else {
        if (tabTermsBtn) tabTermsBtn.classList.add('active');
        if (tabPrivacyBtn) tabPrivacyBtn.classList.remove('active');
        if (termsContent) termsContent.classList.add('active');
        if (privacyContent) privacyContent.classList.remove('active');
    }

    if (legalOverlay) {
        legalOverlay.classList.add('active');
        document.body.style.overflow = 'hidden';
    }
};

window.closeLegalModal = function() {
    const legalOverlay = document.getElementById('legalModalOverlay');
    if (legalOverlay) {
        legalOverlay.classList.remove('active');
        document.body.style.overflow = '';
    }
};

window.switchShowcaseTab = function(tab) {
    const btnHome = document.getElementById('tabBtnHome');
    const btnApps = document.getElementById('tabBtnApps');
    const btnStats = document.getElementById('tabBtnStats');

    const viewHome = document.getElementById('screenViewHome');
    const viewApps = document.getElementById('screenViewApps');
    const viewStats = document.getElementById('screenViewStats');

    if (btnHome) btnHome.classList.toggle('active', tab === 'home');
    if (btnApps) btnApps.classList.toggle('active', tab === 'apps');
    if (btnStats) btnStats.classList.toggle('active', tab === 'stats');

    if (viewHome) viewHome.classList.toggle('active', tab === 'home');
    if (viewApps) viewApps.classList.toggle('active', tab === 'apps');
    if (viewStats) viewStats.classList.toggle('active', tab === 'stats');
};

window.updateFeedbackDeviceRequirement = function() {
    const feedbackCategorySelect = document.getElementById('fbCategory');
    const fbDeviceInput = document.getElementById('fbDevice');
    const fbDeviceLabel = document.getElementById('fbDeviceLabelText') || document.querySelector('label[for="fbDevice"] span');

    if (!feedbackCategorySelect || !fbDeviceInput) return;

    const cat = feedbackCategorySelect.value;
    const isRequired = (cat === 'hata' || cat === 'uyumluluk');
    const lang = window.currentLang || (document.documentElement ? document.documentElement.lang : 'tr') || 'tr';
    const isEn = (lang === 'en');

    fbDeviceInput.required = isRequired;

    if (fbDeviceLabel) {
        if (isRequired) {
            fbDeviceLabel.textContent = isEn ? "Phone Model (Required)" : "Telefon Modeliniz (Zorunlu)";
        } else {
            fbDeviceLabel.textContent = isEn ? "Phone Model (Optional)" : "Telefon Modeliniz (Opsiyonel)";
        }
    }
};

window.checkDeviceCompatibility = function(brand) {
    const isEn = (window.currentLang === 'en');

    const statusBadge = document.getElementById('resultBadgeStatus');
    const osBadge = document.getElementById('resultBadgeOs');
    const title = document.getElementById('resultTitle');
    const desc = document.getElementById('resultDesc');
    const stepsList = document.getElementById('resultStepsList');

    if (!statusBadge || !title || !desc || !stepsList) return;

    const brandData = {
        samsung: {
            statusClass: 'verified',
            statusText: isEn ? '<i class="fa-solid fa-circle-check"></i> 100% Verified Support' : '<i class="fa-solid fa-circle-check"></i> %100 Doğrulanmış Uyum',
            os: 'One UI 4.0 - 6.1',
            title: isEn ? 'Samsung Galaxy Setup Status:' : 'Samsung Galaxy Cihazlar İçin Durum:',
            desc: isEn ? '100% Tested. Samsung One UI seamlessly supports Accessibility Services without aggressive process killing.' : '%100 Test Edildi. Samsung One UI arayüzü Erişilebilirlik Servislerini kesintisiz destekler. Ekstra pil optimizasyonu istisnası tanımlamanıza gerek kalmadan kalkan 7/24 aktif çalışır.',
            steps: isEn ? [
                'Open Android Settings > Accessibility > Installed Apps.',
                'Toggle AwayDoomscrollin\' service to <strong>ON</strong>.'
            ] : [
                'Ayarlar > Erişilebilirlik > Yüklü Uygulamalar bölümüne gidin.',
                'AwayDoomscrollin\' servisini <strong>AÇIK</strong> konuma getirin.'
            ]
        },
        xiaomi: {
            statusClass: 'warning',
            statusText: isEn ? '<i class="fa-solid fa-triangle-exclamation"></i> 85% Compatibility (Autostart Required)' : '<i class="fa-solid fa-triangle-exclamation"></i> %85 Uyum (Otomatik Başlatma Gerekebilir)',
            os: 'MIUI 12 - 14 / HyperOS',
            title: isEn ? 'Xiaomi / Redmi / Poco Setup Status:' : 'Xiaomi / Redmi / Poco Cihazlar İçin Durum:',
            desc: isEn ? 'MIUI / HyperOS uses aggressive RAM management. To prevent shield suspension, Autostart and Battery Saver exemptions must be enabled.' : 'MIUI / HyperOS agresif pil tasarrufu uygular. Kalkanın durmaması için Otomatik Başlatma ve Pil Tasarrufu istisnalarının verilmesi önerilir.',
            steps: isEn ? [
                'Settings > Apps > Manage Apps > AwayDoomscrollin\'.',
                'Enable <strong>Autostart</strong> toggle.',
                'Set Battery Saver to <strong>No Restrictions</strong>.'
            ] : [
                'Ayarlar > Uygulamalar > Uygulamaları Yönet > AwayDoomscrollin\'.',
                '<strong>Otomatik Başlatma</strong> iznini aktif yapın.',
                'Pil Tasarrufu seçeneğini <strong>Kısıtlama Yok</strong> yapın.'
            ]
        },
        oppo: {
            statusClass: 'warning',
            statusText: isEn ? '<i class="fa-solid fa-triangle-exclamation"></i> 80% Compatibility (Background Permission Needed)' : '<i class="fa-solid fa-triangle-exclamation"></i> %80 Uyum (Arka Plan İzni Gerekebilir)',
            os: 'ColorOS / Realme UI',
            title: isEn ? 'Oppo / Realme Setup Status:' : 'Oppo / Realme Cihazlar İçin Durum:',
            desc: isEn ? 'ColorOS may suspend background services when idle. Grant background execution permission for continuous shield protection.' : 'ColorOS arka plan servislerini durdurabilir. Kesintisiz koruma için arka planda çalışmaya izin verilmelidir.',
            steps: isEn ? [
                'Settings > Battery > App Battery Management > AwayDoomscrollin\'.',
                'Turn ON <strong>Allow Background Activity</strong>.'
            ] : [
                'Ayarlar > Pil > Arka Plan Uygulama Yönetimi > AwayDoomscrollin\'.',
                '<strong>Arka Planda Çalışmaya İzin Ver</strong> seçeneğini açın.'
            ]
        },
        vivo: {
            statusClass: 'warning',
            statusText: isEn ? '<i class="fa-solid fa-triangle-exclamation"></i> 80% Compatibility (Background Permission Needed)' : '<i class="fa-solid fa-triangle-exclamation"></i> %80 Uyum (Arka Plan İzni Gerekebilir)',
            os: 'Funtouch OS / OriginOS',
            title: isEn ? 'Vivo / iQOO Setup Status:' : 'Vivo / iQOO Cihazlar İçin Durum:',
            desc: isEn ? 'iManager battery saver can suspend background shield execution. Enable high power consumption permission.' : 'iManager pil tasarrufu arka plan kalkanını durdurabilir. Yüksek güç tüketimi iznini aktifleştirin.',
            steps: isEn ? [
                'Open iManager or Settings > Battery.',
                'Under High Power Consumption, enable <strong>AwayDoomscrollin\'</strong>.'
            ] : [
                'iManager veya Ayarlar > Pil bölümünü açın.',
                'Yüksek Güç Tüketimi altında <strong>AwayDoomscrollin\'</strong> iznini açın.'
            ]
        },
        pixel: {
            statusClass: 'verified',
            statusText: isEn ? '<i class="fa-solid fa-circle-check"></i> 95% Verified Support' : '<i class="fa-solid fa-circle-check"></i> %95 Doğrulanmış Uyum',
            os: 'Stock Android 8.0 - 14',
            title: isEn ? 'Google Pixel Setup Status:' : 'Google Pixel Cihazlar İçin Durum:',
            desc: isEn ? 'Stock Android operates cleanly without custom ROM killers. Standard Accessibility permission is all that is required.' : 'Saf Android arayüzü tam uyumludur. Erişilebilirlik iznini vermeniz kalkanın çalışması için yeterlidir.',
            steps: isEn ? [
                'Settings > Accessibility > AwayDoomscrollin\'.',
                'Enable Accessibility Service.'
            ] : [
                'Ayarlar > Erişilebilirlik > AwayDoomscrollin\'.',
                'Erişilebilirlik Hizmetini aktif yapın.'
            ]
        },
        huawei: {
            statusClass: 'warning',
            statusText: isEn ? '<i class="fa-solid fa-triangle-exclamation"></i> 75% Compatibility (App Launch Exemption)' : '<i class="fa-solid fa-triangle-exclamation"></i> %75 Uyum (Başlatma İzni Gerekebilir)',
            os: 'EMUI / HarmonyOS',
            title: isEn ? 'Huawei / Honor Setup Status:' : 'Huawei / Honor Cihazlar İçin Durum:',
            desc: isEn ? 'EMUI App Launch manager may close background services. Set launch management to manual.' : 'EMUI Başlatma Yöneticisi arka plan servislerini sonlandırabilir. Başlatma yönetimini elle ayarlayın.',
            steps: isEn ? [
                'Settings > Apps > App Launch > AwayDoomscrollin\'.',
                'Set to <strong>Manage Manually</strong> and enable Autostart & Run in Background.'
            ] : [
                'Ayarlar > Uygulamalar > Başlatma Yönetimi > AwayDoomscrollin\'.',
                '<strong>Elle Yönet</strong> yapıp Otomatik Başlatma ve Arka Planda Çalışma izinlerini açın.'
            ]
        },
        other: {
            statusClass: 'verified',
            statusText: isEn ? '<i class="fa-solid fa-circle-check"></i> 75% - 90% Compatibility' : '<i class="fa-solid fa-circle-check"></i> %75 - %90 Uyum',
            os: 'Android 8.0+',
            title: isEn ? 'General Android Setup Status:' : 'Diğer Android Cihazlar İçin Durum:',
            desc: isEn ? 'Fully supported on most Android devices. If shield stops unexpectedly, remove battery optimization under Settings > Battery.' : 'Çoğu Android cihazında sorunsuz çalışır. Kalkan durursa Ayarlar > Pil bölümünden AwayDoomscrollin\' için pil kısıtlamalarını kaldırın.',
            steps: isEn ? [
                'Settings > Accessibility > Enable AwayDoomscrollin\'.',
                'Ensure Battery Optimization is set to Unrestricted.'
            ] : [
                'Ayarlar > Erişilebilirlik > AwayDoomscrollin\' iznini verin.',
                'Pil Kısıtlamasını "Kısıtlanmamış" olarak ayarlayın.'
            ]
        }
    };

    const d = brandData[brand] || brandData.samsung;

    statusBadge.className = 'result-badge ' + d.statusClass;
    statusBadge.innerHTML = d.statusText;
    osBadge.textContent = d.os;
    title.textContent = d.title;
    desc.textContent = d.desc;

    stepsList.innerHTML = d.steps.map(step => `<li>${step}</li>`).join('');
};

document.addEventListener('DOMContentLoaded', () => {
    // --------------------------------------------------------------------------
    // 1. Language Detection & i18n Translation Dictionary
    // --------------------------------------------------------------------------
    const i18n = {
        en: {
            nav_features: "Features",
            nav_comp: "Why Us?",
            nav_calculator: "Calculator",
            nav_apps: "Apps",
            nav_compatibility: "Device Compatibility",
            nav_faq: "FAQ",
            nav_feedback: "Feedback",
            nav_download: "Download (APK)",

            vs_tag: "Why Are We Different?",
            vs_title: "AwayDoomscrollin' vs Traditional Well-Being Apps",
            vs_desc: "Traditional 'digital wellbeing' apps rely on bargaining limits and willpower wars. We break the unconscious vertical scrolling reflex directly at the source.",
            vs_col_feature: "Feature & Approach",
            vs_col_traditional: "Traditional Well-Being Apps",
            vs_badge_winner: "The Solution 🔥",

            vs_row1_title: "Blocking Mechanism",
            vs_row1_sub: "How is the limit enforced?",
            vs_badge_row1_trad: "Time Limit (Bargaining)",
            vs_badge_row1_away: "Reflex Breaker (Instant)",
            vs_row1_trad: "\"Allow 15 mins daily.\" Your brain exploits this limit to the last second, leading to a constant willpower battle when time expires.",
            vs_row1_away: "No bargaining! The instant an unconscious scroll gesture is detected, the shield intervenes and throws you back to the home screen.",

            vs_row2_title: "User Psychology & Withdrawal",
            vs_row2_sub: "How do you feel while using it?",
            vs_badge_row2_trad: "Withdrawal & FOMO",
            vs_badge_row2_away: "Mental Freedom",
            vs_row2_trad: "When time expires, the app locks down completely, causing FOMO (fear of missing out) and heavy withdrawal cravings.",
            vs_row2_away: "Zero restriction anxiety! It only stops the 'mindless scroll trap'. You naturally exit the dopamine loop without craving.",

            vs_row3_title: "DM & Comment Protection (Safe Zone)",
            vs_row3_sub: "Is messaging blocked?",
            vs_badge_row3_trad: "Entire App Locked",
            vs_badge_row3_away: "Smart Safe Zone",
            vs_row3_trad: "The entire app gets locked. You can't even reply to your friends' urgent direct messages (DMs).",
            vs_row3_away: "Smart Safe Zone! Direct messages and reading comments remain freely accessible. Only vertical feed scrolling is blocked.",

            vs_row4_title: "Privacy & Advertising",
            vs_row4_sub: "Where is your data processed?",
            vs_badge_row4_trad: "Ads / Paid / Cloud Data",
            vs_badge_row4_away: "Privacy First & Open Source",
            vs_row4_trad: "Many apps process usage stats on external cloud servers, sell monthly subscriptions, or show invasive ads.",
            vs_row4_away: "Privacy First! Screen analysis stays on-device. Default-on telemetry can be disabled; GitHub rule updates run automatically. 100% free and fully open-source.",

            vs_row5_title: "Anti-Cheat Protection",
            vs_row5_sub: "How easy is it to bypass?",
            vs_badge_row5_trad: "Easily Bypassed",
            vs_badge_row5_away: "Awareness Anti-Cheat Prompt",
            vs_row5_trad: "Impulsively disabled in 1 click via Android Settings or notification drawer whenever craving hits.",
            vs_row5_away: "Anti-Cheat shield triggers a guilt & awareness prompt whenever you try to disable it, breaking impulsive bypass attempts.",

            hero_badge: "Android Accessibility-Based Anti-Scroll Shield",
            hero_heading_base: "Stop scrolling ",
            hero_heading_word: "now.",
            hero_subtitle: "Automatically break the infinite scrolling loop on Instagram, TikTok, and YouTube. Try the shield on 5 live Reels videos in the simulator on the right!",
            hero_btn_download: "Download App (APK)",
            hero_btn_calc: "Calculate Your Time",
            hero_spec_samsung: "Samsung One UI 100% Compatible",
            hero_spec_privacy: "Privacy First & Default-On Telemetry",
            hero_spec_safezone: "Comments & DM Safe Zone Free",

            store_get_it_on: "GET IT ON",
            store_direct: "DIRECT DOWNLOAD",
            store_github_releases: "AVAILABLE ON",

            reels_caption_1: "1st Video — Scroll down to trigger the shield! 👇",
            reels_caption_2: "2nd Video — Infinite feed blocked...",
            reels_caption_3: "3rd Video — Time reclaimed! ⚡",
            reels_caption_4: "4th Video — Mindless scrolling ended!",
            reels_caption_5: "5th Video — All 5 videos completed!",
            reels_hint: "Scroll Down (Demo)",
            shield_overlay_title: "SCROLLING BLOCKED!",
            shield_overlay_sub: "AwayDoomscrollin' shield detected scrolling addiction and auto-stopped the app.",
            shield_overlay_badge: "Daily Streak Kept Alive!",
            shield_overlay_btn: "Try Next Video →",

            calc_tag: "Awareness Analysis",
            calc_title: "Calculate Your Time: What Are You Losing Each Year?",
            calc_desc: "Short videos seem harmless at first. Choose your daily screen time to discover the lost hours in your life.",
            calc_slider_label: "How much time do you average daily on Instagram / TikTok / YouTube?",
            calc_result_monthly_title: "Monthly Time Lost (2.5 Days)",
            calc_result_days_title: "Full Days Spent Per Year (24-Hour)",
            calc_result_books_title: "Books You Could Have Read",
            calc_result_skills_title: "Hours for New Hobbies & Skills",

            feat_tag: "Advanced Protection",
            feat_title: "Cyber Features Protecting Your Focus",
            feat_desc: "AwayDoomscrollin' isn't just a blocker; it's a smart habit transformer that breaks your unconscious scrolling reflex.",
            feat_1_title: "Automatic Shield",
            feat_1_desc: "The instant scrolling is detected in Reels, TikTok, or Shorts, the background service intervenes and redirects you to the safe home screen.",
            feat_2_title: "Smart Safe Zone",
            feat_2_desc: "Scrolling in DMs and Comment sections is freely allowed. Only infinite vertical video feed scrolling is targeted.",
            feat_3_title: "Anti-Cheat System",
            feat_3_desc: "When your subconscious tries to disable the shield in Settings, the Anti-Cheat mechanism kicks in to remind you of your focus goal.",
            feat_4_title: "Streak & Statistics Tracking",
            feat_4_desc: "Visualize your digital freedom process with daily streak tracking and detailed block statistics.",

            showcase_tag: "Cyber Interface",
            showcase_title: "Explore AwayDoomscrollin' Android App",
            showcase_desc: "Designed with Jetpack Compose Material3. Clean dark mode aesthetics with zero clutter.",
            showcase_tab_home: "Shield Status",
            showcase_tab_apps: "Modes & Apps",
            showcase_tab_stats: "Streak & Progress",
            mock_home_shield_active: "SHIELD ACTIVE",
            mock_home_shield_sub: "Instagram, TikTok and YouTube Protected",
            mock_metric_saved: "Time Reclaimed",
            mock_metric_blocks: "Interventions",
            mock_apps_header: "Target Platforms",
            mock_apps_status_badge: "3/3 ACTIVE",
            mock_app_insta_sub: "DMs & Comments Allowed",
            mock_app_tiktok_sub: "Auto Intercept",
            mock_app_yt_sub: "Shorts Tab Only",
            mock_stats_header: "Progress & Streak",
            mock_stats_streak_badge: "7 DAYS",
            mock_chart_lbl: "Weekly Interventions Summary",
            mock_metric_streak: "Daily Streak",
            mock_metric_streak_val: "7 Days",
            mock_metric_total_blocks: "Total Interventions",
            mock_log_title: "Live Shield Activity Log",
            mock_log_live: "LIVE",
            mock_log_insta_count: "Blocked 8 times today",
            mock_log_tiktok_count: "Blocked 4 times today",
            mock_log_yt_count: "Blocked 2 times today",

            steps_tag: "3-Step Setup",
            steps_title: "How It Works",
            steps_desc: "No complex settings required. Activate your shield in seconds.",
            step_1_title: "Grant Accessibility Permission",
            step_1_desc: "Enable AwayDoomscrollin' service under Android Settings > Accessibility.",
            step_2_title: "Select Target Apps",
            step_2_desc: "Enable protection toggles for Instagram Reels, TikTok, and YouTube Shorts.",
            step_3_title: "Reclaim Your Time",
            step_3_desc: "The moment you scroll in targeted apps, the shield intervenes and pulls you out of the loop.",

            apps_tag: "Supported Platforms",
            apps_title: "Covered Short Video Platforms",
            apps_desc: "We directly cover the top 3 short video addiction platforms (Instagram Reels, TikTok, YouTube Shorts).",
            app_insta_status: "FULL SUPPORTED",
            app_insta_desc: "Detects scrolling on Reels while allowing messaging and comment reading without blocking.",
            app_tiktok_status: "BETA",
            app_tiktok_desc: "Detects infinite feed scrolling, stops the app, and redirects to home screen.",
            app_yt_status: "BETA",
            app_yt_desc: "Does not block regular videos; only stops vertical scrolling in the Shorts tab.",

            comp_tag: "Hardware & Device Support",
            comp_title: "Device Compatibility Status",
            comp_desc: "AwayDoomscrollin' relies on Android system architecture, which may vary across manufacturer accessibility policies.",
            comp_samsung_title: "Samsung Galaxy (One UI)",
            comp_samsung_sub: "Fully Verified Device Support",
            comp_samsung_desc: "100% tested and verified on Samsung Galaxy devices (One UI). Accessibility service runs uninterrupted.",
            comp_other_title: "Other Android Devices",
            comp_other_sub: "Xiaomi, Oppo, Vivo, Pixel, Huawei etc.",
            comp_other_desc: "Ongoing tests for custom Android ROMs (MIUI, ColorOS, OriginOS). Continuously improved with user feedback.",

            checker_title: "Check Your Specific Phone Model",
            checker_subtitle: "Select your phone brand below to reveal tailored compatibility status and battery optimization setup instructions.",
            checker_label: "Your Phone Brand:",
            checker_guide_title: "Recommended Setup Steps:",

            faq_tag: "Common Questions",
            faq_title: "Frequently Asked Questions",
            faq_desc: "Here are answers to the most common questions about AwayDoomscrollin'.",
            faq_q1: "Is Accessibility permission safe? Will my data be stolen?",
            faq_a1: "Privacy First. Screen-content analysis and blocking run locally. Pseudonymous telemetry is enabled by default and can be disabled in the app; while enabled it sends a random per-installation ID, the disclosed device/app fields, and aggregate blocking statistics to awaydoomscrollin.com. The ID is not derived from a hardware, advertising, or account identifier. GitHub rule updates run automatically and independently of the telemetry switch. Accessibility permission is used to detect target screens and scrolling gestures.",
            faq_q2: "Will scrolling in Instagram DMs or Comments be blocked?",
            faq_a2: "No! Thanks to our Smart Safe Zone algorithm, scrolling in direct messages and comment reading sheets is recognized as a safe zone. You can read comments comfortably without any blocking.",
            faq_q3: "Does the app consume much battery in the background?",
            faq_a3: "No. Our app uses no background polling loops. It operates purely in an event-driven architecture triggered by the Android system only when you enter Instagram Reels, TikTok, or YouTube Shorts. Battery consumption is virtually zero.",
            faq_q4: "Is AwayDoomscrollin' completely free?",
            faq_a4: "Yes! AwayDoomscrollin' is developed with a community-driven open vision. All shield modes, statistics, and Anti-Cheat features are 100% free.",
            faq_q5: "What should I do if the shield doesn't work on non-Samsung phones?",
            faq_a5: "For brands with aggressive battery managers like Xiaomi, Vivo, or Oppo, you may need to enable Auto-Start permissions for AwayDoomscrollin' under Settings. If you face issues, send us your phone model via the Feedback form.",

            fb_tag: "Community & Support",
            fb_title: "Feedback & Bug Report",
            fb_desc: "Encountered a bug or have an idea to make the app even better? Share it with us!",
            fb_email_label: "Your Email (Required for Reply)",
            fb_email_placeholder: "example@gmail.com",
            fb_device_placeholder: "e.g. Samsung A52, Xiaomi Redmi Note 12",
            fb_type_label: "Feedback Type",
            fb_type_opt_bug: "🐛 Bug Report",
            fb_type_opt_feat: "💡 Feature Suggestion",
            fb_type_opt_compat: "📱 Device Compatibility Report",
            fb_type_opt_other: "💬 General Message",
            fb_msg_label: "Your Message / Issue",
            fb_msg_placeholder: "Briefly describe the bug, your phone model, or your suggestion...",
            fb_captcha_notice: "Protected by Cloudflare & FormSubmit Anti-Spam CAPTCHA",
            fb_btn_submit: "Send Feedback",

            cta_title: "End Unconscious Scrolling Today",
            cta_desc: "Reclaim an average of 2.5 hours every day. Download AwayDoomscrollin' Android app now.",
            cta_btn: "Download AwayDoomscrollin' v1.0 APK",
            footer_rights: "© 2026 AwayDoomscrollin'. All rights reserved.",
            footer_privacy: "Privacy Policy",
            footer_terms: "Terms of Service",

            modal_tab_privacy: "Privacy Policy",
            modal_tab_terms: "Terms of Service",
            legal_privacy_title: "Privacy Policy",
            legal_terms_title: "Terms of Service",
            legal_last_updated: "Last Updated: August 17, 2026",

            legal_p1_title: "1. Introduction & Transparency Commitment",
            legal_p1_text: "At AwayDoomscrollin', we believe digital well-being tools should disclose their behavior clearly. This Privacy Policy explains how the App handles accessibility data, local storage, default-on pseudonymous telemetry, and automatic GitHub rule updates. We do not sell personal information or use third-party advertising analytics.",
            legal_p2_title: "2. Network Data Collection",
            legal_p2_text: "Screen-content analysis and blocking run on-device. Pseudonymous telemetry is enabled by default and can be disabled in the App. While enabled, it sends a random per-installation ID, device manufacturer/model, Android and SDK version, app version, aggregate platform/total block counts, streak days, and XP to awaydoomscrollin.com. The installation ID is generated locally, is not derived from a hardware, advertising, or account identifier, and is removed when the App's data is cleared or the App is uninstalled. Separately, the App automatically contacts GitHub at app or accessibility-service startup for rule/configuration updates; successful fetches are cached for six hours and are not controlled by the telemetry switch. Screen contents, messages, passwords, and location are not transmitted.",
            legal_p3_title: "3. Accessibility Service Permission & Scope",
            legal_p3_text1: "To detect endless scrolling behavior on target short video platforms (Instagram Reels, TikTok, and YouTube Shorts) and perform intervention actions, AwayDoomscrollin' utilizes the Android AccessibilityService API.",
            legal_p3_text2: "Within the supported Instagram, TikTok, and YouTube packages, the service receives scroll, window-state, window-content, and click accessibility events. It inspects accessibility node text, content descriptions, and view identifiers in memory to distinguish target feeds from safe areas. When a target feed is detected, it returns the device to the home screen and may open Android Settings to force stop the target app.",
            legal_p3_text3: "Accessibility content is evaluated in memory and is not retained or transmitted. Screen text, messages, comments, passwords, keystrokes, credit-card information, contacts, photos, and browsing history are not included in telemetry or remote-rule requests.",
            legal_p4_title: "4. Local Data Storage & Preferences",
            legal_p4_text: "Blocking statistics, streak counters, target-app toggles, recent shield activity, and the random installation ID are stored locally via SharedPreferences ('away_doomscroll_prefs') and are removed when the App's data is cleared or the App is uninstalled. While telemetry is enabled, the installation ID and disclosed aggregate block, streak, and XP fields are also submitted to awaydoomscrollin.com.",
            legal_p5_title: "5. Third-Party Libraries & Advertisements",
            legal_p5_text: "The App contains zero third-party advertising SDKs, zero tracking pixels, and zero commercial analytics tools. There are no sponsored ad networks integrated into AwayDoomscrollin'.",
            legal_p6_title: "6. User Control & Permission Revocation",
            legal_p6_text: "You retain 100% control over the App at all times. You may toggle protection shields on or off for individual apps inside the App interface, or revoke Accessibility permissions at any time via Android Settings > Accessibility > AwayDoomscrollin'.",

            legal_t1_title: "1. Acceptance of Terms",
            legal_t1_text: "By downloading, installing, or using AwayDoomscrollin', you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not install or use the App.",
            legal_t2_title: "2. Purpose of the Application",
            legal_t2_text: "AwayDoomscrollin' is a self-control and digital well-being utility designed to help users break compulsive short video scrolling habits on Android devices. The App functions as a personal commitment device by detecting scrolling gestures in specified target apps and redirecting users to the home screen.",
            legal_t3_title: "3. User Responsibilities & Device Configuration",
            legal_t3_text: "Users are responsible for properly configuring required Android system permissions (Accessibility Service, Battery Optimization exemptions) for the App to operate effectively. Users acknowledge that disabling system permissions will suspend shield functionality.",
            legal_t4_title: "4. Compatibility & Manufacturer Variations",
            legal_t4_text: "While AwayDoomscrollin' is thoroughly tested for Samsung Galaxy devices (One UI), Android operating system behavior varies across different device manufacturers (e.g., Xiaomi MIUI, Oppo ColorOS, Vivo, Pixel). We do not guarantee uninterrupted execution on custom vendor ROMs with aggressive background process killers.",
            legal_t5_title: "5. Disclaimer of Warranties & Limitation of Liability",
            legal_t5_text: "AwayDoomscrollin' is provided on an 'AS IS' and 'AS AVAILABLE' basis without warranties of any kind, whether express or implied. In no event shall the developers be liable for any direct, indirect, incidental, or consequential damages resulting from the use or inability to use the App, or force-stopping target applications.",
            legal_t6_title: "6. Open Source & License",
            legal_t6_text: "AwayDoomscrollin' is developed with an open, community-first vision. All branding, logos, and software components are protected under open-source software practices and applicable copyright laws."
        },
        tr: {
            nav_features: "Özellikler",
            nav_comp: "Neden Farklıyız?",
            nav_calculator: "Hesaplayıcı",
            nav_apps: "Uygulamalar",
            nav_compatibility: "Cihaz Uyumluluğu",
            nav_faq: "SSS",
            nav_feedback: "Geri Bildirim",
            nav_download: "İndir (APK)",

            vs_tag: "Neden Farklıyız?",
            vs_title: "AwayDoomscrollin' vs Geleneksel Well-Being Uygulamaları",
            vs_desc: "Geleneksel 'dijital refah' uygulamaları irade savaşı ve pazarlık sınırları koyar. Biz ise bilinçsiz kaydırma refleksini doğrudan kaynağında kırıyoruz.",
            vs_col_feature: "Özellik & Yaklaşım",
            vs_col_traditional: "Geleneksel Well-Being Uygulamaları",
            vs_badge_winner: "Çözüm 🔥",

            vs_row1_title: "Engelleme Mantığı",
            vs_row1_sub: "Sınır nasıl uygulanır?",
            vs_badge_row1_trad: "Sınırlı Süre (Pazarlık)",
            vs_badge_row1_away: "Refleks Kırma (Anında)",
            vs_row1_trad: "\"Günde 15 dk izin ver.\" Beyniniz bu süreyi son saniyesine kadar sömürür ve süre bitince irade savaşı başlar.",
            vs_row1_away: "Süre pazarlığı yok! Bilinçsiz kaydırma hareketini algıladığı an kalkan devreye girer ve sizi ana ekrana fırlatır.",

            vs_row2_title: "Kullanıcı Psikolojisi & Yoksunluk",
            vs_row2_sub: "Kullanırken ne hissedersiniz?",
            vs_badge_row2_trad: "Yoksunluk & FOMO",
            vs_badge_row2_away: "Zihinsel Özgürlük",
            vs_row2_trad: "Süre bittiğinde uygulama tamamen kilitlenir; kaçırma korkusu (FOMO) ve yoksunluk hissi yaratır.",
            vs_row2_away: "Sizi kısıtlamaz! Yalnızca 'otomatik kaydırma tuzağını' durdurur. Dopamin döngüsünden doğal olarak çıkarsınız.",

            vs_row3_title: "DM & Yorum Koruması (Safe Zone)",
            vs_row3_sub: "Mesajlaşma engellenir mi?",
            vs_badge_row3_trad: "Tüm Uygulama Kilitlenir",
            vs_badge_row3_away: "Akıllı Güvenli Bölge",
            vs_row3_trad: "Süre dolunca Instagram/TikTok tamamen kilitlenir. Arkadaşlarınızın mesajlarına dahi bakamazsınız.",
            vs_row3_away: "DM mesajlaşmaları ve yorum okuma serbesttir! Sadece bilinçsiz dikey video kaydırma hareketleri engellenir.",

            vs_row4_title: "Gizlilik & Reklamlar",
            vs_row4_sub: "Verileriniz nerede saklanır?",
            vs_badge_row4_trad: "Reklamlı / Ücretli / Sunucuya Veri",
            vs_badge_row4_away: "Şeffaf Ağ Kullanımı & Açık Kaynak",
            vs_row4_trad: "Pek çok uygulama kullanım verilerinizi sunuculara işler, aylık abonelik satar veya reklam gösterir.",
            vs_row4_away: "Ekran analizi cihazda kalır. Varsayılan açık telemetri kapatılabilir; GitHub kural güncellemeleri otomatik çalışır. %100 ücretsiz ve tamamen açık kaynak kodludur.",

            vs_row5_title: "Hile & Dürtüsel Kapatma (Anti-Cheat)",
            vs_row5_sub: "Uygulamayı kapatmak ne kadar kolay?",
            vs_badge_row5_trad: "Kolayca Kapatılabilir",
            vs_badge_row5_away: "Farkındalık Popup'ı",
            vs_row5_trad: "Dürtüsel olarak Ayarlar'dan veya bildirim panelinden tek tıkla engeli kaldırıp kaydırmaya devam edebilirsiniz.",
            vs_row5_away: "Anti-Cheat kalkanı devreye girer; kapatmaya çalıştığınızda suçluluk & farkındalık ekranı açarak dürtünüzü kırar.",

            hero_badge: "Android Erişilebilirlik Tabanlı Anti-Scroll Kalkanı",
            hero_heading_base: "Artık kaydırmayı ",
            hero_heading_word: "bırak.",
            hero_subtitle: "Instagram, TikTok ve YouTube sonsuz kaydırma döngüsünü otomatik olarak durdurun. Sağ taraftaki canlı simülatörde 5 farklı Reels videosunda kalkanı deneyin!",
            hero_btn_download: "Uygulamayı İndir",
            hero_btn_calc: "Zamanını Hesapla",
            hero_spec_samsung: "Samsung One UI %100 Uyumlu",
            hero_spec_privacy: "%100 Yerel Veri & Gizlilik",
            hero_spec_safezone: "Yorumlar & DM Serbest Güvenli Bölge",

            store_get_it_on: "İNDİRİN",
            store_direct: "DOĞRUDAN İNDİRİN",
            store_github_releases: "YAYINLARDAN İNDİRİN",

            reels_caption_1: "1. Video — Aşağı kaydırarak kalkanı tetikleyin! 👇",
            reels_caption_2: "2. Video — Sonsuz akış engelleniyor...",
            reels_caption_3: "3. Video — Zamanınızı geri kazandınız! ⚡",
            reels_caption_4: "4. Video — Bilinçsiz kaydırma sonlandı!",
            reels_caption_5: "5. Video — 5 video başarıyla tamamlandı!",
            reels_hint: "Aşağı Kaydırın (Demo)",
            shield_overlay_title: "KAYDIRMA ENGELLENDİ!",
            shield_overlay_sub: "AwayDoomscrollin' kalkanı kaydırma bağımlılığını algıladı ve uygulamayı otomatik durdurdu.",
            shield_overlay_badge: "Günlük Seri Korundu!",
            shield_overlay_btn: "Sonraki Videoya Geç & Dene",

            calc_tag: "Farkındalık Analizi",
            calc_title: "Zamanını Hesapla: Yılda Ne Kaybediyorsun?",
            calc_desc: "Kısa videolar başlangıçta zararsız görünür. Günlük ekran sürenizi seçin, hayatınızdan kaybolan zamanı keşfedin.",
            calc_slider_label: "Günde ortalama ne kadar Instagram / TikTok / YouTube kaydırıyorsun?",
            calc_result_monthly_title: "Aylık Kaybedilen Zaman (2.5 Gün)",
            calc_result_days_title: "Yılda Harcanan Tam Gün (24 Saatlik)",
            calc_result_books_title: "Okunabilecek Kitap Sayısı",
            calc_result_skills_title: "Yeni Hobi & Beceri Kazanma Saati",

            feat_tag: "Gelişmiş Koruma",
            feat_title: "Odağınızı Koruyan Siber Özellikler",
            feat_desc: "AwayDoomscrollin' sadece bir engelleyici değil; bilinçsiz kaydırma refleksinizi kıran akıllı bir alışkanlık değiştiricidir.",
            feat_1_title: "Otomatik Kalkan",
            feat_1_desc: "Instagram Reels, TikTok veya Shorts'ta kaydırma algılandığı anda uygulama arka planda müdahale eder ve sizi güvenli ana ekrana yönlendirir.",
            feat_2_title: "Akıllı Güvenli Bölge",
            feat_2_desc: "DM mesajlaşmaları ve Yorum Okuma sayfalarında kaydırmak serbesttir. Yalnızca sonsuz dikey video akış kaydırmaları hedef alınır.",
            feat_3_title: "Anti-Cheat Sistemi",
            feat_3_desc: "Bilinçaltınız kalkanı Ayarlar'dan kapatmaya çalıştığında Anti-Cheat mekanizması devreye girer ve odaklanma kararınızı hatırlatır.",
            feat_4_title: "Streak & İstatistik Takibi",
            feat_4_desc: "Günlük seri (streak) takibi ve detaylı engelleme istatistikleri ile dijital bağımsızlık sürecinizi görselleştirin.",

            showcase_tag: "Siber Arayüz",
            showcase_title: "AwayDoomscrollin' Android Uygulamasına Yakından Bakın",
            showcase_desc: "Jetpack Compose Material3 ile tasarlandı. Şık koyu mod ve göz yormayan sade tasarım.",
            showcase_tab_home: "Kalkan Durumu",
            showcase_tab_apps: "Modlar & Uygulamalar",
            showcase_tab_stats: "Streak & İlerleme",
            mock_home_shield_active: "KALKAN AKTİF",
            mock_home_shield_sub: "Instagram, TikTok ve YouTube Koruma Altında",
            mock_metric_saved: "Kurtarılan Zaman",
            mock_metric_blocks: "Engelleme",
            mock_apps_header: "Hedef Uygulamalar",
            mock_apps_status_badge: "3/3 AKTİF",
            mock_app_insta_sub: "DM & Yorumlar Serbest",
            mock_app_tiktok_sub: "Otomatik Durdurma",
            mock_app_yt_sub: "Sadece Shorts Sekmesi",
            mock_stats_header: "İlerleme & Streak",
            mock_stats_streak_badge: "7 GÜN",
            mock_chart_lbl: "Haftalık Engelleme Özeti",
            mock_metric_streak: "Günlük Seri",
            mock_metric_streak_val: "7 Gün",
            mock_metric_total_blocks: "Toplam Engelleme",
            mock_log_title: "Canlı Kalkan Akışı",
            mock_log_live: "CANLI",
            mock_log_insta_count: "Bugün 8 kez engellendi",
            mock_log_tiktok_count: "Bugün 4 kez engellendi",
            mock_log_yt_count: "Bugün 2 kez engellendi",

            steps_tag: "3 Adımda Kurulum",
            steps_title: "Nasıl Çalışır?",
            steps_desc: "Karmaşık ayarlara gerek yok. Birkaç saniye içinde kalkanınızı aktif hale getirin.",
            step_1_title: "Erişilebilirlik İznini Verin",
            step_1_desc: "Android Ayarları > Erişilebilirlik altından AwayDoomscrollin' servisine izin tanımlayın.",
            step_2_title: "Hedef Uygulamaları Seçin",
            step_2_desc: "Instagram Reels, TikTok ve YouTube Shorts için kalkan toggle düğmelerini aktifleştirin.",
            step_3_title: "Zamanınızı Geri Kazanın",
            step_3_desc: "Uygulamada kaydırma yaptığınız an kalkan devreye girer ve sizi sonsuz döngüden çıkarır.",

            apps_tag: "Uyumlu Platformlar",
            apps_title: "Desteklenen Kısa Video Alanları",
            apps_desc: "Kısa video bağımlılığı yaratan en popüler 3 mecrayı (Instagram Reels, TikTok, YouTube Shorts) doğrudan kapsıyoruz.",
            app_insta_status: "TAM DESTEKLİ",
            app_insta_desc: "Reels ekranındaki seri kaydırmaları algılar, mesajlaşma ve yorum okuma alanlarında engelleme yapmadan tam koruma sağlar.",
            app_tiktok_status: "BETA",
            app_tiktok_desc: "Sonsuz akış kaydırmalarını tespit ederek uygulamayı durdurur ve ana ekrana yönlendirir.",
            app_yt_status: "BETA",
            app_yt_desc: "Normal videoları engellemez, yalnızca Shorts sekmesindeki dikey kaydırma hareketlerini durdurur.",

            comp_tag: "Donanım & Cihaz Desteği",
            comp_title: "Cihaz Uyumluluk Durumu",
            comp_desc: "AwayDoomscrollin' Android sistem yapısına bağlı çalıştığından cihaz üreticilerinin erişilebilirlik politikalarına göre farklılık gösterebilir.",
            comp_samsung_title: "Samsung Galaxy (One UI)",
            comp_samsung_sub: "Tam Doğrulanmış Cihaz Desteği",
            comp_samsung_desc: "Samsung Galaxy cihazlarında (One UI) %100 oranında test edilmiş ve tam stabilite doğrulanmıştır. Erişilebilirlik servisi ve kalkan mekanizması kesintisiz çalışır.",
            comp_other_title: "Diğer Android Cihazlar",
            comp_other_sub: "Xiaomi, Oppo, Vivo, Pixel, Huawei vb.",
            comp_other_desc: "Diğer üreticilerin özel Android sürümlerinde (MIUI, ColorOS, OriginOS) test süreçleri devam etmektedir. Kesin uyumluluk garantisi verilmemekte olup geri bildirimlerinizle geliştirilmektedir.",

            checker_title: "Cihazınızı Kontrol Edin",
            checker_subtitle: "Telefon markanızı seçin, özel pil optimizasyonu ve uyumluluk rehberini anında görün.",
            checker_label: "Telefon Markanız:",
            checker_guide_title: "Önerilen Kurulum Adımları:",

            faq_tag: "Aklınızdaki Sorular",
            faq_title: "Sıkça Sorulan Sorular",
            faq_desc: "AwayDoomscrollin' hakkında en çok merak edilen konuları sizin için derledik.",
            faq_q1: "Erişilebilirlik (Accessibility) izni güvenli mi? Verilerim çalınır mı?",
            faq_a1: "Gizlilik odaklıdır. Ekran içeriği analizi ve engelleme cihazda çalışır. Takma adlı telemetri varsayılan açıktır ve uygulamadan kapatılabilir; açıkken rastgele bir kurulum kimliğini, açıklanan cihaz/uygulama alanlarını ve toplu engelleme istatistiklerini awaydoomscrollin.com'a gönderir. Bu kimlik donanım, reklam veya hesap kimliğinden türetilmez. GitHub kural güncellemeleri telemetri anahtarından bağımsız ve otomatik çalışır. Erişilebilirlik izni hedef ekranları ve kaydırma hareketlerini algılamak için kullanılır.",
            faq_q2: "Instagram DM veya Yorum sayfalarında kaydırmak engellenir mi?",
            faq_a2: "Hayır! Akıllı Güvenli Bölge algoritmamız sayesinde mesajlaşma ekranları ve Yorum okuma penceresindeki kaydırmalar güvenli alan olarak algılanır. Engelleme yapılmadan yorumları rahatça okuyabilirsiniz.",
            faq_q3: "Uygulama arka planda pili/şarjı çok tüketir mi?",
            faq_a3: "Hayır. Uygulamamız sürekli çalışan arka plan döngüleri (polling) kullanmaz. Yalnızca siz Instagram Reels, TikTok veya YouTube Shorts'a girdiğinizde Android sistemi tarafından tetiklenen olay bazlı (event-driven) yapıda çalışır. Bu sayede şarj tüketimi sıfıra yakındır.",
            faq_q4: "AwayDoomscrollin' tamamen ücretsiz mi?",
            faq_a4: "Evet! AwayDoomscrollin' topluluk odaklı açık kaynak vizyonuyla geliştirilmiştir. Tüm kalkan modları, istatistikler ve Anti-Cheat özellikleri tamamen ücretsizdir.",
            faq_q5: "Samsung dışındaki telefonumda kalkan çalışmazsa ne yapmalıyım?",
            faq_a5: "Xiaomi, Vivo veya Oppo gibi agresif pil tasarrufu uygulayan modellerde Ayarlar > Otomatik Başlatma izinlerini AwayDoomscrollin' için açmanız gerekebilir. Herhangi bir uyumsuzlukta 'Geri Bildirim' formundan bize cihaz modelinizi iletebilirsiniz.",

            fb_tag: "Topluluk & Destek",
            fb_title: "Öneri veya Hata Bildirimi",
            fb_desc: "Bir hatayla mı karşılaştınız yoksa uygulamayı daha iyi yapacak bir fikriniz mi var? Bizimle paylaşın!",
            fb_email_label: "E-posta Adresiniz (Zorunlu)",
            fb_email_placeholder: "ornek@gmail.com",
            fb_device_placeholder: "Örn: Samsung A52, Xiaomi Redmi Note 12",
            fb_type_label: "Bildirim Türü",
            fb_type_opt_bug: "🐛 Hata Bildirimi (Bug Report)",
            fb_type_opt_feat: "💡 Yeni Özellik Önerisi",
            fb_type_opt_compat: "📱 Cihaz / Model Uyumluluk Raporu",
            fb_type_opt_other: "💬 Diğer / Genel Mesaj",
            fb_msg_label: "Mesajınız / Karşılaştığınız Durum",
            fb_msg_placeholder: "Karşılaştığınız hatayı, cihaz modelinizi veya önerinizi kısaca açıklayın...",
            fb_captcha_notice: "Cloudflare & FormSubmit CAPTCHA Anti-Spam Korumalı",
            fb_btn_submit: "Bildirimi Gönder",

            cta_title: "Bilinçsiz Kaydırmayı Bugün Sonlandırın",
            cta_desc: "Günde ortalama 2.5 saatinizi geri kazanın. AwayDoomscrollin' Android uygulamasını hemen indirin.",
            cta_btn: "AwayDoomscrollin' v1.0 APK İndir",
            footer_rights: "© 2026 AwayDoomscrollin'. Tüm hakları saklıdır.",
            footer_privacy: "Gizlilik Politikası",
            footer_terms: "Kullanım Koşulları",

            modal_tab_privacy: "Gizlilik Politikası",
            modal_tab_terms: "Kullanım Koşulları",
            legal_privacy_title: "Gizlilik Politikası",
            legal_terms_title: "Kullanım Koşulları",
            legal_last_updated: "Son Güncelleme: 17 Ağustos 2026",

            legal_p1_title: "1. Giriş ve Şeffaflık Taahhüdü",
            legal_p1_text: "AwayDoomscrollin' olarak dijital refah araçlarının davranışlarını açıkça anlatması gerektiğine inanıyoruz. Bu Gizlilik Politikası erişilebilirlik verilerini, yerel depolamayı, varsayılan açık takma adlı telemetriyi ve otomatik GitHub kural güncellemelerini açıklar. Kişisel bilgileri satmayız ve üçüncü taraf reklam analitiği kullanmayız.",
            legal_p2_title: "2. Ağ Üzerinden Veri İşleme",
            legal_p2_text: "Ekran içeriği analizi ve engelleme cihaz üzerinde çalışır. Takma adlı telemetri varsayılan açıktır ve uygulamadan kapatılabilir. Açıkken rastgele bir kurulum kimliği, cihaz üreticisi/modeli, Android ve SDK sürümü, uygulama sürümü, platform/toplam engelleme sayıları, seri günleri ve XP awaydoomscrollin.com'a gönderilir. Kurulum kimliği yerel olarak üretilir, donanım, reklam veya hesap kimliğinden türetilmez ve uygulamanın verileri temizlendiğinde ya da uygulama kaldırıldığında silinir. Uygulama ayrıca uygulama veya erişilebilirlik servisi başladığında kural/yapılandırma güncellemeleri için GitHub'a otomatik bağlanır; başarılı indirmeler altı saat önbelleğe alınır ve telemetri anahtarından bağımsızdır. Ekran içeriği, mesajlar, şifreler ve konum aktarılmaz.",
            legal_p3_title: "3. Erişilebilirlik Servisi (AccessibilityService) İzni ve Kapsamı",
            legal_p3_text1: "Hedef kısa video platformlarındaki (Instagram Reels, TikTok ve YouTube Shorts) sonsuz kaydırma davranışını algılamak ve engelleme eylemini gerçekleştirmek için AwayDoomscrollin', Android AccessibilityService API'sini kullanır.",
            legal_p3_text2: "Servis desteklenen Instagram, TikTok ve YouTube paketlerinde kaydırma, pencere durumu, pencere içeriği ve tıklama erişilebilirlik olaylarını alır. Hedef akışları güvenli alanlardan ayırmak için erişilebilirlik düğümü metnini, içerik açıklamalarını ve görünüm kimliklerini bellekte inceler. Hedef akış algılandığında cihazı ana ekrana döndürür ve hedef uygulamayı zorla durdurmak için Android Ayarları'nı açabilir.",
            legal_p3_text3: "Erişilebilirlik içeriği bellekte değerlendirilir; saklanmaz veya aktarılmaz. Ekran metni, mesajlar, yorumlar, şifreler, tuş vuruşları, kredi kartı bilgileri, kişiler, fotoğraflar ve tarama geçmişi telemetri ya da uzaktan kural isteklerine dahil edilmez.",
            legal_p4_title: "4. Yerel Veri Depolama ve Tercihler",
            legal_p4_text: "Engelleme istatistikleri, seri sayaçları, hedef uygulama anahtarları, son kalkan etkinliği ve rastgele kurulum kimliği SharedPreferences ('away_doomscroll_prefs') ile yerel olarak saklanır; uygulamanın verileri temizlendiğinde ya da Uygulama kaldırıldığında silinir. Telemetri açıkken kurulum kimliği ile açıklanan toplu engelleme, seri ve XP alanları ayrıca awaydoomscrollin.com'a gönderilir.",
            legal_p5_title: "5. Üçüncü Taraf Kütüphaneler ve Reklamlar",
            legal_p5_text: "Uygulama sıfır üçüncü taraf reklam SDK'sı, sıfır takip pikseli ve sıfır ticari analitik aracı içerir. AwayDoomscrollin' içerisine entegre edilmiş hiçbir sponsorlu reklam ağı bulunmamaktadır.",
            legal_p6_title: "6. Kullanıcı Kontrolü ve İzin İptali",
            legal_p6_text: "Uygulama üzerindeki kontrol %100 sizdedir. Uygulama arayüzünden her uygulama için koruma kalkanını istediğiniz zaman kapatıp açabilir veya Android Ayarları > Erişilebilirlik > AwayDoomscrollin' altından izinleri dilediğiniz an iptal edebilirsiniz.",

            legal_t1_title: "1. Koşulların Kabulü",
            legal_t1_text: "AwayDoomscrollin' uygulamasını indirerek, yükleyerek veya kullanarak bu Kullanım Koşullarına bağlı kalmayı kabul etmiş olursunuz. Bu koşulları kabul etmiyorsanız lütfen Uygulamayı yüklemeyiniz.",
            legal_t2_title: "2. Uygulamanın Amacı",
            legal_t2_text: "AwayDoomscrollin', kullanıcıların Android cihazlarda dürtüsel kısa video kaydırma alışkanlıklarını kırmalarına yardımcı olmak için tasarlanmış bir öz denetim ve dijital refah aracıdır. Uygulama, belirtilen hedef uygulamalarda kaydırma hareketlerini tespit edip kullanıcıyı ana ekrana yönlendirerek kişisel bir kararlılık aracı olarak işlev görür.",
            legal_t3_title: "3. Kullanıcı Sorumlulukları ve Cihaz Yapılandırması",
            legal_t3_text: "Kullanıcılar, Uygulamanın etkili çalışması için gerekli Android sistem izinlerini (Erişilebilirlik Servisi, Pil Optimizasyonu muafiyeti) doğru şekilde yapılandırmaktan sorumludur. Sistem izinlerinin kapatılmasının kalkan işlevselliğini durduracağını kullanıcılar kabul eder.",
            legal_t4_title: "4. Uyumluluk ve Üretici Farklılıkları",
            legal_t4_text: "AwayDoomscrollin' Samsung Galaxy cihazları (One UI) için kapsamlı şekilde test edilmiş olsa da, Android işletim sistemi davranışları cihaz üreticilerine göre (ör. Xiaomi MIUI, Oppo ColorOS, Vivo, Pixel) farklılık gösterebilir. Agresif arka plan işlemi sonlandırıcılarına sahip özel üretici yazılımlarında kesintisiz çalışma garantisi verilmez.",
            legal_t5_title: "5. Sorumluluk Reddi ve Sorumluluğun Sınırlandırılması",
            legal_t5_text: "AwayDoomscrollin' 'OLDUĞU GİBİ' ve 'MEVCUT OLDUĞU KADARIYLA' sunulmaktadır. Geliştiriciler, Uygulamanın kullanımından veya kullanılamamasından ya da hedef uygulamaların zorla durdurulmasından kaynaklanan doğrudan veya dolaylı hiçbir zarardan sorumlu tutulamaz.",
            legal_t6_title: "6. Açık Kaynak ve Lisans",
            legal_t6_text: "AwayDoomscrollin' açık, topluluk öncelikli bir vizyonla geliştirilmiştir. Tüm marka, logo ve yazılım bileşenleri açık kaynaklı yazılım ilkeleri ve geçerli telif hakkı yasaları kapsamında korunmaktadır."
        }
    };

    // Detect initial language (Default TR)
    function detectInitialLang() {
        const saved = localStorage.getItem('away_doomscroll_lang');
        if (saved === 'tr' || saved === 'en') return saved;

        const browserLangs = navigator.languages || [navigator.language || navigator.userLanguage || 'tr'];
        const primary = (browserLangs[0] || 'tr').toLowerCase();

        return primary.startsWith('tr') ? 'tr' : (primary.startsWith('en') ? 'en' : 'tr');
    }

    let currentLang = detectInitialLang();

    window.setLanguage = function(lang) {
        currentLang = lang;
        window.currentLang = lang;
        localStorage.setItem('away_doomscroll_lang', lang);
        document.documentElement.lang = lang;

        // Update active class on language toggle buttons
        const btnTr = document.getElementById('langBtnTr');
        const btnEn = document.getElementById('langBtnEn');
        if (btnTr && btnEn) {
            btnTr.classList.toggle('active', lang === 'tr');
            btnEn.classList.toggle('active', lang === 'en');
        }

        const langDict = i18n[lang] || i18n.tr;

        // Update text content for elements with data-i18n
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (langDict[key]) {
                el.textContent = langDict[key];
            }
        });

        // Update placeholders
        document.querySelectorAll('[data-i18n-ph]').forEach(el => {
            const key = el.getAttribute('data-i18n-ph');
            if (langDict[key]) {
                el.placeholder = langDict[key];
            }
        });

        // Trigger calculator update to reflect new language units (e.g. Days vs Gün)
        if (window.updateCalculatorDisplay) {
            window.updateCalculatorDisplay();
        }

        // Reset typing animation on language change
        if (typeof resetTypingAnimation === 'function') {
            resetTypingAnimation();
        }

        // Update feedback template if active
        const feedbackCategorySelect = document.getElementById('fbCategory');
        const feedbackMessageInput = document.getElementById('fbMessage');
        if (feedbackCategorySelect && feedbackMessageInput && typeof feedbackTemplates !== 'undefined') {
            const cat = feedbackCategorySelect.value;
            const t = feedbackTemplates[lang] && feedbackTemplates[lang][cat];
            if (t) {
                if (!feedbackMessageInput.value || feedbackMessageInput.value.includes("📌") || feedbackMessageInput.value.includes("🌟") || feedbackMessageInput.value.includes("⚠️") || feedbackMessageInput.value.includes("💬")) {
                    feedbackMessageInput.value = t;
                }
            }
        }

        if (typeof window.updateFeedbackDeviceRequirement === 'function') {
            window.updateFeedbackDeviceRequirement();
        }
    };

    // Attach language switcher click listeners
    const btnTr = document.getElementById('langBtnTr');
    const btnEn = document.getElementById('langBtnEn');

    if (btnTr) btnTr.addEventListener('click', () => setLanguage('tr'));
    if (btnEn) btnEn.addEventListener('click', () => setLanguage('en'));

    // --------------------------------------------------------------------------
    // 2. Endless Typing & Erasing Loop Animation for "bırak." / "now."
    // --------------------------------------------------------------------------
    const loopTarget = document.getElementById('loopWordTarget');
    let charIdx = 0;
    let isDeleting = false;
    let typingTimer = null;
    const typeSpeed = 130;
    const deleteSpeed = 90;
    const pauseEndDelay = 1800;
    const pauseStartDelay = 400;

    function resetTypingAnimation() {
        if (typingTimer) clearTimeout(typingTimer);
        charIdx = 0;
        isDeleting = false;
        if (loopTarget) loopTarget.textContent = "";
        loopTypingAnimation();
    }

    function loopTypingAnimation() {
        if (!loopTarget) return;

        const targetWord = currentLang === 'tr' ? "bırak." : "now.";

        if (!isDeleting) {
            loopTarget.textContent = targetWord.substring(0, charIdx + 1);
            charIdx++;

            if (charIdx === targetWord.length) {
                isDeleting = true;
                typingTimer = setTimeout(loopTypingAnimation, pauseEndDelay);
                return;
            }
        } else {
            loopTarget.textContent = targetWord.substring(0, charIdx - 1);
            charIdx--;

            if (charIdx === 0) {
                isDeleting = false;
                typingTimer = setTimeout(loopTypingAnimation, pauseStartDelay);
                return;
            }
        }

        typingTimer = setTimeout(loopTypingAnimation, isDeleting ? deleteSpeed : typeSpeed);
    }

    // --------------------------------------------------------------------------
    // 3. Mobile Menu Toggle
    // --------------------------------------------------------------------------
    const mobileToggle = document.getElementById('mobileMenuToggle');
    const navMenu = document.getElementById('navMenu');

    if (mobileToggle && navMenu) {
        mobileToggle.addEventListener('click', () => {
            navMenu.classList.toggle('active');
            mobileToggle.setAttribute('aria-expanded', navMenu.classList.contains('active'));
        });

        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                navMenu.classList.remove('active');
            });
        });
    }

    // --------------------------------------------------------------------------
    // 4. Interactive Reels Video Simulator
    // --------------------------------------------------------------------------
    const reelsFeed = document.getElementById('reelsFeed');
    const shieldOverlay = document.getElementById('shieldBlockOverlay');
    const btnReplay = document.getElementById('btnReplayDemo');
    const videoCounterBadge = document.getElementById('videoCounterBadge');
    const demoVideos = document.querySelectorAll('.reels-video-element');
    const totalVideos = demoVideos.length;

    let currentVideoIndex = 0;
    let hasBlocked = false;
    let isProgrammaticScroll = false;
    let blockCount = 0;

    let isMouseDown = false;
    let startY = 0;
    let scrollTopStart = 0;

    function updateVideoState(index) {
        demoVideos.forEach((vid, i) => {
            if (i === index) {
                vid.currentTime = 0;
                const playPromise = vid.play();
                if (playPromise !== undefined) {
                    playPromise.catch(() => {
                        // Playback prevented, un-mute or play on click
                    });
                }
            } else {
                vid.pause();
            }
        });

        if (videoCounterBadge) {
            videoCounterBadge.textContent = `Video ${index + 1} / ${totalVideos}`;
        }
    }

    if (totalVideos > 0) {
        updateVideoState(0);
    }

    // Fallback: Ensure videos play on first user interaction with document
    document.addEventListener('click', () => {
        if (demoVideos[currentVideoIndex] && demoVideos[currentVideoIndex].paused) {
            demoVideos[currentVideoIndex].play().catch(() => {});
        }
    }, { once: true });

    function handleUserScrollAttempt() {
        if (hasBlocked || isProgrammaticScroll) return;

        hasBlocked = true;
        blockCount++;

        currentVideoIndex = (currentVideoIndex + 1) % totalVideos;

        const containerHeight = reelsFeed ? reelsFeed.clientHeight : 580;
        if (reelsFeed) {
            reelsFeed.scrollTo({
                top: currentVideoIndex * containerHeight,
                behavior: 'smooth'
            });
        }

        updateVideoState(currentVideoIndex);

        setTimeout(() => {
            if (shieldOverlay) {
                shieldOverlay.classList.add('active');
            }
        }, 300);
    }

    if (reelsFeed) {
        let scrollTimeout;

        reelsFeed.addEventListener('wheel', (e) => {
            if (hasBlocked || isProgrammaticScroll) return;

            if (Math.abs(e.deltaY) > 6) {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(() => {
                    handleUserScrollAttempt();
                }, 100);
            }
        }, { passive: true });

        reelsFeed.addEventListener('mousedown', (e) => {
            if (hasBlocked || isProgrammaticScroll) return;
            isMouseDown = true;
            startY = e.pageY - reelsFeed.offsetTop;
            scrollTopStart = reelsFeed.scrollTop;
            reelsFeed.style.cursor = 'grabbing';
        });

        reelsFeed.addEventListener('mouseleave', () => {
            isMouseDown = false;
            reelsFeed.style.cursor = 'grab';
        });

        reelsFeed.addEventListener('mouseup', () => {
            isMouseDown = false;
            reelsFeed.style.cursor = 'grab';
        });

        reelsFeed.addEventListener('mousemove', (e) => {
            if (!isMouseDown || hasBlocked || isProgrammaticScroll) return;
            e.preventDefault();
            const y = e.pageY - reelsFeed.offsetTop;
            const distance = startY - y;

            if (Math.abs(distance) > 12 && !hasBlocked) {
                isMouseDown = false;
                reelsFeed.style.cursor = 'grab';
                handleUserScrollAttempt();
            }
        });

        reelsFeed.addEventListener('touchstart', (e) => {
            if (hasBlocked || isProgrammaticScroll) return;
            startY = e.touches[0].pageY - reelsFeed.offsetTop;
            scrollTopStart = reelsFeed.scrollTop;
        }, { passive: true });

        reelsFeed.addEventListener('touchmove', (e) => {
            if (hasBlocked || isProgrammaticScroll) return;
            const y = e.touches[0].pageY - reelsFeed.offsetTop;
            const distance = startY - y;

            if (Math.abs(distance) > 15 && !hasBlocked) {
                handleUserScrollAttempt();
            }
        }, { passive: true });
    }

    if (btnReplay && reelsFeed && shieldOverlay) {
        btnReplay.addEventListener('click', () => {
            isProgrammaticScroll = true;
            hasBlocked = false;

            shieldOverlay.classList.remove('active');
            updateVideoState(currentVideoIndex);

            setTimeout(() => {
                isProgrammaticScroll = false;
            }, 400);
        });
    }

    // --------------------------------------------------------------------------
    // 5. Interactive Doomscroll Time Calculator
    // --------------------------------------------------------------------------
    const calcSlider = document.getElementById('calcSlider');
    const calcHoursText = document.getElementById('calcHoursText');
    const calcSummaryBanner = document.getElementById('calcSummaryBanner');
    const calcMonthlyTime = document.getElementById('calcMonthlyTime');
    const calcDaysSaved = document.getElementById('calcDaysSaved');
    const calcBooksSaved = document.getElementById('calcBooksSaved');
    const calcSkillsHours = document.getElementById('calcSkillsHours');
    const calcSummaryText = document.getElementById('calcSummaryText');

    // Dynamic slider color scheme based on hours (Green -> Yellow -> Orange -> Crimson Red)
    function getSliderColor(hours) {
        if (hours <= 1) {
            return '#00FF87'; // Safe Neon Green
        } else if (hours <= 2.5) {
            return '#FFD166'; // Moderate Gold / Yellow
        } else if (hours <= 4.5) {
            return '#FF9F1C'; // High Risk Orange
        } else {
            return '#FF0055'; // Critical Danger Neon Red
        }
    }

    window.updateCalculatorDisplay = function() {
        if (!calcSlider) return;
        const hoursPerDay = parseFloat(calcSlider.value);
        
        const isTr = currentLang === 'tr';

        let dailyLabel = "";
        if (hoursPerDay < 1) {
            const mins = Math.round(hoursPerDay * 60);
            dailyLabel = isTr ? `Günde ${mins} Dakika` : `${mins} Minutes Daily`;
        } else {
            dailyLabel = isTr ? `Günde ${hoursPerDay} Saat` : `${hoursPerDay} Hours Daily`;
        }

        // Dynamic Color Transition (Increases towards Crimson Red)
        const activeColor = getSliderColor(hoursPerDay);
        const minVal = parseFloat(calcSlider.min) || 0.25;
        const maxVal = parseFloat(calcSlider.max) || 10;
        const percentage = ((hoursPerDay - minVal) / (maxVal - minVal)) * 100;

        // Apply track gradient and text glow
        calcSlider.style.background = `linear-gradient(90deg, ${activeColor} 0%, ${activeColor} ${percentage}%, rgba(30, 42, 64, 0.9) ${percentage}%, rgba(30, 42, 64, 0.9) 100%)`;

        if (calcHoursText) {
            calcHoursText.textContent = dailyLabel;
            calcHoursText.style.color = activeColor;
            calcHoursText.style.textShadow = `0 0 25px ${activeColor}`;
        }

        if (calcSummaryBanner) {
            calcSummaryBanner.style.borderColor = activeColor;
            calcSummaryBanner.style.boxShadow = `0 0 25px ${activeColor}33`;
            const icon = calcSummaryBanner.querySelector('i');
            if (icon) {
                icon.style.color = activeColor;
            }
        }

        // Monthly Calculations (30 days)
        const monthlyHours = Math.round(hoursPerDay * 30);
        const monthlyDays = (monthlyHours / 24).toFixed(1);

        // Yearly Calculations (365 days)
        const yearlyHours = hoursPerDay * 365;
        const yearlyDays = Math.round(yearlyHours / 24);
        const yearlyBooks = Math.round(yearlyHours / 5);
        const yearlySkillHours = Math.round(yearlyHours);

        if (calcMonthlyTime) {
            calcMonthlyTime.textContent = isTr ? `${monthlyHours} Saat / Ay` : `${monthlyHours} Hours / Mo`;
        }
        if (calcDaysSaved) {
            calcDaysSaved.textContent = isTr ? `${yearlyDays} Gün / Yıl` : `${yearlyDays} Days / Yr`;
        }
        if (calcBooksSaved) {
            calcBooksSaved.textContent = isTr ? `${yearlyBooks} Kitap` : `${yearlyBooks} Books`;
        }
        if (calcSkillsHours) {
            calcSkillsHours.textContent = isTr ? `${yearlySkillHours} Saat` : `${yearlySkillHours} Hours`;
        }

        // Dynamic Summary Banner Text
        if (calcSummaryText) {
            const capDailyLabel = dailyLabel.charAt(0).toUpperCase() + dailyLabel.slice(1);
            if (isTr) {
                calcSummaryText.textContent = `${capDailyLabel} kaydırarak 1 ayda tam ${monthlyHours} saatini (${monthlyDays} gününü) ve 1 yılda ${yearlyDays} tam gününü kaybediyorsun.`;
            } else {
                calcSummaryText.textContent = `Scrolling ${dailyLabel.toLowerCase()}, you lose ${monthlyHours} hours (${monthlyDays} full days) every month and ${yearlyDays} full days every year.`;
            }
        }
    };

    if (calcSlider) {
        ['input', 'change', 'pointermove', 'touchmove'].forEach(evt => {
            calcSlider.addEventListener(evt, window.updateCalculatorDisplay);
        });
    }

    // Apply initial language and calculator update
    setLanguage(currentLang);
    resetTypingAnimation();

    // --------------------------------------------------------------------------
    // 6. FAQ Accordion Event Listener Fail-safe
    // --------------------------------------------------------------------------
    document.querySelectorAll('.faq-item').forEach(item => {
        item.addEventListener('click', function(e) {
            window.toggleFaq(this);
        });
    });

    // --------------------------------------------------------------------------
    // 7. Feedback & Bug Report Form Submission
    // --------------------------------------------------------------------------
    // 💡 YETKİLİLERE BİLDİRİM İLETME ALTYAPISI (FormSubmit AJAX API -> support@awaydoomscrollin.com)
    const FEEDBACK_ENDPOINT = "https://formsubmit.co/ajax/support@awaydoomscrollin.com";

    const feedbackForm = document.getElementById('feedbackForm');
    const feedbackToast = document.getElementById('feedbackToast');
    const feedbackCategorySelect = document.getElementById('fbCategory');
    const feedbackMessageInput = document.getElementById('fbMessage');

    // Kategorilere Göre Hazır Şablonlar (Templates)
    const feedbackTemplates = {
        tr: {
            hata: "📌 Yaşanan Sorun: [Sorunu kısaca açıklayın]\n📱 Cihaz Modeli / OS: [Örn: Samsung Galaxy S23 / Android 14]\n🔄 Hangi Ekranda Oluştu?: [Instagram Reels / TikTok / YouTube Shorts]\n💡 Beklenen Davranış: [Ne olması gerekiyordu?]",
            oneri: "🌟 Öneri Başlığı: [Önerinizi tanımlayın]\n🎯 Ne İşe Yarayacak?: [Uygulamayı nasıl iyileştirecek?]\n📋 Detaylı Açıklama: ",
            uyumluluk: "📱 Telefon Marka / Model: [Örn: Xiaomi Redmi Note 12 / MIUI 14]\n⚠️ Kalkan Durumu: [Kalkan çalışıyor mu / kilitleniyor mu?]\n🔋 Pil Tasarrufu İzinleri Verildi mi?: [Evet / Hayır]",
            diger: "💬 Konu: [Mesaj başlığınız]\n📝 Detaylar: "
        },
        en: {
            hata: "📌 Issue Summary: [Briefly describe the bug]\n📱 Device Model / Android OS: [e.g. Samsung Galaxy S23 / Android 14]\n🔄 Screen / App Affected: [Instagram Reels / TikTok / YouTube Shorts]\n💡 Expected Behavior: [What should have happened?]",
            oneri: "🌟 Feature Title: [Describe your idea]\n🎯 Benefit: [How will it improve the app?]\n📋 Details: ",
            uyumluluk: "📱 Phone Brand / Model: [e.g. Xiaomi Redmi Note 12 / MIUI 14]\n⚠️ Shield Behavior: [Working / Freezing / Stopped?]\n🔋 Battery Optimization Exempted?: [Yes / No]",
            diger: "💬 Subject: [Your message subject]\n📝 Message Details: "
        }
    };

    // Kategori değiştiğinde hazır şablonu otomatik doldur ve cihaz zorunluluğunu ayarla
    if (feedbackCategorySelect && feedbackMessageInput) {
        feedbackCategorySelect.addEventListener('change', () => {
            const cat = feedbackCategorySelect.value;
            const lang = currentLang === 'tr' ? 'tr' : 'en';
            if (feedbackTemplates[lang] && feedbackTemplates[lang][cat]) {
                feedbackMessageInput.value = feedbackTemplates[lang][cat];
            }
            if (window.updateFeedbackDeviceRequirement) window.updateFeedbackDeviceRequirement();
        });
        if (window.updateFeedbackDeviceRequirement) window.updateFeedbackDeviceRequirement();
    }

    if (feedbackForm) {
        feedbackForm.addEventListener('submit', async (e) => {
            e.preventDefault();

            const emailInput = document.getElementById('fbEmail');
            const deviceInput = document.getElementById('fbDevice');
            const email = emailInput ? emailInput.value.trim() : '';
            const device = deviceInput ? deviceInput.value.trim() : '';
            const category = document.getElementById('fbCategory').value;
            const message = document.getElementById('fbMessage').value.trim();
            const isTr = currentLang === 'tr';

            if (!email || !email.includes('@')) {
                alert(isTr ? 'Lütfen geçerli bir e-posta adresi giriniz. Geri dönüş yapabilmemiz için e-posta zorunludur.' : 'Please enter a valid email address. Required for us to reply.');
                return;
            }

            if ((category === 'hata' || category === 'uyumluluk') && !device) {
                alert(isTr ? 'Lütfen hata veya uyumluluk bildirimi için telefon modelinizi belirtiniz (Örn: Samsung A52, Xiaomi Redmi Note 12).' : 'Please specify your phone model for bug or compatibility reports (e.g. Samsung A52, Xiaomi Redmi Note 12).');
                if (deviceInput) deviceInput.focus();
                return;
            }

            if (!message) {
                alert(isTr ? 'Lütfen mesaj alanını doldurunuz.' : 'Please fill in the message field.');
                return;
            }

            // support@awaydoomscrollin.com adresine e-posta gönder (FormSubmit AJAX API with Anti-Spam Captcha)
            if (FEEDBACK_ENDPOINT) {
                try {
                    await fetch(FEEDBACK_ENDPOINT, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Accept': 'application/json'
                        },
                        body: JSON.stringify({
                            _subject: `[AwayDoomscrollin' Web] New ${category.toUpperCase()} Feedback from ${email}`,
                            _replyto: email,
                            _captcha: "true",
                            _template: "table",
                            Email: email,
                            DeviceModel: device || 'N/A',
                            Category: category,
                            Message: message,
                            Date: new Date().toLocaleString()
                        })
                    });
                } catch (err) {
                    console.warn("Feedback iletim hatası:", err);
                }
            }

            if (feedbackToast) {
                feedbackToast.style.display = 'block';
                if (isTr) {
                    feedbackToast.textContent = `Teşekkürler! ${category === 'hata' ? 'Hata bildiriminiz' : 'Öneriniz'} başarıyla iletildi. Ekibimiz en kısa sürede ${email} adresiniz üzerinden size dönüş yapacaktır.`;
                } else {
                    feedbackToast.textContent = `Thank you! Your ${category === 'hata' ? 'bug report' : 'feedback'} was submitted successfully. We will reply to ${email} shortly.`;
                }
            }

            feedbackForm.reset();
            updateFeedbackDeviceRequirement();

            setTimeout(() => {
                if (feedbackToast) feedbackToast.style.display = 'none';
            }, 6000);
        });
    }

    // --------------------------------------------------------------------------
    // 8. Privacy Policy & Terms of Service Modal Controller
    // --------------------------------------------------------------------------
    const legalOverlay = document.getElementById('legalModalOverlay');
    const openPrivacyBtn = document.getElementById('openPrivacyBtn');
    const openTermsBtn = document.getElementById('openTermsBtn');
    const closeLegalModal = document.getElementById('closeLegalModal');

    const tabPrivacyBtn = document.getElementById('tabPrivacyBtn');
    const tabTermsBtn = document.getElementById('tabTermsBtn');
    const privacyContent = document.getElementById('privacyContent');
    const termsContent = document.getElementById('termsContent');

    function switchLegalTab(tab) {
        if (tab === 'privacy') {
            tabPrivacyBtn.classList.add('active');
            tabTermsBtn.classList.remove('active');
            privacyContent.classList.add('active');
            termsContent.classList.remove('active');
        } else {
            tabTermsBtn.classList.add('active');
            tabPrivacyBtn.classList.remove('active');
            termsContent.classList.add('active');
            privacyContent.classList.remove('active');
        }
    }

    function openLegalModal(tab) {
        switchLegalTab(tab);
        if (legalOverlay) {
            legalOverlay.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }

    function closeModal() {
        if (legalOverlay) {
            legalOverlay.classList.remove('active');
            document.body.style.overflow = '';
        }
    }

    if (openPrivacyBtn) {
        openPrivacyBtn.addEventListener('click', (e) => {
            e.preventDefault();
            openLegalModal('privacy');
        });
    }

    if (openTermsBtn) {
        openTermsBtn.addEventListener('click', (e) => {
            e.preventDefault();
            openLegalModal('terms');
        });
    }

    if (tabPrivacyBtn) {
        tabPrivacyBtn.addEventListener('click', () => switchLegalTab('privacy'));
    }

    if (tabTermsBtn) {
        tabTermsBtn.addEventListener('click', () => switchLegalTab('terms'));
    }

    if (closeLegalModal) {
        closeLegalModal.addEventListener('click', closeModal);
    }

    if (legalOverlay) {
        legalOverlay.addEventListener('click', (e) => {
            if (e.target === legalOverlay) {
                closeModal();
            }
        });
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && legalOverlay && legalOverlay.classList.contains('active')) {
            closeModal();
        }
    });
});


