package com.tutpro.baresip.plus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedAor = MutableStateFlow("")
    val selectedAor: StateFlow<String> = _selectedAor.asStateFlow()

    fun updateSelectedAor(newValue: String) {
        _selectedAor.value = newValue
    }

}
