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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

//shared views built in code with the existing dashboard styling

//4.output

//create a vertical group of views
fun Context.vertical(padding: Int = 0): LinearLayout
{
    val column = LinearLayout(this)
    column.orientation = LinearLayout.VERTICAL
    if (padding > 0) column.setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    column.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    return column
}

//create text with the existing font settings
fun Context.label(text: String, size: Int, bold: Boolean, colorRes: Int): TextView
{
    val textLabel = TextView(this)
    textLabel.text = text
    textLabel.textSize = size.toFloat()
    textLabel.setTextColor(color(colorRes))
    if (bold) textLabel.setTypeface(textLabel.typeface, Typeface.BOLD)
    textLabel.setLineSpacing(0f, 1.1f)
    return textLabel
}

//create a card with the existing colours and shape
fun Context.card(background: Int): MaterialCardView
{
    val container = MaterialCardView(this)
    container.radius = dp(20).toFloat()
    container.cardElevation = dp(1).toFloat()
    container.setCardBackgroundColor(color(background))
    container.strokeColor = color(R.color.paw_surface_variant)
    container.strokeWidth = dp(1)
    container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    return container
}

//display an empty or error message inside the current screen
fun Context.emptyCard(title: String, subtitle: String): MaterialCardView
{
    val body = vertical(18)
    val titleView = label(title, 16, true, R.color.paw_text)
    titleView.id = R.id.messageTitle
    body.addView(titleView)
    val subtitleView = label(subtitle, 13, false, R.color.paw_muted)
    subtitleView.id = R.id.messageSubtitle
    body.addView(subtitleView.withTopMargin(4))
    val container = card(R.color.white)
    container.id = R.id.messageCard
    container.addView(body)
    return container
}

//display the summary of a saved record
fun Context.infoCard(title: String, date: String, detail: String): MaterialCardView
{
    val content = vertical(15)
    content.addView(label(title, 16, true, R.color.paw_text))
    content.addView(label(date, 13, true, R.color.paw_primary).withTopMargin(4))
    if (detail.isNotBlank()) content.addView(label(detail, 13, false, R.color.paw_muted).withTopMargin(3))

    val container = card(R.color.white)
    container.addView(content)
    return container
}

//create a full-width action button
fun Context.actionButton(text: String, click: () -> Unit): MaterialButton
{
    val button = MaterialButton(this)
    button.text = text
    button.cornerRadius = dp(14)
    button.setOnClickListener { click() }
    button.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    return button
}

//set the top spacing while retaining the view size
fun View.withTopMargin(value: Int): View
{
    val currentLayout = layoutParams
        ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    val marginLayout = when (currentLayout)
    {
        is LinearLayout.LayoutParams -> currentLayout
        else -> LinearLayout.LayoutParams(currentLayout.width, currentLayout.height)
    }
    marginLayout.topMargin = context.dp(value)
    layoutParams = marginLayout
    return this
}

//resolve a colour resource
fun Context.color(resource: Int): Int = ContextCompat.getColor(this, resource)

//convert spacing for the screen density
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

//format a weight without unnecessary decimal places
fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

//format a saved date for display
fun Context.prettyDate(value: String): String
{
    if (value.isBlank()) return getString(R.string.date_not_set)
    return try
    {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    catch (_: DateTimeParseException)
    {
        value
    }
}
