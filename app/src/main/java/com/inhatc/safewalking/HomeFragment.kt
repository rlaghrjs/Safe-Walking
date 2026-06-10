package com.inhatc.safewalking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var cardStatus: CardView
    private lateinit var textEvent: TextView
    private lateinit var textSubStatus: TextView
    private lateinit var textDurationDashboard: TextView
    private lateinit var textCountDashboard: TextView
    private lateinit var textLongestSafeWalk: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // ⭐️ [누락 수정] xml 컴포넌트와 객체 간 맵핑 연동 완료
        cardStatus = view.findViewById(R.id.cardStatus)
        textEvent = view.findViewById(R.id.textEvent)
        textSubStatus = view.findViewById(R.id.textSubStatus)
        textDurationDashboard = view.findViewById(R.id.textDurationDashboard)
        textCountDashboard = view.findViewById(R.id.textCountDashboard)
        textLongestSafeWalk = view.findViewById(R.id.textLongestSafeWalk)


        // 서비스의 실시간 LiveData 관찰 시작
        SafeWalkingService.liveUiState.observe(viewLifecycleOwner) { state ->
            state?.let { updateUI(it) }
        }

        return view
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
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                    textSubStatus.text = "보행 중 스마트폰 사용을 감지했습니다."
                }
                "주의" -> {
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#FFC107"))
                    textSubStatus.text = "스마트폰을 계속 보면 위험할 수 있습니다."
                }
                "위험" -> {
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                    textSubStatus.text = "⚠️ 위험! 고개를 들고 전방을 확인하세요!"
                }
                else -> {
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                    textSubStatus.text = "🚨 매우 위험! 안전사고 발생 확률이 높습니다!"
                }
            }
        } else {
            if (state.isWalking) {
                textEvent.text = "안전하게 보행 중"
                textSubStatus.text = "정면을 바라보며 올바르게 걷고 있습니다."
                cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            } else {
                textEvent.text = "정지 상태"
                textSubStatus.text = "안전한 구역에 멈춰 서 있습니다."
                cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#757575"))
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