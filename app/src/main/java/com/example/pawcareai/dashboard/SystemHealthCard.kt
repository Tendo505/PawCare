package com.example.pawcareai.dashboard

import android.view.View
import android.widget.TextView
import com.example.pawcareai.R
import com.google.android.material.button.MaterialButton

//the connection card on the home tab
class SystemHealthCard(
    private val page: DashboardPage,
    private val onChecked: () -> Unit
)
{
    private val context = page.activity

    //3.process

    //update the connection status shown on the home screen
    fun refresh()
    {
        page.repository.checkSystemHealth {
            if (page.isShowing(R.id.nav_home))
            {
                onChecked()
            }
        }
    }

    //4.output

    //fill the connection card from the latest service check
    fun bind(root: View)
    {
        val health = page.repository.systemHealth()
        root.findViewById<TextView>(R.id.connectionTitle).text = context.getString(when (health?.overall)
        {
            "ok" -> R.string.health_all_connected
            "degraded" -> R.string.health_degraded
            "unavailable" -> R.string.health_unavailable
            else -> R.string.health_checking
        })
        root.findViewById<TextView>(R.id.connectionLaravel).text = context.getString(R.string.health_laravel, serviceLabel(health?.laravel))
        root.findViewById<TextView>(R.id.connectionDatabase).text = context.getString(R.string.health_database, serviceLabel(health?.database))
        root.findViewById<TextView>(R.id.connectionAi).text = context.getString(R.string.health_ai, serviceLabel(health?.ai))
        val model = root.findViewById<TextView>(R.id.connectionModel)
        model.visibility = if (health?.aiModelVersion.isNullOrBlank()) View.GONE else View.VISIBLE
        model.text = context.getString(R.string.health_model, health?.aiModelVersion)
        root.findViewById<MaterialButton>(R.id.connectionRetry).setOnClickListener { refresh() }
    }

    //display a readable name for each service status
    private fun serviceLabel(status: String?): String
    {
        return context.getString(when (status)
        {
            "ok", "ready" -> R.string.service_connected
            "model_missing" -> R.string.service_model_missing
            "unavailable" -> R.string.service_unavailable
            "unknown" -> R.string.service_not_confirmed
            else -> R.string.service_checking
        })
    }
}
