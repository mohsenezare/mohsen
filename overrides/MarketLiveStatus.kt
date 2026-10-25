package ir.personal.chand.ui

import ir.personal.chand.data.MarketItem
import ir.personal.chand.data.MarketSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The card's LIVE badge means the instrument's *trading session is scheduled
 * to be open*. It does NOT measure how recently a price was downloaded or
 * changed: a quiet market must not make the badge flicker.
 *
 * Session times are local to their exchanges. Provider-specific holidays,
 * unscheduled halts and extraordinary sessions cannot be confirmed by this
 * offline schedule; they need an authoritative exchange session feed.
 */
object MarketLiveStatus {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val newYork = ZoneId.of("America/New_York")

    fun label(item: MarketItem, now: Long = System.currentTimeMillis()): String =
        if (isMarketOpen(item, now)) "LIVE" else ""

    fun isMarketOpen(item: MarketItem, now: Long = System.currentTimeMillis()): Boolean {
        if (isCrypto(item)) return true // Crypto exchanges are open 24/7.
        val instant = Instant.ofEpochMilli(now)
        return when (item.source) {
            MarketSource.TSETMC -> iranExchangeSession(item, instant.atZone(tehran))
            MarketSource.TGJU -> {
                if (item.category.contains("جهانی") || item.category.contains("فارکس") ||
                    item.category.contains("بین‌المللی") || item.category.contains("بین المللی")) {
                    globalSession(item, instant.atZone(newYork))
                } else iranCashSession(item, instant.atZone(tehran))
            }
            MarketSource.TRADINGVIEW -> globalSession(item, instant.atZone(newYork))
        }
    }

    private fun isCrypto(item: MarketItem): Boolean {
        val category = item.category
        return category.contains("ارز دیجیتال") || category.contains("رمزارز") ||
            item.symbol.equals("CRYPTO", ignoreCase = true) ||
            (item.source != MarketSource.TSETMC &&
                item.code.uppercase() in setOf("BTC", "ETH", "USDT", "BTC/USD", "ETH/USD"))
    }

    private fun iranExchangeSession(item: MarketItem, local: ZonedDateTime): Boolean {
        if (!isIranExchangeDay(local.dayOfWeek)) return false
        val goldOrSilverFund = item.category.contains("صندوق طلا") ||
            item.category.contains("صندوق نقره") ||
            ((item.name.contains("صندوق") || item.category.contains("صندوق")) &&
                (item.name.contains("طلا") || item.name.contains("نقره") ||
                    item.name.contains("عیار") || item.code in setOf("عیار", "طلا", "نقره")))
        if (goldOrSilverFund) return between(local, 12, 0, 18, 0)
        val fixedIncome = item.name.contains("درآمد ثابت") || item.category.contains("درآمد ثابت")
        return if (fixedIncome) between(local, 8, 30, 15, 0)
        else between(local, 9, 0, 12, 30)
    }

    private fun iranCashSession(item: MarketItem, local: ZonedDateTime): Boolean {
        if (local.dayOfWeek == DayOfWeek.FRIDAY) return false
        // TGJU can quote exchange-traded funds; those follow the exchange,
        // not street-gold or currency-dealer opening hours.
        if (item.category.contains("صندوق") || item.id == "ayar") {
            return isIranExchangeDay(local.dayOfWeek) && between(local, 12, 0, 18, 0)
        }
        val thursday = local.dayOfWeek == DayOfWeek.THURSDAY
        val goldOrSilver = item.category.contains("طلا") ||
            item.category.contains("سکه") || item.category.contains("نقره") ||
            item.symbol.equals("GOLD", ignoreCase = true)
        return if (goldOrSilver) {
            if (thursday) between(local, 11, 0, 14, 0)
            else between(local, 11, 0, 19, 0)
        } else {
            if (thursday) between(local, 10, 0, 14, 0)
            else between(local, 10, 0, 19, 0)
        }
    }

    private fun globalSession(item: MarketItem, ny: ZonedDateTime): Boolean {
        val category = item.category
        val symbol = item.symbol.uppercase()
        val ticker = item.code.uppercase()
        val exchange = item.id.removePrefix("tv:").substringBefore(':').uppercase()
        val weekday = ny.dayOfWeek
        // NYSE/Nasdaq listings have a different daily session than forex.
        if (exchange in setOf("NASDAQ", "NYSE", "AMEX", "ARCA")) {
            return weekday in DayOfWeek.MONDAY..DayOfWeek.FRIDAY && between(ny, 9, 30, 16, 0)
        }
        val metalsOrEnergy = category.contains("طلای جهانی") || category.contains("نقره جهانی") ||
            category.contains("انرژی جهانی") || symbol in setOf("GOLD", "SILVER", "OIL") ||
            ticker in setOf("XAU/USD", "XAG/USD", "XAUUSD", "XAGUSD", "BRENT", "WTI", "UKOIL", "USOIL")
        if (metalsOrEnergy) {
            // Typical OTC metal/oil session, with the daily 17:00-18:00 NY break.
            return when (weekday) {
                DayOfWeek.SUNDAY -> !ny.toLocalTime().isBefore(LocalTime.of(18, 0))
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY -> ny.toLocalTime().isBefore(LocalTime.of(17, 0)) ||
                    !ny.toLocalTime().isBefore(LocalTime.of(18, 0))
                DayOfWeek.FRIDAY -> ny.toLocalTime().isBefore(LocalTime.of(17, 0))
                else -> false
            }
        }
        if (category.contains("فارکس") || symbol == "FX" || exchange in setOf("FX", "OANDA", "FOREXCOM", "FX_IDC")) {
            // Forex weekly session: Sunday 17:00 through Friday 17:00 New York.
            return when (weekday) {
                DayOfWeek.SUNDAY -> !ny.toLocalTime().isBefore(LocalTime.of(17, 0))
                DayOfWeek.FRIDAY -> ny.toLocalTime().isBefore(LocalTime.of(17, 0))
                DayOfWeek.SATURDAY -> false
                else -> true
            }
        }
        // Never show a confident LIVE label for an unknown exchange calendar.
        return false
    }

    private fun isIranExchangeDay(day: DayOfWeek): Boolean =
        day != DayOfWeek.THURSDAY && day != DayOfWeek.FRIDAY

    private fun between(time: ZonedDateTime, h1: Int, m1: Int, h2: Int, m2: Int): Boolean {
        val local = time.toLocalTime()
        return !local.isBefore(LocalTime.of(h1, m1)) && local.isBefore(LocalTime.of(h2, m2))
    }
}
