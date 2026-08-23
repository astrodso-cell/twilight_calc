package com.example.twilightcalculator

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Движок расчёта Луны: топоцентрическая высота над горизонтом, фаза.
 *
 * Используется усечённый ряд Жана Миуса (точности ~0.5° достаточно для
 * определения, находится ли Луна над/под горизонтом в течение ночи).
 */
object Moon {

    /** Фаза Луны: освещённость (%) и название. */
    data class PhaseInfo(val illuminationPercent: Int, val name: String)

    /** Минуты эпохи Unix (UTC) для локального времени [time] в зоне [zone]. */
    fun utcEpochMinute(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().epochSecond / 60

    /** Минуты эпохи Unix (UTC) для полдня [date] локальной зоны [zone]. */
    private fun utcEpochMinuteNoon(date: LocalDate, zone: ZoneId): Long =
        LocalDateTime.of(date, LocalTime.NOON).atZone(zone).toInstant().epochSecond / 60

    /** Локальное время (в зоне устройства) для минуты эпохи Unix (UTC). */
    fun localTimeAt(epochMinuteUtc: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochSecond(epochMinuteUtc * 60).atZone(zone).toLocalTime()

    /** Топоцентрическая высота Луны над горизонтом (градусы, север положительный). */
    fun altitudeAt(epochMinuteUtc: Long, latitude: Double, longitude: Double): Double =
        AltitudeComputer(latitude, longitude).altitudeAt(epochMinuteUtc)

    /**
     * Быстрый расчёт высоты Луны для фиксированной точки наблюдения.
     *
     * Наблюдательно-зависимые тригонометрические величины (sin/cos широты и
     * параметры наклона эклиптики) вычисляются один раз в конструкторе,
     * а не на каждом обороте сканирования ночи (сотни вызовов).
     */
    class AltitudeComputer(val latitude: Double, val longitude: Double) {

        private val sinLat = sin(rad(latitude))
        private val cosLat = cos(rad(latitude))
        private val sinEps0 = sin(rad(EPS0))
        private val cosEps0 = cos(rad(EPS0))
        private val epsRate = EPS_DECLINATION / 36525.0

        /** Высота Луны для минуты эпохи Unix (UTC). */
        fun altitudeAt(epochMinuteUtc: Long): Double {
            val d = daysSinceJ2000(epochMinuteUtc)
            val t = d / 36525.0

            val mSun = norm(357.5291 + 35999.0503 * t)          // средняя аномалия Солнца
            val Lp = norm(218.3164477 + 481267.88123421 * t)    // средняя долгота Луны
            val l = norm(134.9634114 + 477198.8676313 * t)      // средняя аномалия Луны
            val F = norm(93.2720993 + 483202.0175273 * t)       // аргумент широты
            val D = norm(297.8501921 + 445267.1114034 * t)      // среднее удлинение

            // Эклиптическая долгота Луны.
            val lp = Lp +
                6.288774 * sin(rad(l)) +
                1.274024 * sin(rad(2 * D - l)) +
                0.658314 * sin(rad(2 * D)) -
                0.213618 * sin(rad(2 * l)) -
                0.185116 * sin(rad(mSun)) -
                0.114332 * sin(rad(2 * F)) +
                0.058793 * sin(rad(2 * D - 2 * l)) +
                0.057066 * sin(rad(2 * D - l - mSun)) +
                0.053322 * sin(rad(2 * D + l))

            // Эклиптическая широта.
            val beta = 5.128122 * sin(rad(F)) +
                0.280606 * sin(rad(l + F)) +
                0.277693 * sin(rad(l - F)) +
                0.173238 * sin(rad(2 * D - F)) -
                0.055413 * sin(rad(2 * D + F - l)) -
                0.046575 * sin(rad(2 * D - F - l)) +
                0.032734 * sin(rad(2 * D + F)) +
                0.021843 * sin(rad(2 * l + F)) -
                0.027493 * sin(rad(2 * l - F))

            // Наклон эклиптики (линейно меняется со временем).
            val eps = EPS0 + epsRate * t
            val sinEps = sin(rad(eps))
            val cosEps = cos(rad(eps))

            // Прямое восхождение и склонение.
            val sinBeta = sin(rad(beta))
            val cosBeta = cos(rad(beta))
            val sinDec = sinBeta * cosEps + cosBeta * sinEps * sin(rad(lp))
            val dec = asin(sinDec)
            val ra = atan2(
                sin(rad(lp)) * cosEps - tan(rad(beta)) * sinEps,
                cos(rad(lp))
            )

            // Гринвичское звёздное время -> местное.
            val gmst = norm(280.46061837 + 360.98564736629 * d)
            val lst = gmst + longitude
            val h = rad(lst) - ra

            // Геоцентрическая высота.
            val sinAlt = sinLat * sin(dec) + cosLat * cos(dec) * cos(h)
            val altGeo = asin(sinAlt)

            // Горизонтальный параллакс -> топоцентрическая высота.
            val pi = 0.950724 + 0.051818 * cos(rad(l)) +
                0.009531 * cos(rad(2 * D - l)) +
                0.007843 * cos(rad(2 * D)) +
                0.002824 * cos(rad(2 * l))

            val altTopo = altGeo + asin(sin(rad(pi)) * cos(altGeo))
            return Math.toDegrees(altTopo)
        }

        private companion object {
            const val EPS0 = 23.4392911
            const val EPS_DECLINATION = -0.0130042
        }
    }

    /** Фаза Луны для даты. */
    fun phase(date: LocalDate, zone: ZoneId): PhaseInfo {
        val d = daysSinceJ2000(utcEpochMinuteNoon(date, zone))
        val t = d / 36525.0

        val mSun = norm(357.5291 + 35999.0503 * t)
        val Lp = norm(218.3164477 + 481267.88123421 * t)
        val l = norm(134.9634114 + 477198.8676313 * t)
        val F = norm(93.2720993 + 483202.0175273 * t)
        val D = norm(297.8501921 + 445267.1114034 * t)

        val lp = Lp + 6.288774 * sin(rad(l)) +
            1.274024 * sin(rad(2 * D - l)) +
            0.658314 * sin(rad(2 * D)) -
            0.213618 * sin(rad(2 * l)) -
            0.185116 * sin(rad(mSun)) -
            0.114332 * sin(rad(2 * F)) +
            0.058793 * sin(rad(2 * D - 2 * l)) +
            0.057066 * sin(rad(2 * D - l - mSun)) +
            0.053322 * sin(rad(2 * D + l))

        val beta = 5.128122 * sin(rad(F)) +
            0.280606 * sin(rad(l + F)) +
            0.277693 * sin(rad(l - F)) +
            0.173238 * sin(rad(2 * D - F)) -
            0.055413 * sin(rad(2 * D + F - l)) -
            0.046575 * sin(rad(2 * D - F - l)) +
            0.032734 * sin(rad(2 * D + F)) +
            0.021843 * sin(rad(2 * l + F)) -
            0.027493 * sin(rad(2 * l - F))

        val sunLon = norm(
            280.46646 + 36000.76983 * t +   // средняя долгота Солнца (от эпохи J2000)
                1.914666 * sin(rad(mSun)) +
                0.019994 * sin(rad(2 * mSun))
        )
        val elongCos = (cos(rad(beta)) * cos(rad(lp - sunLon))).coerceIn(-1.0, 1.0)
        val inc = Math.acos(elongCos)
        // Доля освещённого диска: новолуние (угол 0°) -> 0%, полнолуние (180°) -> 100%.
        val illum = ((1 - cos(inc)) / 2.0 * 100).toInt().coerceIn(0, 100)

        val phaseAngle = norm(lp - sunLon)
        val waxing = phaseAngle in 0.0..180.0
        return PhaseInfo(illum, phaseName(illum, waxing))
    }

    private fun phaseName(illum: Int, waxing: Boolean): String = when {
        illum <= 3 -> "Новолуние"
        illum >= 97 -> "Полнолуние"
        illum < 50 -> if (waxing) "Растущий серп" else "Убывающий серп"
        else -> if (waxing) "Растущая Луна" else "Убывающая Луна"
    }

    private fun daysSinceJ2000(epochMinuteUtc: Long): Double =
        epochMinuteUtc / 1440.0 - 10957.5

    private fun rad(x: Double): Double = Math.toRadians(x)

    private fun norm(x: Double): Double {
        var a = x % 360.0
        if (a < 0) a += 360.0
        return a
    }
}