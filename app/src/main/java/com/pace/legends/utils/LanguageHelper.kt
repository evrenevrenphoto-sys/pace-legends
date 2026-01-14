package com.pace.legends.utils

import java.util.Locale

object LanguageHelper {
    fun getLocalizedName(names: Map<String, String>): String {
        if (names.isEmpty()) return ""
        
        val currentLanguage = Locale.getDefault().language // e.g., "tr", "en"
        
        return names[currentLanguage] ?: names["en"] ?: names.values.firstOrNull() ?: ""
    }

    fun getLocalizedText(textMap: Map<String, String>): String {
        return getLocalizedName(textMap)
    }
}
