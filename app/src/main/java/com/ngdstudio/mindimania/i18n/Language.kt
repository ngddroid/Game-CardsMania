package com.ngdstudio.mindimania.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** The languages the app is translated into. [tag] is the Android resource qualifier (values-hi, values-gu). */
enum class AppLanguage(val tag: String, val nativeName: String, val englishName: String, val glyph: String) {
    EN("en", "English", "English", "A"),
    HI("hi", "हिन्दी", "Hindi", "अ"),
    GU("gu", "ગુજરાતી", "Gujarati", "અ");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Stores the chosen language and applies it. The choice lives in plain SharedPreferences (not DataStore) because it
 * must be readable synchronously in attachBaseContext, before anything else runs.
 *
 * How the language reaches the UI:
 *  - MainActivity and CardsManiaApp wrap their base context with [wrap], so Compose `stringResource(...)` uses it.
 *  - Code outside Compose (ViewModels, catalogs) reads text through [Strings], refreshed by [apply].
 *  - Changing the language calls [apply] and then recreates the activity.
 */
object LocaleManager {
    private const val PREFS = "locale_prefs"
    private const val KEY = "lang"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the player picked a language on the Language screen. */
    fun hasChosen(ctx: Context): Boolean = AppLanguage.fromTag(prefs(ctx).getString(KEY, null)) != null

    /** The chosen language; before a choice, the phone's language if we support it, else English. */
    fun current(ctx: Context): AppLanguage =
        AppLanguage.fromTag(prefs(ctx).getString(KEY, null))
            ?: AppLanguage.fromTag(Locale.getDefault().language)
            ?: AppLanguage.EN

    /** Saves [lang] and refreshes the text used outside Compose. The caller recreates the activity. */
    fun apply(ctx: Context, lang: AppLanguage) {
        prefs(ctx).edit().putString(KEY, lang.tag).apply()
        Locale.setDefault(Locale(lang.tag))
        Strings.init(ctx.applicationContext)
    }

    /** [base] with the chosen language applied to its resources. */
    fun wrap(base: Context): Context {
        val locale = Locale(current(base).tag)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }
}

/**
 * Translated text for code that isn't a @Composable (ViewModels, catalogs, toasts, canvas pop-ups).
 * In Compose use `stringResource(R.string.x)` instead.
 * Before [init] (plain JVM unit tests) it returns a placeholder instead of crashing.
 */
object Strings {
    @Volatile private var res: Resources? = null

    fun init(app: Context) {
        res = LocaleManager.wrap(app).resources
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val r = res ?: return "#$id"
        return if (args.isEmpty()) r.getString(id) else r.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val r = res ?: return "#$id"
        return if (args.isEmpty()) r.getQuantityString(id, count, count) else r.getQuantityString(id, count, *args)
    }
}
