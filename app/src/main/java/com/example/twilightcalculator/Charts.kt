package com.example.twilightcalculator

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * Заполняет график тёмной (астрономической) ночи на месяц.
 */
object Charts {

    /**
     * Заполняет групповой столбиковый график: для каждого дня месяца два столбика —
     * время начала тёмной ночи (вечером) и время её конца (утром следующего дня).
     * Ось X — число месяца, ось Y — время (часы 0..24).
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

        val startEntries = ArrayList<BarEntry>()
        val endEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        for (i in 0 until days) {
            val d = monthStart.plusDays(i.toLong())
            val sky = Sky.astroNight(d, lat, lng, zone)

            val startHours = sky.astroNightStart?.let(::timeToHours) ?: 0f
            val endHours = sky.astroNightEnd?.let(::timeToHours) ?: 0f

            startEntries.add(BarEntry(i.toFloat(), startHours))
            endEntries.add(BarEntry(i.toFloat(), endHours))
            labels.add(d.dayOfMonth.toString())
        }

        val startSet = BarDataSet(startEntries, context.getString(R.string.chart_dark_start))
        startSet.color = startColor
        startSet.setDrawValues(false)

        val endSet = BarDataSet(endEntries, context.getString(R.string.chart_dark_end))
        endSet.color = endColor
        endSet.setDrawValues(false)

        ChartStyle.styleBarGrouped(chart, labels)
        chart.data = BarData(startSet, endSet)

        // Группируем столбики: ширина, зазор внутри группы и между группами.
        val groupSpace = 0.3f
        val barSpace = 0.04f
        val barWidth = 0.33f
        chart.data.barWidth = barWidth
        chart.xAxis.axisMinimum = -0.4f
        chart.xAxis.axisMaximum = days - 0.6f
        chart.groupBars(-0.4f, groupSpace, barSpace)
        chart.invalidate()
    }

    /** Время ночи в часах (дробное), например 21:30 -> 21.5. */
    private fun timeToHours(t: java.time.LocalTime): Float =
        t.hour + t.minute / 60.0f
}