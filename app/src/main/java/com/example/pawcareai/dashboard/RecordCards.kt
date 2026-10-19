package com.example.pawcareai.dashboard

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pawcareai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//builds the cards used for pets and health records, with Edit and Delete buttons
class RecordCards(private val page: DashboardPage)
{
    private val views: ViewMaker = page.views

    //4.output

    //a health record card: title, coloured subtitle, optional detail line and the two buttons
    fun recordCard(title: String, subtitle: String, detail: String, onEdit: () -> Unit, onDelete: () -> Unit): MaterialCardView
    {
        val body: LinearLayout = views.column(16)
        val titleView: TextView = views.label(title, 17, true, R.color.paw_text)
        titleView.id = R.id.recordTitle
        body.addView(titleView)

        val subtitleView: TextView = views.label(subtitle, 14, false, R.color.paw_primary)
        subtitleView.id = R.id.recordSubtitle
        views.addWithTopMargin(body, subtitleView, 4)

        val detailView: TextView = views.label(detail, 13, false, R.color.paw_muted)
        detailView.id = R.id.recordDetail
        if (detail.isBlank())
        {
            detailView.visibility = View.GONE
        }
        views.addWithTopMargin(body, detailView, 5)

        val buttons: LinearLayout = editDeleteButtons(R.id.recordEdit, R.id.recordDelete, onEdit, onDelete)
        views.addWithTopMargin(body, buttons, 8)

        val card: MaterialCardView = views.card(R.color.white)
        card.id = R.id.recordCard
        card.addView(body)
        return card
    }

    //the Edit and Delete buttons on the right of a card
    //onEdit and onDelete are what should happen when each button is tapped
    fun editDeleteButtons(editId: Int, deleteId: Int, onEdit: () -> Unit, onDelete: () -> Unit): LinearLayout
    {
        val row: LinearLayout = LinearLayout(page.dashboard)
        row.gravity = Gravity.END

        val editButton: MaterialButton = smallButton(editId, page.dashboard.getString(R.string.action_edit))
        editButton.setOnClickListener {
            onEdit()
        }
        row.addView(editButton, buttonPosition())

        val deleteButton: MaterialButton = smallButton(deleteId, page.dashboard.getString(R.string.action_delete))
        deleteButton.setOnClickListener {
            onDelete()
        }
        row.addView(deleteButton, buttonPosition())
        return row
    }

    //a small outlined button
    private fun smallButton(id: Int, text: String): MaterialButton
    {
        val button: MaterialButton = MaterialButton(page.dashboard, null, R.attr.materialButtonOutlinedStyle)
        button.id = id
        button.text = text
        button.textSize = 12f
        button.cornerRadius = views.dp(12)
        button.minHeight = 0
        button.insetTop = 0
        button.insetBottom = 0
        return button
    }

    //each button is 42dp tall with a small gap on its left
    private fun buttonPosition(): LinearLayout.LayoutParams
    {
        val position: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, views.dp(42))
        position.leftMargin = views.dp(8)
        return position
    }
}
