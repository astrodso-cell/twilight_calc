package com.example.twilightcalculator

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Заполняет график «истинной тёмной ночи» на месяц.
 */
object Charts {

    /**
     * Групповой столбиковый график: для каждого дня месяца два столбика —
     * начало и конец «истинной тёмной ночи» (Солнце ниже −18° и Луна за горизонтом).
     * Ось X — число месяца, ось Y — время в формате ЧЧ:ММ (0..24 ч).
     */
    fun fillDarkNight(
        context: Context,
        chart: BarChart,
        monthStart: LocalDate,
        days: Int,
        lat: Double,
        lng: Double,
        zone: ZoneId
    ) {
        val startColor = ContextCompat.getColor(context, R.color.chart_night_start)
        val endColor = ContextCompat.getColor(context, R.color.chart_night_end)

        // Для каждого календарного дня месяца собираем тёмное время в пределах
        // его собственных суток [0..24). Ночь, пересекающая полночь (например
        // 20:37 → 00:34), разбивается на «вечер» (на дате x) и «утро» (на дате x+1),
        // чтобы данные не «уезжали» на соседний столбик.
        val startEntries = ArrayList<BarEntry>()
        val endEntries = ArrayList<BarEntry>()
        val labels = ArrayList<Int>()

        for (i in 0 until days) {
            val d = monthStart.plusDays(i.toLong())
            // Календарные сутки d: утро (из ночи d-1→d) + вечер (из ночи d→d+1).
            val (lo, hi) = darkSegmentOfDay(d, lat, lng, zone)

            val startH = lo?.takeIf { it.isFinite() } ?: 0f
            val endH = hi?.takeIf { it.isFinite() } ?: 0f

            startEntries.add(BarEntry(i.toFloat(), startH))
            endEntries.add(BarEntry(i.toFloat(), endH))
            labels.add(d.dayOfMonth)
        }

        val timeFormatter = TimeValueFormatter()

        val startSet = BarDataSet(startEntries, context.getString(R.string.chart_dark_start))
        startSet.color = startColor
        startSet.setDrawValues(false)

        val endSet = BarDataSet(endEntries, context.getString(R.string.chart_dark_end))
        endSet.color = endColor
        endSet.setDrawValues(false)

        ChartStyle.styleBarGrouped(chart, labels, timeFormatter)
        chart.data = BarData(startSet, endSet)

        // Группировка: ширина столбика, зазор внутри группы и между днями.
        val groupSpace = 0.25f
        val barSpace = 0.04f
        chart.data.barWidth = 0.34f
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = days - 0.5f
        chart.groupBars(-0.5f, groupSpace, barSpace)
        chart.invalidate()
    }

    /**
     * Тёмное время в календарные сутки [date]: пары (начало, конец) в часах,
     * только в пределах [0,24). Из ночи [date] берём только вечернюю часть,
     * из ночи date-1 — только утреннюю, пересекающую полночь.
     */
    private fun darkSegmentOfDay(
        date: LocalDate,
        lat: Double,
        lng: Double,
        zone: ZoneId
    ): Pair<Float?, Float?> {
        // Ночь date: вечер date → утро date+1.
        val sky = Sky.astroNight(date, lat, lng, zone)
        var lo: Float? = null
        var hi: Float? = null
        if (sky.hasAstroNight) {
            for (w in sky.darkWindows) {
                val s = timeToHours(w.start)
                val e = timeToHours(w.end)
                if (e >= s) {
                    // Неполночное окно целиком в сутках date.
                    lo = if (lo == null) s else minOf(lo, s)
                    hi = if (hi == null) e else maxOf(hi, e)
                } else {
                    // Переход полночь: вечерняя часть в date [s..24).
                    lo = if (lo == null) s else minOf(lo, s)
                    hi = 24f
                }
            }
        }
        // Утренняя часть из ночи date-1 (если та перешла полночь).
        val prev = Sky.astroNight(date.minusDays(1), lat, lng, zone)
        if (prev.hasAstroNight) {
            for (w in prev.darkWindows) {
                val s = timeToHours(w.start)
                val e = timeToHours(w.end)
                if (e < s) {
                    hi = if (hi == null) e else maxOf(hi, e)
                    lo = 0f
                }
            }
        }
        return lo to hi
    }

    /** Время в часах с долей минуты, напр. 21:30 -> 21.5. */
    fun timeToHours(t: LocalTime): Float =
        t.hour + t.minute / 60.0f

    private fun hoursToText(h: Float): String {
        if (h < 0.5f) return "—" // тёмной ночи нет
        val totalMin = (h * 60).roundToInt()
        return String.format(Locale.US, "%02d:%02d", totalMin / 60, totalMin % 60)
    }

    /** Форматирует часы как ЧЧ:ММ для осей и значений столбиков. */
    class TimeValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float, axis: AxisBase): String =
            hoursToText(value)

        override fun getFormattedValue(value: Float): String =
            hoursToText(value)
    }
}