package ir.personal.chand.ui

import ir.personal.chand.data.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketLiveStatusTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val ny = ZoneId.of("America/New_York")

    private fun epoch(zone: ZoneId, date: String, time: String) =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun quote(source: MarketSource, category: String = "", symbol: String = "", code: String = "TEST") = MarketItem(
        id = "test", name = "Test", code = code, symbol = symbol,
        price = 100.0, change = 0.0, changePercent = 0.0,
        high = 100.0, low = 100.0, buy = 100.0, sell = 100.0,
        history = emptyList(), sourceUpdatedAtMillis = 0L,
        liveQuoteAtMillis = 0L, receivedAtMillis = 0L, origin = DataOrigin.CACHE,
        isAvailable = false, source = source, category = category
    )

    @Test fun iranStockSessionDependsOnHoursNotPriceUpdates() {
        val stock = quote(MarketSource.TSETMC, category = "بورس ایران")
        assertEquals("", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-24","08:59")))
        assertEquals("LIVE", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-24","09:00")))
        assertEquals("LIVE", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-24","12:29")))
        assertEquals("", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-24","12:30")))
    }

    @Test fun iranExchangeClosesThursdayAndFriday() {
        val stock = quote(MarketSource.TSETMC)
        assertEquals("", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-29","10:00")))
        assertEquals("", MarketLiveStatus.label(stock, epoch(tehran,"2026-10-30","10:00")))
    }

    @Test fun iranGoldFundStartsAtNoonEvenWhenStocksAlreadyOpen() {
        val fund = quote(MarketSource.TGJU, category = "صندوق طلا", code = "AYAR")
        assertEquals("", MarketLiveStatus.label(fund, epoch(tehran,"2026-10-24","11:59")))
        assertEquals("LIVE", MarketLiveStatus.label(fund, epoch(tehran,"2026-10-24","12:00")))
        assertEquals("LIVE", MarketLiveStatus.label(fund, epoch(tehran,"2026-10-24","17:59")))
        assertEquals("", MarketLiveStatus.label(fund, epoch(tehran,"2026-10-24","18:00")))
    }

    @Test fun iranCurrencySessionDoesNotFlickerWhenQuoteStaysUnchanged() {
        val usd = quote(MarketSource.TGJU, category = "ارز آزاد")
        assertEquals("LIVE", MarketLiveStatus.label(usd, epoch(tehran,"2026-10-25","11:00")))
        assertEquals("LIVE", MarketLiveStatus.label(usd, epoch(tehran,"2026-10-25","18:30")))
        assertEquals("", MarketLiveStatus.label(usd, epoch(tehran,"2026-10-25","19:00")))
    }

    @Test fun weekendForexAndSundayOpenFollowNewYorkTime() {
        val eurusd = quote(MarketSource.TRADINGVIEW, category = "فارکس جهانی", symbol = "FX")
        assertEquals("", MarketLiveStatus.label(eurusd, epoch(ny,"2026-10-25","16:59")))
        assertEquals("LIVE", MarketLiveStatus.label(eurusd, epoch(ny,"2026-10-25","17:00")))
        assertEquals("", MarketLiveStatus.label(eurusd, epoch(ny,"2026-10-30","17:00")))
    }

    @Test fun forexSessionTracksNewYorkDaylightSavings() {
        val eurusd = quote(MarketSource.TRADINGVIEW, category = "فارکس جهانی", symbol = "FX")
        assertEquals("", MarketLiveStatus.label(eurusd, epoch(ny,"2026-11-01","16:59")))
        assertEquals("LIVE", MarketLiveStatus.label(eurusd, epoch(ny,"2026-11-01","17:00")))
    }

    @Test fun goldHasDailyRolloverBreak() {
        val gold = quote(MarketSource.TRADINGVIEW, category = "طلای جهانی", symbol = "GOLD")
        assertEquals("", MarketLiveStatus.label(gold, epoch(ny,"2026-10-25","17:59")))
        assertEquals("LIVE", MarketLiveStatus.label(gold, epoch(ny,"2026-10-25","18:00")))
        assertEquals("", MarketLiveStatus.label(gold, epoch(ny,"2026-10-26","17:01")))
        assertEquals("LIVE", MarketLiveStatus.label(gold, epoch(ny,"2026-10-26","18:01")))
    }

    @Test fun cryptoRemainsOpenOnWeekendsWithoutFreshQuote() {
        val btc = quote(MarketSource.TRADINGVIEW, category = "ارز دیجیتال جهانی", symbol = "CRYPTO")
        assertEquals("LIVE", MarketLiveStatus.label(btc, epoch(tehran,"2026-10-30","04:00")))
        val usdt = quote(MarketSource.TGJU, category = "ارز دیجیتال داخلی", code = "USDT")
        assertEquals("LIVE", MarketLiveStatus.label(usdt, epoch(tehran,"2026-10-30","04:00")))
    }

    @Test fun closedOrUnknownNeverShowsPlaceholder() {
        val unknown = quote(MarketSource.TRADINGVIEW, category = "unknown")
        assertEquals("", MarketLiveStatus.label(unknown, epoch(ny,"2026-10-26","12:00")))
        val gold = quote(MarketSource.TGJU, category = "طلای ۱۸ عیار")
        assertEquals("", MarketLiveStatus.label(gold, epoch(tehran,"2026-10-30","12:00")))
    }
}
