package com.example.twilightcalculator

import android.graphics.Color
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis

/**
 * Единая светлая стилизация графиков MPAndroidChart в теме «сумерки».
 */
object ChartStyle {

    private const val GRID_COLOR = 0x223B46CF.toInt()
    private const val AXIS_TEXT = 0xFF5A66D6.toInt()

    /** Стандартные настройки для любой линейной/баровой диаграммы. */
    private fun base(chart: BarLineChartBase<*>) {
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.TRANSPARENT)
        chart.setNoDataText("—")
        chart.setNoDataTextColor(AXIS_TEXT)
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
    }

    fun styleLine(chart: LineChart) {
        base(chart)
        val legend = chart.legend
        legend.isEnabled = true
        legend.textColor = AXIS_TEXT
        legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        legend.setDrawInside(false)
        legend.xEntrySpace = 12f

        val x = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.textColor = AXIS_TEXT
        x.gridColor = GRID_COLOR
        x.setDrawGridLines(true)
        x.axisMinimum = 0f
        x.axisMaximum = 24f
        x.granularity = 1f

        val yl = chart.axisLeft
        yl.textColor = AXIS_TEXT
        yl.gridColor = GRID_COLOR
        yl.granularity = 6f

        chart.axisRight.isEnabled = false
    }
}