package com.example.twilightcalculator

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Заполняет график высоты Солнца и Луны астрономическими расчётами.
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
            val time = LocalTime.ofSecondOfDay(minute * 60L)
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

        // Уровни сумерек и ночи (цвета под светлую тему).
        val axisColor = ContextCompat.getColor(context, R.color.text_secondary)
        val civilColor = ContextCompat.getColor(context, R.color.tw_civil)
        val nautColor = ContextCompat.getColor(context, R.color.tw_nautical)
        val astroColor = ContextCompat.getColor(context, R.color.tw_astro)
        addLimit(chart, "0°", 0f, axisColor)
        addLimit(chart, "−6°", -6f, civilColor)
        addLimit(chart, "−12°", -12f, nautColor)
        addLimit(chart, "−18°", -18f, astroColor)

        chart.invalidate()
    }

    private fun addLimit(chart: LineChart, label: String, value: Float, color: Int) {
        val ll = LimitLine(value, label)
        ll.lineColor = color
        ll.lineWidth = 1f
        ll.enableDashedLine(8f, 8f, 0f)
        ll.textColor = color
        ll.textSize = 10f
        ll.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        chart.axisLeft.addLimitLine(ll)
    }
}