package com.whoami22888.mediavault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.model.AuditEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuditViewModel(application: Application) : AndroidViewModel(application) {
    private val _events = MutableStateFlow<List<AuditEvent>>(emptyList())
    val events: StateFlow<List<AuditEvent>> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            AuditStore(application).events().collectLatest { auditEvents ->
                _events.value = auditEvents
            }
        }
    }
}
