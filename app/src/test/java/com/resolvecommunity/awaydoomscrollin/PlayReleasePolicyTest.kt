package com.resolvecommunity.awaydoomscrollin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReleasePolicyTest {
    private data class PngHeader(val width: Int, val height: Int, val colorType: Int)

    private fun repoFile(relativePath: String): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        var current: File? = File(userDirectory).absoluteFile
        repeat(4) {
            val directory = current ?: error("Repository root could not be resolved")
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            current = directory.parentFile
        }
        error("Repository file not found: $relativePath")
    }

    private fun pngHeader(file: File): PngHeader {
        val bytes = file.readBytes()
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        require(bytes.size >= 26 && bytes.copyOfRange(0, 8).contentEquals(signature)) {
            "Not a valid PNG: ${file.path}"
        }
        fun intAt(offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        return PngHeader(
            width = intAt(16),
            height = intAt(20),
            colorType = bytes[25].toInt() and 0xff
        )
    }

    @Test
    fun `localized Play titles and short descriptions fit Console limits`() {
        listOf("en-US", "tr-TR").forEach { locale ->
            val title = repoFile("fastlane/metadata/android/$locale/title.txt").readText().trim()
            val shortDescription = repoFile("fastlane/metadata/android/$locale/short_description.txt").readText().trim()
            val releaseNotes = repoFile("fastlane/metadata/android/$locale/changelogs/7.txt").readText().trim()
            assertTrue("$locale title is longer than 30 characters", title.length <= 30)
            assertTrue("$locale short description is longer than 80 characters", shortDescription.length <= 80)
            assertTrue("$locale release notes are longer than 500 characters", releaseNotes.length <= 500)
        }
    }

    @Test
    fun `store listings disclose third-party independence`() {
        listOf("en-US", "tr-TR").forEach { locale ->
            val listing = repoFile("fastlane/metadata/android/$locale/full_description.txt").readText()
            assertTrue("$locale listing is missing the independence disclosure", listing.contains("independent", ignoreCase = true) || listing.contains("bağımsız", ignoreCase = true))
        }
    }

    @Test
    fun `Play icon and feature graphic meet required dimensions`() {
        listOf("en-US", "tr-TR").forEach { locale ->
            val iconFile = repoFile("fastlane/metadata/android/$locale/images/icon.png")
            val featureFile = repoFile("fastlane/metadata/android/$locale/images/featureGraphic.png")
            val icon = pngHeader(iconFile)
            val feature = pngHeader(featureFile)

            assertEquals("$locale icon width", 512, icon.width)
            assertEquals("$locale icon height", 512, icon.height)
            assertTrue("$locale icon is larger than 1 MB", iconFile.length() <= 1_048_576L)
            assertEquals("$locale feature graphic width", 1024, feature.width)
            assertEquals("$locale feature graphic height", 500, feature.height)
            assertTrue("$locale icon must have alpha", icon.colorType == 4 || icon.colorType == 6)
            assertFalse("$locale feature graphic must not have alpha", feature.colorType == 4 || feature.colorType == 6)
        }
    }

    @Test
    fun `accessibility disclosure requires affirmative consent and offers a decline path`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()
        val consentSource = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/AccessibilityConsent.kt"
        ).readText()
        val serviceSource = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/AntiScrollService.kt"
        ).readText()

        assertTrue(
            source.contains("AccessibilityDisclosureCard(isEn = isEn)")
        )
        assertTrue(source.contains("Kabul et ve ayarları aç"))
        assertTrue(source.contains("Şimdi değil — koruma kapalı kalsın"))
        assertTrue(source.contains("AccessibilityConsentDialog("))
        assertTrue(source.contains("onActivateClick = onRequestAccessibility"))
        assertTrue(source.contains("onClick = onRequestAccessibility"))
        assertTrue(consentSource.contains("CURRENT_DISCLOSURE_VERSION"))
        assertTrue(serviceSource.contains("!AccessibilityConsent.isAccepted(this)"))
    }

    @Test
    fun `optional telemetry is enabled only after the detailed consent dialog`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()

        assertTrue(source.contains("TelemetryConsentDialog("))
        assertTrue(source.contains("Paylaşımı aç"))
        assertTrue(source.contains("Vazgeç"))
        assertTrue(source.contains("showTelemetryConsent = true"))
        assertTrue(source.contains("TelemetryManager.setTelemetryEnabled(context, true)"))
    }

    @Test
    fun `service declares that it is not a disability accessibility tool`() {
        val serviceConfig = repoFile(
            "app/src/main/res/xml/accessibility_service_config.xml"
        ).readText()
        assertTrue(serviceConfig.contains("android:isAccessibilityTool=\"false\""))
    }

    @Test
    fun `release packaging refuses to create unsigned artifacts`() {
        val buildScript = repoFile("app/build.gradle.kts").readText()
        assertTrue(buildScript.contains("verifyReleaseSigningInputs"))
        assertTrue(buildScript.contains("hasPlayUploadSigning || hasGithubSigning"))
        assertTrue(buildScript.contains("packageReleaseBundle"))
    }

    @Test
    fun `Compose never loads the adaptive launcher icon as a painter`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()
        assertFalse(source.contains("painterResource(id = R.mipmap.ic_launcher)"))
    }

    @Test
    fun `adaptive launcher icon uses the full AwayDoomscrollin brand mark`() {
        val foreground = repoFile(
            "app/src/main/res/drawable/ic_launcher_foreground.xml"
        ).readText()
        val monochrome = repoFile(
            "app/src/main/res/drawable/ic_launcher_monochrome.xml"
        ).readText()
        val adaptiveIcons = listOf(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
        )

        assertTrue(foreground.contains("@drawable/ic_splash_logo"))
        assertTrue(monochrome.contains("Phone frame"))
        assertFalse(monochrome.contains("M54,21L36,29"))
        adaptiveIcons.forEach { path ->
            assertTrue(repoFile(path).readText().contains("@drawable/ic_launcher_monochrome"))
        }
    }

    @Test
    fun `Turkish interface avoids internal telemetry and mode jargon`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()
        listOf(
            "takma adlı",
            "İsteğe bağlı telemetri",
            "TELEMETRİ (İSTEĞE BAĞLI)",
            "text = \"Full Mode\"",
            "text = \"Directly DM\"",
            "Bugün \$appCount. kez"
        ).forEach { unwanted ->
            assertFalse("User-facing jargon returned: $unwanted", source.contains(unwanted))
        }
        assertTrue(source.contains("İSTEĞE BAĞLI KULLANIM VERİLERİ"))
        assertTrue(source.contains("if (isEn) \"PERMISSION REQUIRED\" else \"İZİN GEREKLİ\""))
    }

    @Test
    fun `About screen presents the license only once`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()
        val aboutSource = source.substringAfter("fun AboutScreen(")
            .substringBefore("fun MainNavigationDashboard(")

        assertEquals(1, Regex("GPL", RegexOption.IGNORE_CASE).findAll(aboutSource).count())
        assertFalse(aboutSource.contains("Background Watermark Logo"))
    }

    @Test
    fun `dashboard keeps Instagram-only measurements distinct from app totals`() {
        val source = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()

        assertFalse(source.contains("Instagram akışı ve Reels"))
        assertTrue(source.contains("Bugün \$todayInterventions engelleme"))
        assertTrue(source.contains("todayInstagramBlocks"))
        assertTrue(source.contains("todayTiktokBlocks"))
        assertTrue(source.contains("todayYoutubeBlocks"))
        assertTrue(source.contains("Instagram koruma süresi"))
        assertTrue(source.contains("Haftalık grafik nasıl çalışır?"))
        assertTrue(source.contains("Başarımlar ve seri"))
    }

    @Test
    fun `notification and selected-app copy describe the real protection state`() {
        val mainSource = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/MainActivity.kt"
        ).readText()
        val notificationSource = repoFile(
            "app/src/main/java/com/resolvecommunity/awaydoomscrollin/NotificationHelper.kt"
        ).readText()

        assertTrue(mainSource.contains("Koruma durumu ve izin hatırlatmaları alın."))
        assertTrue(mainSource.contains("else -> if (isEn) \"On\" else \"Açık\""))
        assertFalse(mainSource.contains("else -> if (isEn) \"Selected\" else \"Seçili\""))
        assertTrue(notificationSource.contains("Koruma ve izin durumu"))
        assertFalse(notificationSource.contains("showMilestoneNotification"))
    }
}
