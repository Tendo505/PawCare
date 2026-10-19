package com.example.pawcareai.dashboard

import android.widget.LinearLayout
import com.example.pawcareai.R
import com.example.pawcareai.data.BreedPrediction
import com.example.pawcareai.data.BreedPredictionOption
import com.google.android.material.card.MaterialCardView

//the cards on the AI scan screen: the latest result and the saved scan history
class PredictionCards(private val page: DashboardPage)
{
    private val views: ViewMaker = page.views

    //4.output

    //the big result card: best match, confidence, two other possibilities and the linked pet
    fun resultCard(result: BreedPrediction): MaterialCardView
    {
        val body: LinearLayout = views.column(18)
        body.addView(views.label(page.dashboard.getString(R.string.ai_best_match), 13, true, R.color.paw_primary))
        views.addWithTopMargin(body, views.label(TextFormat.breedName(result.breed), 25, true, R.color.paw_text), 4)
        views.addWithTopMargin(body, views.label(confidenceText(result), 15, false, R.color.paw_muted), 4)

        //the first item in topPredictions is the best match, so the next two are the alternatives
        if (result.topPredictions.size > 1)
        {
            val heading: String = page.dashboard.getString(R.string.ai_other_possibilities)
            views.addWithTopMargin(body, views.label(heading, 14, true, R.color.paw_text), 14)
            var index: Int = 1
            while (index < result.topPredictions.size && index <= 2)
            {
                val option: BreedPredictionOption = result.topPredictions[index]
                val optionText: String = page.dashboard.getString(R.string.ai_other_option, TextFormat.breedName(option.breed), TextFormat.percent(option.confidence))
                views.addWithTopMargin(body, views.label(optionText, 14, false, R.color.paw_muted), 5)
                index = index + 1
            }
        }

        if (result.petName.isNotBlank())
        {
            val savedFor: String = page.dashboard.getString(R.string.ai_saved_for, result.petName)
            views.addWithTopMargin(body, views.label(savedFor, 13, true, R.color.paw_primary), 12)
        }

        //the result card has a green border so it stands out from the history
        val card: MaterialCardView = views.card(R.color.white)
        card.strokeColor = views.color(R.color.paw_primary)
        card.strokeWidth = views.dp(1)
        card.addView(body)
        return card
    }

    //a smaller card for one saved scan: breed, confidence, then pet name and date
    fun historyCard(prediction: BreedPrediction): MaterialCardView
    {
        val body: LinearLayout = views.column(16)
        body.addView(views.label(TextFormat.breedName(prediction.breed), 18, true, R.color.paw_text))
        views.addWithTopMargin(body, views.label(confidenceText(prediction), 14, false, R.color.paw_primary), 4)

        val details: MutableList<String> = mutableListOf()
        if (prediction.petName.isNotBlank())
        {
            details.add(prediction.petName)
        }
        //createdAt looks like "2026-10-19T08:30:00Z"; only the date part is shown
        val date: String = prediction.createdAt.substringBefore('T')
        if (date.isNotBlank())
        {
            details.add(TextFormat.prettyDate(page.dashboard, date))
        }
        if (details.isNotEmpty())
        {
            views.addWithTopMargin(body, views.label(details.joinToString(" · "), 13, false, R.color.paw_muted), 6)
        }

        val card: MaterialCardView = views.card(R.color.white)
        card.addView(body)
        return card
    }

    //"Cat · 95% confidence"
    private fun confidenceText(prediction: BreedPrediction): String
    {
        val species: String = TextFormat.capitalise(prediction.species)
        return page.dashboard.getString(R.string.ai_confidence, species, TextFormat.percent(prediction.confidence))
    }
}
