package com.example.pawcareai.dashboard

import android.content.Context
import com.example.pawcareai.R
import com.example.pawcareai.data.BreedPrediction
import com.google.android.material.card.MaterialCardView

//cards for the latest scan result and the saved scan history

//4.output

//create the latest breed match and alternative predictions
fun Context.predictionResultCard(result: BreedPrediction): MaterialCardView
{
    val resultBody = vertical(18)
    resultBody.addView(label(getString(R.string.ai_best_match), 13, true, R.color.paw_primary))
    resultBody.addView(label(result.breed.replace('_', ' '), 25, true, R.color.paw_text).withTopMargin(4))

    val speciesName: String = result.species.replaceFirstChar { character -> character.uppercase() }
    //model probability x 100 for display
    val confidencePercent: Int = (result.confidence * 100).toInt()
    resultBody.addView(label(getString(R.string.ai_confidence, speciesName, confidencePercent), 15, false, R.color.paw_muted).withTopMargin(4))
    if (result.topPredictions.size > 1)
    {
        resultBody.addView(label(getString(R.string.ai_other_possibilities), 14, true, R.color.paw_text).withTopMargin(14))
        result.topPredictions.drop(1).take(2).forEach { prediction ->
            val predictionText: String = getString(
                R.string.ai_other_option,
                prediction.breed.replace('_', ' '),
                (prediction.confidence * 100).toInt()
            )
            resultBody.addView(label(predictionText, 14, false, R.color.paw_muted).withTopMargin(5))
        }
    }
    if (result.petName.isNotBlank())
    {
        resultBody.addView(label(getString(R.string.ai_saved_for, result.petName), 13, true, R.color.paw_primary).withTopMargin(12))
    }

    val resultCard: MaterialCardView = card(R.color.white)
    resultCard.strokeColor = color(R.color.paw_primary)
    resultCard.strokeWidth = dp(1)
    resultCard.addView(resultBody)
    return resultCard
}

//create a saved prediction history card
fun Context.predictionCard(prediction: BreedPrediction): MaterialCardView
{
    val predictionBody = vertical(16)
    predictionBody.addView(label(prediction.breed.replace('_', ' '), 18, true, R.color.paw_text))
    val speciesName: String = prediction.species.replaceFirstChar { character -> character.uppercase() }
    //model probability x 100 for display
    val confidencePercent: Int = (prediction.confidence * 100).toInt()
    predictionBody.addView(label(getString(R.string.ai_confidence, speciesName, confidencePercent), 14, false, R.color.paw_primary).withTopMargin(4))

    val details = mutableListOf<String>()
    if (prediction.petName.isNotBlank())
    {
        details += prediction.petName
    }
    prediction.createdAt.substringBefore('T')
        .takeIf { date -> date.isNotBlank() }
        ?.let { date -> details += prettyDate(date) }
    if (details.isNotEmpty())
    {
        predictionBody.addView(label(details.joinToString(" · "), 13, false, R.color.paw_muted).withTopMargin(6))
    }

    val historyCard: MaterialCardView = card(R.color.white)
    historyCard.addView(predictionBody)
    return historyCard
}
