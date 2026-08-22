package com.example.twilightcalculator

import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter

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
        // Разрешаем перемещение/зум и по вертикали, и по горизонтали.
        chart.setScaleXEnabled(true)
        chart.setScaleYEnabled(true)
    }

    /**
     * Групповой столбиковый график: даты по X (подпись каждые 5 дней),
     * время ЧЧ:ММ по Y, легенда сверху.
     */
    fun styleBarGrouped(
        chart: BarChart,
        labels: List<Int>,
        timeFormatter: ValueFormatter
    ) {
        base(chart)
        chart.legend.isEnabled = true
        chart.legend.textColor = AXIS_TEXT
        chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        chart.legend.setDrawInside(false)
        chart.legend.xEntrySpace = 12f

        val x = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.textColor = AXIS_TEXT
        x.gridColor = GRID_COLOR
        x.setDrawGridLines(false)
        x.granularity = 1f
        x.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float, axis: AxisBase): String {
                val i = value.toInt()
                if (i < 0 || i >= labels.size) return ""
                // Подписываем число месяца разреженно, чтобы не слипалось.
                if (i == 0 || i % 5 == 0 || i == labels.size - 1) {
                    return labels[i].toString()
                }
                return ""
            }
        }

        val yl = chart.axisLeft
        yl.textColor = AXIS_TEXT
        yl.gridColor = GRID_COLOR
        yl.axisMinimum = 0f
        yl.axisMaximum = 24f
        // Без фиксированной гранулярности: при увеличении ось сама подбирает
        // более мелкий шаг времени (вплоть до получаса/часа).
        yl.setLabelCount(6, true)
        yl.valueFormatter = timeFormatter

        chart.axisRight.isEnabled = false
    }
}