package com.tutpro.baresip

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf

@Immutable
data class CustomColors(
    val primary: Color = Color.Unspecified,
    val primaryDark: Color = Color.Unspecified,
    val primaryLight: Color = Color.Unspecified,
    val secondary: Color = Color.Unspecified,
    val secondaryDark: Color = Color.Unspecified,
    val secondaryLight: Color = Color.Unspecified,
    val gray: Color = Color.Unspecified,
    val grayDark: Color = Color.Unspecified,
    val grayLight: Color = Color.Unspecified,
    val accent: Color = Color.Unspecified,
    val baresip: Color = Color.Unspecified,
    val white: Color = Color.Unspecified,
    val black: Color = Color.Unspecified,
    val strong: Color = Color.Unspecified,
    val green: Color = Color.Unspecified,
    val red: Color = Color.Unspecified,
    val trafficGreen: Color = Color.Unspecified,
    val trafficYellow: Color = Color.Unspecified,
    val trafficRed: Color = Color.Unspecified,
    val light: Color = Color.Unspecified,
    val dark: Color = Color.Unspecified,
    val spinnerText: Color = Color.Unspecified,
    val spinnerDropdown: Color = Color.Unspecified,
    val spinnerDivider: Color = Color.Unspecified,
    val itemText: Color = Color.Unspecified,
    val alertText: Color = Color.Unspecified,
    val codec: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val cardBackground: Color = Color.Unspecified,
    val textFieldBackground: Color = Color.Unspecified,
    val popupBackground: Color = Color.Unspecified,
    val alert: Color = Color.Unspecified,
    val actionbar: Color = Color.Unspecified,
    )

val light_primary = Color(0xff0ca1fd)
val light_primary_dark = Color(0xff0073c9)
val light_primary_light = Color(0xff6ad2ff)
val light_secondary = Color(0xFF00B9A1)
val light_secondary_dark = Color(0xFF008873)
val light_secondary_light = Color(0xff5cecd2)
val light_gray = Color(0xff9e9e9e)
val light_gray_dark = Color(0xFF424242)
val light_gray_light = Color(0xffe0e0e0)
val light_accent = Color(0xFFF77445)
val light_light = Color(0xfffcfcfc)
val light_dark = Color(0xFF383838)
val light_spinner_divider = light_gray
val light_item_text = light_dark
val light_background = light_light
val light_textfield_background = light_gray_light
val light_card_background = light_gray_light
val light_popup_background = light_secondary_dark
val light_alert = light_primary

val dark_primary = Color(0xff0073c9)
val dark_primary_dark = Color(0xff00008b)
val dark_primary_light = Color(0xff0ca1fd)
val dark_secondary = Color(0xFF00B9A1)
val dark_secondary_dark = Color(0xFF008873)
val dark_secondary_light = Color(0xff5cecd2)
val dark_gray = Color(0xff9e9e9e)
val dark_gray_dark = Color(0xFF424242)
val dark_gray_light = Color(0xffe0e0e0)
val dark_accent = Color(0xFFF77445)
val dark_light = dark_gray_light
val dark_dark = Color(0xFF121212)
val dark_spinner_divider = dark_gray_dark
val dark_item_text = dark_gray_light
val dark_background = dark_dark
val dark_textfield_background = dark_gray
val dark_card_background = dark_gray_dark
val dark_popup_background = dark_secondary_dark
val dark_alert = dark_primary_light

val LightCustomColors = CustomColors(
    primary = light_primary,
    primaryDark = light_primary_dark,
    primaryLight = light_primary_light,
    secondary = light_secondary,
    secondaryDark = light_secondary_dark,
    secondaryLight = light_secondary_light,
    gray = light_gray,
    grayDark = light_gray_dark,
    grayLight = light_gray_light,
    accent = light_accent,
    light = light_light,
    dark = light_dark,
    spinnerDivider = light_spinner_divider,
    itemText = light_item_text,
    background = light_background,
    cardBackground = light_card_background,
    textFieldBackground = light_textfield_background,
    popupBackground = light_popup_background,
    alert = light_alert,
)

val DarkCustomColors = CustomColors(
    primary = dark_primary,
    primaryDark = dark_primary_dark,
    primaryLight = dark_primary_light,
    secondary = dark_secondary,
    secondaryDark = dark_secondary_dark,
    secondaryLight = dark_secondary_light,
    gray = dark_gray,
    grayDark = dark_gray_dark,
    grayLight = dark_gray_light,
    accent = dark_accent,
    light = dark_light,
    dark = dark_dark,
    spinnerDivider = dark_spinner_divider,
    itemText = dark_item_text,
    background = dark_background,
    cardBackground = dark_card_background,
    textFieldBackground = dark_textfield_background,
    popupBackground = dark_popup_background,
    alert = dark_alert,
)

val LocalCustomColors = compositionLocalOf { CustomColors() }
