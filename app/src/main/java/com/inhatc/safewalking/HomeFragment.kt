package com.inhatc.safewalking

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var cardStatus: CardView
    private lateinit var textEvent: TextView
    private lateinit var textSubStatus: TextView
    private lateinit var textDurationDashboard: TextView
    private lateinit var textCountDashboard: TextView
    private lateinit var textLongestSafeWalk: TextView
    private lateinit var textTotalSafeWalk: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        cardStatus = view.findViewById(R.id.cardStatus)
        textEvent = view.findViewById(R.id.textEvent)
        textSubStatus = view.findViewById(R.id.textSubStatus)
        textDurationDashboard = view.findViewById(R.id.textDurationDashboard)
        textCountDashboard = view.findViewById(R.id.textCountDashboard)
        textLongestSafeWalk = view.findViewById(R.id.textLongestSafeWalk)
        textTotalSafeWalk = view.findViewById(R.id.textTotalSafeDuration)

        setupObservers()

        return view
    }

    private fun setupObservers() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        AppDatabase.getDatabase(requireContext())
            .smombieDao()
            .getTodaySafeDuration(today)
            .observe(viewLifecycleOwner) { duration ->
                textTotalSafeWalk.text = formatDuration(duration ?: 0L)
            }

        SafeWalkingService.liveUiState.observe(viewLifecycleOwner) { state ->
            state?.let { updateUI(it) }
        }
    }

    private fun updateUI(state: SafeWalkingService.Companion.UIState) {
        val durationSec = state.smombieDuration / 1000

        textDurationDashboard.text = "${durationSec}초"
        textCountDashboard.text = "${state.smombieCount}회"
        textLongestSafeWalk.text = formatDuration(state.longestSafeWalkingDuration)

        if (state.isSmombie) {
            textEvent.text = "스몸비 위험: ${state.riskLevel}"

            when (state.riskLevel) {
                "일반 사용" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#4CAF50"))
                    textSubStatus.text = "보행 중 스마트폰 사용을 감지했습니다."
                }

                "주의" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#FFC107"))
                    textSubStatus.text = "스마트폰을 계속 보면 위험할 수 있습니다."
                }

                "위험" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#FF9800"))
                    textSubStatus.text = "⚠️ 위험! 고개를 들고 전방을 확인하세요!"
                }

                "매우 위험" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#F44336"))
                    textSubStatus.text = "🚨 매우 위험! 안전사고 발생 확률이 높습니다!"
                }

                "야간 일반" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#3F51B5"))
                    textSubStatus.text = "🌙 야간 보행 중 스마트폰 사용 감지"
                }

                "야간 주의" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#303F9F"))
                    textSubStatus.text = "🌙 어두운 곳에서는 더 주의가 필요합니다."
                }

                "야간 위험" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#1A237E"))
                    textSubStatus.text = "🚨 야간 위험! 즉시 전방을 확인하세요."
                }

                "야간 매우 위험" -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#B71C1C"))
                    textSubStatus.text = "❌ 야간 매우 위험! 즉시 걸음을 멈추세요."
                }

                else -> {
                    cardStatus.setCardBackgroundColor(Color.parseColor("#F44336"))
                    textSubStatus.text = "위험 상태입니다. 전방을 확인하세요."
                }
            }
        } else {
            if (state.isWalking) {
                textEvent.text = "안전하게 보행 중"

                if (state.isDark) {
                    textSubStatus.text = "🌙 어두운 환경입니다. 전방을 주시하며 주의해서 걸으세요."
                    cardStatus.setCardBackgroundColor(Color.parseColor("#1C2A38"))
                } else {
                    textSubStatus.text = "정면을 바라보며 올바르게 걷고 있습니다."
                    cardStatus.setCardBackgroundColor(Color.parseColor("#2563EB"))
                }
            } else {
                textEvent.text = "정지 상태"

                if (state.isDark) {
                    textSubStatus.text = "🌙 주변이 어둡습니다. 이동 시 주의하세요."
                    cardStatus.setCardBackgroundColor(Color.parseColor("#374151"))
                } else {
                    textSubStatus.text = "안전한 구역에 멈춰 서 있습니다."
                    cardStatus.setCardBackgroundColor(Color.parseColor("#757575"))
                }
            }
        }
    }

    private fun formatDuration(duration: Long): String {
        val totalSec = (duration / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60

        return if (min > 0) {
            "${min}분 ${sec}초"
        } else {
            "${sec}초"
        }
    }
}