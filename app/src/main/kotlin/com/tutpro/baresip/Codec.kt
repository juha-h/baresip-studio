package com.tutpro.baresip

import androidx.compose.runtime.MutableState

data class Codec(val name: String, var enabled: MutableState<Boolean>)