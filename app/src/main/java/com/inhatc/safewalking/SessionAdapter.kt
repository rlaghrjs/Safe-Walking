package com.inhatc.safewalking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(private var sessions: List<SmombieSession>) :
    RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTime: TextView = view.findViewById(R.id.textSessionTime)
        val textDuration: TextView = view.findViewById(R.id.textSessionDuration)
        val textWarning: TextView = view.findViewById(R.id.textSessionWarning)
        val textRisk: TextView = view.findViewById(R.id.textSessionRisk)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = sessions[position]
        holder.textTime.text = item.startTime

        val sec = item.duration / 1000
        holder.textDuration.text = "지속시간: ${sec}초"
        holder.textWarning.text = "경고 횟수: ${item.warningCount}회"
        holder.textRisk.text = item.riskLevel

        // 위험도에 따른 색상 차별화
        when(item.riskLevel) {
            "위험", "매우 위험" -> holder.textRisk.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
            "주의" -> holder.textRisk.setBackgroundColor(android.graphics.Color.parseColor("#FFB300"))
            else -> holder.textRisk.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateData(newSessions: List<SmombieSession>) {
        this.sessions = newSessions
        notifyDataSetChanged()
    }
}