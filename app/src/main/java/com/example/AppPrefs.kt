package com.example

import android.content.Context

object AppPrefs {
    private const val PREFS_NAME = "taxi_macro_settings"
    private const val KEY_TARGET_PACKAGE = "target_package"
    private const val KEY_NAVIGATOR_TYPE = "navigator_type" // "navigator" (Яндекс Навигатор) or "maps" (Яндекс Карты)

    fun getTargetPackage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TARGET_PACKAGE, "com.example.radar") ?: "com.example.radar"
    }

    fun setTargetPackage(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TARGET_PACKAGE, pkg).apply()
    }

    fun getNavigatorType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAVIGATOR_TYPE, "navigator") ?: "navigator"
    }

    fun setNavigatorType(context: Context, type: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NAVIGATOR_TYPE, type).apply()
    }
}
