package com.tutpro.baresip

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
val light_baresip = Color(0xff0ca1fd)
val light_white = Color(0xffffffff)
val light_black = Color(0xFF000000)
val light_strong = light_black
val light_green = Color(0xff01df01)
val light_red = Color(0xffff0000)
val light_traffic_green = Color(0xFF4CAF50)
val light_traffic_yellow = Color(0xFFFFEB3B)
val light_traffic_red = Color(0xFFFF5722)
val light_light = light_white
val light_dark = Color(0xFF383838)
val light_spinner_text = light_dark
val light_spinner_dropdown = light_gray_light
val light_spinner_divider = light_gray
val light_item_text = light_black
val light_alert_text = light_black
val light_codec = light_black
val light_background = Color(0xfffcfcfc)
val light_popup_background = light_secondary_dark
val light_alert = Color(0xff0ca1fd) // == light_primary
val light_actionbar = light_background

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
val dark_baresip = Color(0xff0ca1fd)
val dark_white = Color(0xffffffff)
val dark_black = Color(0xFF000000)
val dark_strong = dark_white
val dark_green = Color(0xff01df01)
val dark_red = Color(0xffdf0101)
val dark_traffic_green = Color(0xFF2E7D32)
val dark_traffic_yellow = Color(0xFFF9A825)
val dark_traffic_red = Color(0xFFC62828)
val dark_light = dark_gray_light
val dark_dark = Color(0xFF383838)
val dark_spinner_text = dark_dark
val dark_spinner_dropdown = dark_gray
val dark_spinner_divider = dark_gray_dark
val dark_item_text = dark_gray_light
val dark_alert_text = dark_gray_light
val dark_codec = dark_secondary_dark
val dark_background = dark_dark
val dark_popup_background = dark_secondary_dark
val dark_alert = dark_primary_light
val dark_actionbar = dark_gray_dark

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
    baresip = light_baresip,
    white = light_white,
    black = light_black,
    strong = light_strong,
    green = light_green,
    red = light_red,
    trafficGreen = light_traffic_green,
    trafficYellow = light_traffic_yellow,
    trafficRed = light_traffic_red,
    light = light_light,
    dark = light_dark,
    spinnerText = light_spinner_text,
    spinnerDropdown = light_spinner_dropdown,
    spinnerDivider = light_spinner_divider,
    itemText = light_item_text,
    alertText = light_alert_text,
    codec = light_codec,
    background = light_background,
    popupBackground = light_popup_background,
    alert = light_alert,
    actionbar = light_actionbar,
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
    baresip = dark_baresip,
    white = dark_white,
    black = dark_black,
    strong = dark_strong,
    green = dark_green,
    red = dark_red,
    trafficGreen = dark_traffic_green,
    trafficYellow = dark_traffic_yellow,
    trafficRed = dark_traffic_red,
    light = dark_light,
    dark = dark_dark,
    spinnerText = dark_spinner_text,
    spinnerDropdown = dark_spinner_dropdown,
    spinnerDivider = dark_spinner_divider,
    itemText = dark_item_text,
    alertText = dark_alert_text,
    codec = dark_codec,
    background = dark_background,
    popupBackground = dark_popup_background,
    alert = dark_alert,
    actionbar = dark_actionbar,
    )

val LocalCustomColors = compositionLocalOf { CustomColors() }
