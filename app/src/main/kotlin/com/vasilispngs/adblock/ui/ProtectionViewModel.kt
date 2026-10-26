package com.vasilispngs.adblock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vasilispngs.adblock.AdblockApplication
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.Blocklists
import com.vasilispngs.adblock.data.ListSource
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.data.RuleDao
import com.vasilispngs.adblock.data.SourceDao
import com.vasilispngs.adblock.dns.HOST
import com.vasilispngs.adblock.dns.Resolver
import com.vasilispngs.adblock.dns.probe
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

enum class ProtectionMessage { Saved, ResolverUnreachable }

data class ProtectionUiState(
    val sources: List<ListSource> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val resolver: String = "",
    val message: ProtectionMessage? = null,
    val loaded: Boolean = false
)

internal fun ruleHost(text: String): String? = text.trim().lowercase().trim('.').takeIf(HOST::matches)

class ProtectionViewModel(
    private val blocklists: Blocklists,
    sources: SourceDao,
    private val rules: RuleDao,
    private val preferences: ProtectionPreferences,
    private val resolver: Resolver,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val message = MutableStateFlow<ProtectionMessage?>(null)
    private var resolverCheck: Job? = null

    val uiState: StateFlow<ProtectionUiState> =
        combine(sources.observe(), rules.observe(), preferences.resolver, message) { sources, rules, resolver, message ->
            ProtectionUiState(sources, rules, resolver, message, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProtectionUiState())

    fun addSource(url: String, name: String) {
        viewModelScope.launch { blocklists.add(url, name.trim()) }
    }

    fun removeSource(url: String) {
        viewModelScope.launch { blocklists.remove(url) }
    }

    fun addRule(host: String, action: Action) {
        viewModelScope.launch { rules.save(Rule(host, action, clock())) }
    }

    fun removeRule(host: String) {
        viewModelScope.launch { rules.delete(host) }
    }

    fun setResolver(url: String) {
        resolverCheck?.cancel()
        resolverCheck = viewModelScope.launch {
            if (url == preferences.resolver.first()) return@launch
            if (resolver.probe(url)) {
                preferences.setResolver(url)
                message.value = ProtectionMessage.Saved
            } else {
                message.value = ProtectionMessage.ResolverUnreachable
            }
        }
    }

    fun messageShown() {
        message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AdblockApplication).container
                ProtectionViewModel(
                    container.blocklists,
                    container.database.sources(),
                    container.database.rules(),
                    container.preferences,
                    { url, message, onResult -> container.doh.resolve(url, message, onResult) }
                )
            }
        }
    }
}
