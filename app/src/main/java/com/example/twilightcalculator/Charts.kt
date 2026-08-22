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
        val valueText = ContextCompat.getColor(context, R.color.text_primary)

        val startEntries = ArrayList<BarEntry>()
        val endEntries = ArrayList<BarEntry>()
        val labels = ArrayList<Int>()

        for (i in 0 until days) {
            val d = monthStart.plusDays(i.toLong())
            val sky = Sky.astroNight(d, lat, lng, zone)

            // «Истинная тёмная ночь» — тёмные окна: ночь, когда Луна за горизонтом.
            val dark = sky.darkWindows
            val startHours = if (sky.hasAstroNight && dark.isNotEmpty())
                timeToHours(dark.first().start) else 0f
            val endHours = if (sky.hasAstroNight && dark.isNotEmpty())
                timeToHours(dark.last().end) else 0f

            startEntries.add(BarEntry(i.toFloat(), startHours))
            endEntries.add(BarEntry(i.toFloat(), endHours))
            labels.add(d.dayOfMonth)
        }

        val timeFormatter = TimeValueFormatter()

        val startSet = BarDataSet(startEntries, context.getString(R.string.chart_dark_start))
        startSet.color = startColor
        startSet.valueTextColor = valueText
        startSet.valueTextSize = 9f
        startSet.setDrawValues(true)
        startSet.setValueFormatter(timeFormatter)

        val endSet = BarDataSet(endEntries, context.getString(R.string.chart_dark_end))
        endSet.color = endColor
        endSet.valueTextColor = valueText
        endSet.valueTextSize = 9f
        endSet.setDrawValues(true)
        endSet.setValueFormatter(timeFormatter)

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