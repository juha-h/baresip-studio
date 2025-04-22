package com.tutpro.baresip.plus

import androidx.compose.runtime.MutableState

data class Codec(val name: String, var enabled: MutableState<Boolean>)