package com.example.twilightcalculator

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Движок расчёта времени восхода/заката и сумерек.
 *
 * Используется классический алгоритм на основе солнечной геометрии
 * (алгоритм NOAA по методике Жана Миуса): для даты, широты и долготы
 * вычисляется момент, когда высота Солнца достигает заданного зенитного угла
 * (90° + высота ниже горизонта).
 *
 * Зенитные углы:
 *  - официальный восход/закат:   по верхнему краю, рефракция 0.833° -> 90.833°
 *  - гражданские сумерки:        Солнце 0°..-6°                     -> 96°
 *  - навигационные сумерки:      Солнце -6°..-12°                   -> 102°
 *  - астрономические сумерки:    Солнце -12°..-18°                  -> 108°
 */
object SunTimes {

    enum class Event { RISE, SET }

    const val ZENITH_OFFICIAL = 90.833
    const val ZENITH_CIVIL = 96.0
    const val ZENITH_NAUTICAL = 102.0
    const val ZENITH_ASTRO = 108.0

    data class TwilightPoint(val label: String, val zenith: Double, val isRise: Boolean)

    /** Точки сумерек: [гражданские/навигационные/астрономические] для утра и вечера. */
    val TWILIGHT_POINTS: List<TwilightPoint> = listOf(
        TwilightPoint("Начало гражданских сумерек (рассвет)", ZENITH_CIVIL, true),
        TwilightPoint("Начало навигационных сумерек", ZENITH_NAUTICAL, true),
        TwilightPoint("Начало астрономических сумерек", ZENITH_ASTRO, true),
        TwilightPoint("Конец астрономических сумерек", ZENITH_ASTRO, false),
        TwilightPoint("Конец навигационных сумерек", ZENITH_NAUTICAL, false),
        TwilightPoint("Конец гражданских сумерек (закат)", ZENITH_CIVIL, false)
    )

    data class DailyTimes(
        val date: LocalDate,
        val sunrise: LocalTime?,
        val sunset: LocalTime?,
        val civilDawn: LocalTime?,
        val nauticalDawn: LocalTime?,
        val astroDawn: LocalTime?,
        val astroDusk: LocalTime?,
        val nauticalDusk: LocalTime?,
        val civilDusk: LocalTime?
    ) {
        /** Долгота дня в часах (1 десятичный знак). null — нет события. */
        fun daylightHours(): Double? {
            if (sunrise == null || sunset == null) return null
            var minutes = java.time.Duration.between(sunrise, sunset).toMinutes()
            if (minutes < 0) minutes += 24 * 60
            if (minutes > 24 * 60) minutes -= 24 * 60
            return (minutes / 60.0 * 10.0).roundToLong() / 10.0
        }
    }

    /** Вычисляет время события для даты/координат. null — событие не наступает. */
    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        event: Event,
        zenith: Double,
        zone: ZoneId
    ): LocalTime? {
        val doy = date.dayOfYear.toDouble()
        // Смещение пояса на выбранную дату (учитывает переход на летнее время).
        val offsetSeconds = offsetOn(date, zone)
        // В полярных районах событие может не наступать в этот день — перебираем
        // соседние дни (обиходный допуск для практических целей), начиная с самого
        // дня и постепенно расширяясь наружу.
        for (delta in 0..40) {
            calcDoy(doy - delta, latitude, longitude, event, zenith, zone, offsetSeconds)
                ?.let { return it }
            if (delta > 0) {
                calcDoy(doy + delta, latitude, longitude, event, zenith, zone, offsetSeconds)
                    ?.let { return it }
            }
        }
        return null
    }

    /** Смещение часового пояса на [date] (учитывает переход на летнее время). */
    private fun offsetOn(date: LocalDate, zone: ZoneId): Int {
        // Полдень даты — безопасная точка вне «переходной» паузы (вторая половина ночи).
        val ldt = LocalDateTime.of(date, LocalTime.NOON)
        return zone.rules.getOffset(ldt).totalSeconds
    }

    /** Все времена для одной даты. */
    fun dailyTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): DailyTimes {
        val riseOfficial = compute(date, latitude, longitude, Event.RISE, ZENITH_OFFICIAL, zone)
        val setOfficial = compute(date, latitude, longitude, Event.SET, ZENITH_OFFICIAL, zone)

        val civilDawn = compute(date, latitude, longitude, Event.RISE, ZENITH_CIVIL, zone)
        val nauticalDawn = compute(date, latitude, longitude, Event.RISE, ZENITH_NAUTICAL, zone)
        val astroDawn = compute(date, latitude, longitude, Event.RISE, ZENITH_ASTRO, zone)

        val civilDusk = compute(date, latitude, longitude, Event.SET, ZENITH_CIVIL, zone)
        val nauticalDusk = compute(date, latitude, longitude, Event.SET, ZENITH_NAUTICAL, zone)
        val astroDusk = compute(date, latitude, longitude, Event.SET, ZENITH_ASTRO, zone)

        return DailyTimes(
            date = date,
            sunrise = riseOfficial,
            sunset = setOfficial,
            civilDawn = civilDawn,
            nauticalDawn = nauticalDawn,
            astroDawn = astroDawn,
            astroDusk = astroDusk,
            nauticalDusk = nauticalDusk,
            civilDusk = civilDusk
        )
    }

    /**
     * Классический алгоритм для конкретного дня года [doy].
     * Возвращает время в местной зоне устройства (передаётся [zone]), либо null.
     */
    private fun calcDoy(
        doy: Double,
        latitude: Double,
        longitude: Double,
        event: Event,
        zenith: Double,
        zone: ZoneId,
        offsetSeconds: Int
    ): LocalTime? {
        val lng = ((longitude + 180.0).rem(360.0)) - 180.0
        val lngHour = lng / 15.0

        val approx = if (event == Event.RISE) 6.0 else 18.0
        val t = doy + ((approx - lngHour) / 24.0)

        val m = (0.9856 * t) - 3.289
        val L = m + 1.916 * sin(Math.toRadians(m)) +
            0.020 * sin(Math.toRadians(2.0 * m)) + 282.634
        val Ln = L - 360.0 * (L / 360).toLong()

        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(Ln))))
        ra += (floor(Ln / 90) - floor(ra / 90)) * 90
        ra /= 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(Ln))
        val cosDec = cos(asin(sinDec))
        if (cosDec == 0.0) return null

        val cosH = (cos(Math.toRadians(zenith)) - sinDec * sin(Math.toRadians(latitude))) /
            (cosDec * cos(Math.toRadians(latitude)))
        if (cosH > 1.0) return null
        if (cosH < -1.0) return null

        var h =
            if (event == Event.RISE) 360.0 - Math.toDegrees(Math.acos(cosH))
            else Math.toDegrees(Math.acos(cosH))
        h /= 15.0

        val tLocal = h + ra - (0.06571 * t) - 6.622
        val utHours = tLocal - lngHour

        var minutes = (utHours * 60.0).roundToLong() % (24 * 60)
        if (minutes < 0) minutes += 24 * 60

        // Переводим UT в локальную зону устройства (сдвиг пояса на дату расчёта).
        minutes = (minutes + offsetSeconds / 60) % (24 * 60)
        if (minutes < 0) minutes += 24 * 60

        return LocalTime.ofSecondOfDay(minutes * 60)
    }

    /** Форматирует время как «HH:mm», либо прочерк, если события нет. */
    fun fmt(t: LocalTime?): String =
        t?.format(TIME_FMT) ?: "—"

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}