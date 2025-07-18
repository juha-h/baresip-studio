package com.tutpro.baresip

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.core.content.edit

class Preferences(context: Context) {

    companion object {
        private const val DISPLAY_THEME = "com.tutpro.baresip.DISPLAY_THEME"
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    var displayTheme = preferences.getInt(DISPLAY_THEME, -1)
        set(value) = preferences.edit { putInt(DISPLAY_THEME, value) }

    var ringtoneUri = preferences.getString("ringtone_uri", "")
        set(value) = preferences.edit { putString("ringtone_uri", value) }

}