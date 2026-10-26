package com.vasilispngs.adblock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.vasilispngs.adblock.AdblockApplication
import com.vasilispngs.adblock.data.Action
import com.vasilispngs.adblock.data.LOG_RETENTION_MILLIS
import com.vasilispngs.adblock.data.LoggedQuery
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.QueryDao
import com.vasilispngs.adblock.data.Rule
import com.vasilispngs.adblock.data.RuleDao
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.TunnelState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val SEARCH_DELAY_MILLIS = 250L
private const val PAGE_SIZE = 50
private const val MAX_SEARCH = 253
private val Running = setOf(TunnelState.On, TunnelState.AlwaysOn, TunnelState.Lockdown)

enum class LogFilter(val action: Action?) { All(null), Blocked(Action.Block), Allowed(Action.Allow) }

data class HomeUiState(
    val tunnel: TunnelState = TunnelState.Off,
    val privateDnsServer: String? = null,
    val vpnSettings: Boolean = false,
    val logEnabled: Boolean = true,
    val filter: LogFilter = LogFilter.All,
    val search: String = "",
    val rules: Map<String, Action> = emptyMap(),
    val loaded: Boolean = false,
    val readyToConnect: Boolean = false
)

private data class Request(val starting: Boolean = false, val readyToConnect: Boolean = false)

private data class LogQuery(val filter: LogFilter, val search: String)

internal fun searchPattern(text: String): String {
    val query = text.trim().lowercase().take(MAX_SEARCH)
    if (query.isEmpty()) return ""
    return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val tunnel: DnsTunnel,
    preferences: ProtectionPreferences,
    privateDnsServer: Flow<String?>,
    private val rules: RuleDao,
    queries: QueryDao,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val request = MutableStateFlow(Request())
    private val logQuery = MutableStateFlow(LogQuery(LogFilter.All, ""))

    val uiState: StateFlow<HomeUiState> = combine(
        combine(tunnel.state, request, ::Pair),
        privateDnsServer,
        preferences.logEnabled,
        rules.observe().map { list -> list.associate { it.host to it.action } },
        logQuery
    ) { (state, request), privateDns, logEnabled, ruleMap, query ->
        HomeUiState(
            tunnel = if (request.starting && state == TunnelState.Off) TunnelState.Connecting else state,
            privateDnsServer = privateDns,
            vpnSettings = tunnel.vpnSettings,
            logEnabled = logEnabled,
            filter = query.filter,
            search = query.search,
            rules = ruleMap,
            loaded = true,
            readyToConnect = request.readyToConnect
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        HomeUiState(tunnel = tunnel.state.value, vpnSettings = tunnel.vpnSettings)
    )

    val log: Flow<PagingData<LoggedQuery>> = logQuery
        .debounce { if (it.search.isEmpty()) 0L else SEARCH_DELAY_MILLIS }
        .flatMapLatest { query ->
            Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
                queries.page(clock() - LOG_RETENTION_MILLIS, query.filter.action, searchPattern(query.search))
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setFilter(filter: LogFilter) = logQuery.update { it.copy(filter = filter) }

    fun setSearch(text: String) = logQuery.update { it.copy(search = text) }

    fun setRule(host: String, action: Action) {
        viewModelScope.launch { rules.save(Rule(host, action, clock())) }
    }

    fun removeRule(host: String) {
        viewModelScope.launch { rules.delete(host) }
    }

    fun turnOn() = request.update { Request(starting = true, readyToConnect = true) }

    fun connectRequestHandled() = request.update { it.copy(readyToConnect = false) }

    fun connect() {
        tunnel.start()
        viewModelScope.launch {
            tunnel.state.first { it != TunnelState.Off }
            request.update { it.copy(starting = false) }
        }
    }

    fun cancel() = request.update { it.copy(starting = false) }

    fun turnOff() {
        request.update { Request() }
        tunnel.stop()
    }

    fun refresh() {
        if (tunnel.state.value in Running) tunnel.start()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AdblockApplication).container
                HomeViewModel(
                    container.tunnel,
                    container.preferences,
                    container.network.privateDnsServer,
                    container.database.rules(),
                    container.database.queries()
                )
            }
        }
    }
}
