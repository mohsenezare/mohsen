from pathlib import Path
import sys
base=Path(sys.argv[1]) if len(sys.argv)>1 else Path("project")
root=base/"app/src/main/java/ir/personal/chand"
vm=root/"MainViewModel.kt"
s=vm.read_text()
a=s.index("    fun startPolling() {")
b=s.index("    fun stopPolling()", a)
new='''    // v4.8.4 price pipeline: first REST quote for every provider, then streams,
    // followed by periodic full reconciliation of visible instruments.
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            var firstLoad = _uiState.value.snapshot?.items?.none(MarketItem::isAvailable) ?: true
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    refreshOnce(showInitialLoading = firstLoad)
                    manualRefreshRequested = false
                    repository.knownDescriptors().forEach { descriptorCache[it.id] = it }
                    startProviderStreams()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.update { current -> current.copy(
                        loading = false,
                        connectionState = if (current.snapshot?.items?.any {
                            ir.personal.chand.ui.MarketLiveStatus.label(it) == "LIVE"
                        } == true) LiveConnectionState.LIVE else LiveConnectionState.OFFLINE
                    ) }
                    startProviderStreams()
                }
                firstLoad = false
                val interval = when {
                    hasVisibleTsetmc() -> TSETMC_RECONCILE_INTERVAL_MS
                    hasVisibleInvesting() || hasVisibleTgju() -> LIVE_RECONCILE_INTERVAL_MS
                    _uiState.value.connectionState == LiveConnectionState.CONNECTING -> CONNECTING_INTERVAL_MS
                    else -> FALLBACK_INTERVAL_MS
                }
                val remaining = (interval - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
                if (remaining > 0L) withTimeoutOrNull(remaining) { refreshSignal.receive() }
                else delay(25L)
            }
        }
    }

'''
vm.write_text(s[:a]+new+s[b:])

stream=root/"data/InvestingStreamClient.kt"
s=stream.read_text()
assert 'private const val POLL_INTERVAL_MS = 12_000L' in s
s=s.replace('private const val POLL_INTERVAL_MS = 12_000L',
            'private const val POLL_INTERVAL_MS = 1_000L // original v4.8.4')
s=s.replace('"lp", "last", "close", "prev_close_price", "prev_close"',
            '"lp", "last", "close"')
assert 'val timestamp = sourceTimestamp(value) ?: 0L' in s
s=s.replace('val timestamp = sourceTimestamp(value) ?: 0L',
            'val trueTimestamp = sourceTimestamp(value)\n                    val timestamp = trueTimestamp ?: receivedAt')
s=s.replace('liveQuoteAtMillis = timestamp,','liveQuoteAtMillis = trueTimestamp ?: 0L,')
stream.write_text(s)

repo=root/"data/MarketRepository.kt"
s=repo.read_text()
assert s.count('"lp", "last", "close", "prev_close_price", "prev_close"') == 1
s=s.replace('"lp", "last", "close", "prev_close_price", "prev_close"',
            '"lp", "last", "close"')
assert 'val sourceTime = actualSourceTime ?: previous?.sourceUpdatedAtMillis ?: 0L' in s
s=s.replace('val sourceTime = actualSourceTime ?: previous?.sourceUpdatedAtMillis ?: 0L',
            'val sourceTime = actualSourceTime ?: receivedAtMillis // original v4.8.4 snapshot timestamp')
assert 'incomingTime == null -> current?.sourceUpdatedAtMillis?.let { it <= 0L } ?: true' in s
s=s.replace('incomingTime == null -> current?.sourceUpdatedAtMillis?.let { it <= 0L } ?: true',
            'incomingTime == null -> current?.origin != DataOrigin.LIVE')
# Do not compare a REST receipt timestamp to an exchange tick timestamp.
# A five-second-old real market tick must not be rejected because REST was received just now.
old_guard = 'previous != null && tick.sourceUpdatedAtMillis > 0L && previous.sourceUpdatedAtMillis > 0L && tick.sourceUpdatedAtMillis < previous.sourceUpdatedAtMillis'
new_guard = 'previous != null && tick.liveQuoteAtMillis > 0L && previous.liveQuoteAtMillis > 0L && tick.liveQuoteAtMillis < previous.liveQuoteAtMillis'
assert old_guard in s
s=s.replace(old_guard, new_guard)
repo.write_text(s)

gradle=base/"app/build.gradle.kts"
g=gradle.read_text()
assert 'versionCode = 51' in g and 'versionName = "4.9.0-investing-global"' in g
gradle.write_text(g.replace('versionCode = 51','versionCode = 52')
    .replace('versionName = "4.9.0-investing-global"',
             'versionName = "4.9.1-restored-484-pricing"'))

assert "pollDueSources" not in vm.read_text()
assert 'POLL_INTERVAL_MS = 1_000L' in stream.read_text()
print("ORIGINAL_V484_PRICE_ENGINE_RESTORED")
