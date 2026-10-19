package com.example.pawcareai.dashboard

import android.content.Context
import com.example.pawcareai.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

//turns saved values into the text shown on screen
object TextFormat
{
    //4.output

    //"2026-11-03" becomes "3 Nov 2026"; an empty date becomes "Not set"
    fun prettyDate(context: Context, date: String): String
    {
        if (date.isBlank())
        {
            return context.getString(R.string.date_not_set)
        }
        try
        {
            val parsedDate: LocalDate = LocalDate.parse(date)
            return parsedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        catch (error: DateTimeParseException)
        {
            //not a valid date, so show it exactly as it was saved
            return date
        }
    }

    //4.0 becomes "4" and 4.25 becomes "4.3"
    fun weight(weightKg: Double): String
    {
        if (weightKg % 1.0 == 0.0)
        {
            return weightKg.toInt().toString()
        }
        return String.format(Locale.US, "%.1f", weightKg)
    }

    //the model gives 0.95; the screen shows 95 (the percent sign is in strings.xml)
    fun percent(confidence: Double): Int
    {
        return (confidence * 100).toInt()
    }

    //"Golden_Retriever" becomes "Golden Retriever"
    fun breedName(breed: String): String
    {
        return breed.replace('_', ' ')
    }

    //"cat" becomes "Cat"
    fun capitalise(text: String): String
    {
        if (text.isEmpty())
        {
            return text
        }
        return text.substring(0, 1).uppercase() + text.substring(1)
    }
}
