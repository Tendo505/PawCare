package com.example.pawcareai.dashboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.pawcareai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//record cards and edit/delete buttons shared by the pets and health screens

//4.output

//display records or the matching empty message with the same spacing
fun <T> LinearLayout.showRecords(
    records: List<T>,
    emptyTitle: String,
    emptyMessage: String,
    createCard: (T) -> View
)
{
    if (records.isEmpty())
    {
        addView(context.emptyCard(emptyTitle, emptyMessage).withTopMargin(10))
    }
    records.forEach { record ->
        addView(createCard(record).withTopMargin(10))
    }
}

//display each saved health record inside the health screen
fun Context.recordCard(
    title: String,
    subtitle: String,
    detail: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
): MaterialCardView
{
    val body = vertical(16)
    val titleView = label(title, 17, true, R.color.paw_text)
    titleView.id = R.id.recordTitle
    body.addView(titleView)
    val subtitleView = label(subtitle, 14, false, R.color.paw_primary)
    subtitleView.id = R.id.recordSubtitle
    body.addView(subtitleView.withTopMargin(4))
    val detailView = label(detail, 13, false, R.color.paw_muted)
    detailView.id = R.id.recordDetail
    detailView.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
    body.addView(detailView.withTopMargin(5))
    body.addView(recordActions(R.id.recordEdit, R.id.recordDelete, onEdit, onDelete).withTopMargin(8))
    val container = card(R.color.white)
    container.id = R.id.recordCard
    container.addView(body)
    return container
}

//keep edit and delete actions beside their record
fun Context.recordActions(editId: Int, deleteId: Int, onEdit: () -> Unit, onDelete: () -> Unit): LinearLayout
{
    val actions = LinearLayout(this)
    actions.gravity = Gravity.END
    val buttons = listOf(
        Triple(editId, getString(R.string.action_edit), onEdit),
        Triple(deleteId, getString(R.string.action_delete), onDelete)
    )
    buttons.forEach { (id, text, action) ->
        val button = MaterialButton(this, null, R.attr.materialButtonOutlinedStyle)
        button.id = id
        button.text = text
        button.textSize = 12f
        button.cornerRadius = dp(12)
        button.minHeight = 0
        button.insetTop = 0
        button.insetBottom = 0
        button.setOnClickListener { action() }
        val position = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
        position.leftMargin = dp(8)
        actions.addView(button, position)
    }
    return actions
}
