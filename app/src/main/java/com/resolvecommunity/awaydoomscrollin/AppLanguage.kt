package com.resolvecommunity.awaydoomscrollin

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** The app's interface languages, each named in its own language for the picker. */
enum class AppLanguage(val code: String, val nativeName: String) {
    TR("tr", "Türkçe"),
    EN("en", "English"),
    FR("fr", "Français"),
    IT("it", "Italiano"),
    ES("es", "Español"),
    RU("ru", "Русский"),
    KO("ko", "한국어");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** The phone's language if the app has it, English otherwise. */
        fun forDevice(deviceLanguage: String): AppLanguage =
            fromCode(deviceLanguage.lowercase(Locale.ROOT).take(2)) ?: EN

        /**
         * Which language to use, from what is saved.
         *
         * Before French, Italian, Spanish, Russian and Korean existed, the app saved
         * "en" for every phone that was not Turkish, without the user choosing
         * it. So a saved "en" that the user did not pick, on a phone in one of
         * the new languages, moves to that language once. A language the user
         * picked is never changed.
         */
        fun resolve(
            saved: String?,
            chosenByUser: Boolean,
            alreadyMigrated: Boolean,
            deviceLanguage: String
        ): AppLanguage {
            val device = forDevice(deviceLanguage)
            val current = fromCode(saved) ?: return device
            if (current == EN && !chosenByUser && !alreadyMigrated && device != TR) return device
            return current
        }
    }
}

// SharedPreferences keys (ProtectionPreferences.PREFS_NAME):
// "app_language": the language code in use (AppLanguage.code).
// "app_language_chosen": true once the user picked a language themselves.
// "app_language_migrated_v2": the one-time move to a new language has been checked.
internal const val KEY_APP_LANGUAGE = "app_language"
internal const val KEY_APP_LANGUAGE_CHOSEN = "app_language_chosen"
internal const val KEY_APP_LANGUAGE_MIGRATED = "app_language_migrated_v2"

/** The language in use, resolved and saved on first use (see [AppLanguage.resolve]). */
fun appLanguage(prefs: SharedPreferences): AppLanguage {
    val saved = prefs.getString(KEY_APP_LANGUAGE, null)
    val migrated = prefs.getBoolean(KEY_APP_LANGUAGE_MIGRATED, false)
    val resolved = AppLanguage.resolve(
        saved = saved,
        chosenByUser = prefs.getBoolean(KEY_APP_LANGUAGE_CHOSEN, false),
        alreadyMigrated = migrated,
        deviceLanguage = Locale.getDefault().language
    )
    if (resolved.code != saved || !migrated) {
        prefs.edit()
            .putString(KEY_APP_LANGUAGE, resolved.code)
            .putBoolean(KEY_APP_LANGUAGE_MIGRATED, true)
            .apply()
    }
    return resolved
}

/** The user picked [language] in the app. */
fun setAppLanguage(prefs: SharedPreferences, language: AppLanguage) {
    prefs.edit()
        .putString(KEY_APP_LANGUAGE, language.code)
        .putBoolean(KEY_APP_LANGUAGE_CHOSEN, true)
        .putBoolean(KEY_APP_LANGUAGE_MIGRATED, true)
        .apply()
}

/**
 * The interface language code, provided at the root of the UI. Static on
 * purpose: a change redraws everything below it, including parts whose
 * parameters (such as `isEn`) did not change between two languages.
 */
val LocalAppLanguage = androidx.compose.runtime.staticCompositionLocalOf { "en" }

/**
 * Text in the app's own language, which can differ from the phone's. Used by
 * the screens, the curtains and the notifications alike. A string missing in
 * a language falls back to English (res/values).
 */
object AppText {
    @Volatile private var appContext: Context? = null
    private val resources = HashMap<AppLanguage, Resources>()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun language(): AppLanguage {
        val context = appContext ?: return AppLanguage.EN
        return appLanguage(context.getSharedPreferences(ProtectionPreferences.PREFS_NAME, Context.MODE_PRIVATE))
    }

    private fun resourcesFor(language: AppLanguage): Resources? {
        val context = appContext ?: return null
        synchronized(resources) {
            return resources.getOrPut(language) {
                val config = Configuration(context.resources.configuration)
                config.setLocale(Locale.forLanguageTag(language.code))
                context.createConfigurationContext(config).resources
            }
        }
    }

    fun get(id: Int, vararg args: Any): String = getIn(language(), id, *args)

    fun plural(id: Int, count: Int, vararg args: Any): String = pluralIn(language(), id, count, *args)

    /** A text in a given language (tests, and helpers that take the language). */
    fun getIn(language: AppLanguage, id: Int, vararg args: Any): String {
        val res = resourcesFor(language) ?: return ""
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    fun pluralIn(language: AppLanguage, id: Int, count: Int, vararg args: Any): String {
        val res = resourcesFor(language) ?: return ""
        return res.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))
    }

    /** The locale of [language], for platform names of days and months. */
    fun locale(language: AppLanguage): Locale = Locale.forLanguageTag(language.code)
}
