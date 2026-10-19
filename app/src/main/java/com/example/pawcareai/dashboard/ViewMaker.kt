package com.example.pawcareai.dashboard

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pawcareai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

//builds the cards, labels and buttons that are created in code instead of in xml
//every screen uses the same sizes and colours, so all cards look the same
class ViewMaker(private val context: Context)
{
    //1.screen setup

    //convert a size in dp to pixels for this phone's screen density
    fun dp(value: Int): Int
    {
        val density: Float = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    //read a colour from res/values/colors.xml
    fun color(colorResource: Int): Int
    {
        return ContextCompat.getColor(context, colorResource)
    }

    //4.output

    //create a vertical column, with the same padding on every side
    fun column(padding: Int): LinearLayout
    {
        val column: LinearLayout = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        if (padding > 0)
        {
            column.setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        }
        column.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return column
    }

    //create a text label with the app's font settings
    fun label(text: String, size: Int, bold: Boolean, colorResource: Int): TextView
    {
        val label: TextView = TextView(context)
        label.text = text
        label.textSize = size.toFloat()
        label.setTextColor(color(colorResource))
        if (bold)
        {
            label.setTypeface(label.typeface, Typeface.BOLD)
        }
        label.setLineSpacing(0f, 1.1f)
        return label
    }

    //create an empty rounded card
    fun card(backgroundColor: Int): MaterialCardView
    {
        val card: MaterialCardView = MaterialCardView(context)
        card.radius = dp(20).toFloat()
        card.cardElevation = dp(1).toFloat()
        card.setCardBackgroundColor(color(backgroundColor))
        card.strokeColor = color(R.color.paw_surface_variant)
        card.strokeWidth = dp(1)
        card.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return card
    }

    //create the card shown when a list is empty or something went wrong
    fun messageCard(title: String, message: String): MaterialCardView
    {
        val body: LinearLayout = column(18)
        val titleView: TextView = label(title, 16, true, R.color.paw_text)
        titleView.id = R.id.messageTitle
        body.addView(titleView)
        val messageView: TextView = label(message, 13, false, R.color.paw_muted)
        messageView.id = R.id.messageSubtitle
        addWithTopMargin(body, messageView, 4)

        val card: MaterialCardView = card(R.color.white)
        card.id = R.id.messageCard
        card.addView(body)
        return card
    }

    //create a reminder card: a title, a coloured date line and an optional detail line
    fun reminderCard(title: String, date: String, detail: String): MaterialCardView
    {
        val body: LinearLayout = column(15)
        body.addView(label(title, 16, true, R.color.paw_text))
        addWithTopMargin(body, label(date, 13, true, R.color.paw_primary), 4)
        if (detail.isNotBlank())
        {
            addWithTopMargin(body, label(detail, 13, false, R.color.paw_muted), 3)
        }

        val card: MaterialCardView = card(R.color.white)
        card.addView(body)
        return card
    }

    //create a full-width button
    fun wideButton(text: String): MaterialButton
    {
        val button: MaterialButton = MaterialButton(context)
        button.text = text
        button.cornerRadius = dp(14)
        button.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        return button
    }

    //add a view to a column with a gap above it; the view keeps its own width and height
    fun addWithTopMargin(parent: LinearLayout, child: View, topMargin: Int)
    {
        val oldParams: ViewGroup.LayoutParams? = child.layoutParams
        val newParams: LinearLayout.LayoutParams
        if (oldParams == null)
        {
            newParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        else if (oldParams is LinearLayout.LayoutParams)
        {
            newParams = oldParams
        }
        else
        {
            newParams = LinearLayout.LayoutParams(oldParams.width, oldParams.height)
        }
        newParams.topMargin = dp(topMargin)
        child.layoutParams = newParams
        parent.addView(child)
    }
}
