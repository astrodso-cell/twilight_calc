package com.example.twilightcalculator

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Заполняет три графика данными астрономических расчётов.
 */
object Charts {

    /** Заполняет график высоты Солнца и Луны в течение суток. */
    fun fillAltitude(
        context: Context,
        chart: LineChart,
        date: LocalDate,
        lat: Double,
        lng: Double,
        zone: ZoneId
    ) {
        val stepMin = 10
        val sunEntries = ArrayList<Entry>()
        val moonEntries = ArrayList<Entry>()
        var minute = 0
        while (minute < 24 * 60) {
            val time = LocalTime.ofSecondOfDay(minute * 60)
            val epochMinute = Moon.utcEpochMinute(date, time, zone)

            val x = minute / 60f // часы с начала суток
            sunEntries.add(Entry(x, SunTimes.altitudeAt(epochMinute, lat, lng).toFloat()))
            moonEntries.add(Entry(x, Moon.altitudeAt(epochMinute, lat, lng).toFloat()))

            minute += stepMin
        }

        val sun = LineDataSet(sunEntries, context.getString(R.string.chart_altitude_legend_sun))
        sun.color = ContextCompat.getColor(context, R.color.chart_sun)
        sun.setCircleColor(ContextCompat.getColor(context, R.color.chart_sun))
        sun.setDrawCircles(false)
        sun.mode = LineDataSet.Mode.CUBIC_BEZIER
        sun.lineWidth = 2f
        sun.setDrawValues(false)

        val moon = LineDataSet(moonEntries, context.getString(R.string.chart_altitude_legend_moon))
        moon.color = ContextCompat.getColor(context, R.color.chart_moon)
        moon.setCircleColor(ContextCompat.getColor(context, R.color.chart_moon))
        moon.setDrawCircles(false)
        moon.mode = LineDataSet.Mode.CUBIC_BEZIER
        moon.lineWidth = 2f
        moon.setDrawValues(false)

        ChartStyle.styleLine(chart)
        chart.data = LineData(sun, moon)

        // Уровни сумерек и ночи.
        addLimit(chart, "0°", 0f, 0xFF9AA7C7.toInt())
        addLimit(chart, "−6°", -6f, 0xFF7DD3FC.toInt())
        addLimit(chart, "−12°", -12f, 0xFF7DD3FC.toInt())
        addLimit(chart, "−18°", -18f, 0xFFC4B5FD.toInt())

        chart.invalidate()
    }

    private fun addLimit(chart: LineChart, label: String, value: Float, color: Int) {
        val ll = LimitLine(value, label)
        ll.lineColor = color
        ll.lineWidth = 1f
        ll.enableDashedLine(8f, 8f, 0f)
        ll.textColor = color
        ll.textSize = 10f
        ll.labelPosition = LimitLine.LimitLabelPosition.RIGHT
        chart.axisLeft.addLimitLine(ll)
    }

    /** Заполняет график фазы Луны на [days] дней с подсветкой текущего. */
    fun fillMoonPhase(
        context: Context,
        chart: BarChart,
        startDate: LocalDate,
        days: Int,
        zone: ZoneId
    ) {
        val barColor = ContextCompat.getColor(context, R.color.chart_bar)
        val currentColor = ContextCompat.getColor(context, R.color.chart_bar_current)
        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()
        for (i in 0 until days) {
            val d = startDate.plusDays(i.toLong())
            val illum = Moon.phase(d, zone).illuminationPercent
            entries.add(BarEntry(i.toFloat(), illum.toFloat()))
            colors.add(if (i == 0) currentColor else barColor)
        }

        val set = BarDataSet(entries, "")
        set.color = barColor
        set.setColors(colors)
        set.setDrawValues(false)

        ChartStyle.styleBar(chart)
        chart.data = BarData(set)
        chart.data.barWidth = 0.8f
        chart.invalidate()
    }

    /** Заполняет график долготы дня на [days] дней. */
    fun fillDayLength(
        context: Context,
        chart: BarChart,
        startDate: LocalDate,
        days: Int,
        lat: Double,
        lng: Double,
        zone: ZoneId
    ) {
        val barColor = ContextCompat.getColor(context, R.color.chart_bar)
        val currentColor = ContextCompat.getColor(context, R.color.chart_bar_current)
        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()
        for (i in 0 until days) {
            val d = startDate.plusDays(i.toLong())
            val hours = SunTimes.dailyTimes(d, lat, lng, zone).daylightHours() ?: 0.0
            entries.add(BarEntry(i.toFloat(), hours.toFloat()))
            colors.add(if (i == 0) currentColor else barColor)
        }

        val set = BarDataSet(entries, "")
        set.color = barColor
        set.setColors(colors)
        set.setDrawValues(false)

        ChartStyle.styleBar(chart)
        chart.data = BarData(set)
        chart.data.barWidth = 0.8f
        chart.invalidate()
    }
}