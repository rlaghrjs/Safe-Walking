package com.inhatc.safewalking

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

class ReportFragment : Fragment() {

    private lateinit var barChart: BarChart
    private lateinit var spinnerFilter: Spinner
    private lateinit var textSelectedDate: TextView
    private lateinit var textSelectedDuration: TextView
    private lateinit var textSelectedCount: TextView

    private lateinit var recyclerViewSessions: RecyclerView

    private lateinit var sessionAdapter: SessionAdapter

    private lateinit var db: AppDatabase
    private var currentRecords: List<SmombieRecord> = ArrayList()
    private val dateLabels = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_report, container, false)

        barChart = view.findViewById(R.id.barChart)
        spinnerFilter = view.findViewById(R.id.spinnerFilter)
        textSelectedDate = view.findViewById(R.id.textSelectedDate)
        textSelectedDuration = view.findViewById(R.id.textSelectedDuration)
        textSelectedCount = view.findViewById(R.id.textSelectedCount)
        recyclerViewSessions = view.findViewById(R.id.recyclerViewSessions)

        sessionAdapter = SessionAdapter(ArrayList())
        recyclerViewSessions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        recyclerViewSessions.adapter = sessionAdapter

        db = AppDatabase.getDatabase(requireContext().applicationContext)

        db.smombieDao().getDailySummaries().observe(viewLifecycleOwner) { records ->
            handleIncomingRecords(records, "일별")
        }

        setupSpinner()
        setupChartInteraction()

        return view
    }

    private fun setupSpinner() {
        val options = arrayOf("일별", "주간", "월간")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)

        // 🛠️ [해결] 시스템 내장 드롭다운 아이템 레이아웃 경로 수정
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> observeDaily()
                    1 -> observeWeekly()
                    2 -> observeMonthly()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun observeDaily() {
        db.smombieDao().getRecent7DaysRecords().removeObservers(viewLifecycleOwner)
        db.smombieDao().getRecent7DaysRecords().observe(viewLifecycleOwner) { records ->
            handleIncomingRecords(records, "일별")
        }
    }

    private fun observeWeekly() {
        db.smombieDao().getWeeklyRecords().removeObservers(viewLifecycleOwner)
        db.smombieDao().getWeeklyRecords().observe(viewLifecycleOwner) { records ->
            handleIncomingRecords(records, "주간")
        }
    }

    private fun observeMonthly() {
        db.smombieDao().getMonthlyRecords().removeObservers(viewLifecycleOwner)
        db.smombieDao().getMonthlyRecords().observe(viewLifecycleOwner) { records ->
            handleIncomingRecords(records, "월간")
        }
    }

    private fun handleIncomingRecords(records: List<SmombieRecord>?, filterType: String) {
        if (!records.isNullOrEmpty()) {
            currentRecords = records.sortedBy { it.date }
            setupAndDrawChart(currentRecords, filterType)
            updateBottomSummary(currentRecords.last())
        } else {
            barChart.clear()
            textSelectedDate.text = "데이터 없음"
            textSelectedDuration.text = "0초"
            textSelectedCount.text = "총 경고 0회"
        }
    }

    private fun setupAndDrawChart(records: List<SmombieRecord>, filterType: String) {
        val entries = ArrayList<BarEntry>()
        dateLabels.clear()

        records.forEachIndexed { index, record ->
            entries.add(BarEntry(index.toFloat(), record.totalWarningCount.toFloat()))

            val formattedLabel = when (filterType) {
                "일별" -> try { record.date.substring(5).replace("-", "/") } catch (e: Exception) { record.date }
                "주간" -> record.date.replace("-W", "년 ") + "주차"
                "월간" -> try { record.date.substring(2).replace("-", "년 ") + "월" } catch (e: Exception) { record.date }
                else -> record.date
            }
            dateLabels.add(formattedLabel)
        }

        val barDataSet = BarDataSet(entries, "스몸비 경고 횟수").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}회"
            }
        }

        barChart.data = BarData(barDataSet).apply { barWidth = 0.4f }

        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            animateY(800)
        }

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index in dateLabels.indices) dateLabels[index] else ""
                }
            }
        }

        barChart.axisRight.isEnabled = false
        barChart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f
            setDrawGridLines(true)
        }

        barChart.invalidate()
    }

    private fun setupChartInteraction() {
        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                e?.let {
                    val index = it.x.toInt()
                    if (index in currentRecords.indices) {
                        val selectedRecord = currentRecords[index]
                        updateBottomSummary(selectedRecord)

                        val targetDate = selectedRecord.date

                        val summaryText = view?.findViewById<TextView>(R.id.textStatusSummary)

                        if (targetDate.contains("-W") || targetDate.length == 7) {
                            sessionAdapter.updateData(emptyList())
                            summaryText?.text = "일별 그래프에서만 상세 세션을 확인할 수 있습니다"
                            return
                        }

                        Thread {
                            val daySessions = db.smombieDao().getSessionsByDate(targetDate)

                            activity?.runOnUiThread {
                                sessionAdapter.updateData(daySessions)
                                summaryText?.text = "총 ${daySessions.size}개의 위험 세션 감지"
                            }
                        }.start()
                    }
                }
            }

            override fun onNothingSelected() {}
        })
    }

    private fun updateBottomSummary(record: SmombieRecord) {
        // 🛠️ [해결] Long 타입을 명확히 Int 초 단위로 캐스팅 후 연산 수행
        val totalSec = (record.totalDangerDuration / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60

        textSelectedDate.text = "${record.date} 기준 요약"

        // 올바르게 텍스트 바인딩 수행
        if (min > 0) {
            textSelectedDuration.text = "${min}분 ${sec}초"
        } else {
            textSelectedDuration.text = "${sec}초"
        }

        textSelectedCount.text = "총 경고 ${record.totalWarningCount}회"
    }
}