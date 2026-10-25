/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  *
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android.helpers.storage

import android.content.Context
import android.util.Log
import com.mindful.android.R
import com.mindful.android.models.DecisionCard

/**
 * Repository for "decision cards": per-app guidance sheets shown when a mapped
 * app is launched. Cards are stored as a raw JSON array string in shared prefs
 * (same pattern as [PREF_KEY_WELLBEING_SETTINGS]) and seeded from the bundled
 * defaults in `res/raw/decision_cards_default.json` on first use.
 */
object DecisionCardsRepository {
    private const val TAG = "Mindful.DecisionCardsRepo"

    /**
     * Returns the decision card mapped to [packageName], or NULL if the feature
     * is switched off or no enabled card exists for the package.
     */
    fun getCardFor(context: Context, packageName: String): DecisionCard? {
        if (!SharedPrefsHelper.getSetDecisionCardsEnabled(context, null)) return null

        val json = SharedPrefsHelper.getSetDecisionCards(context, null)
            ?: seedAndGetDefaults(context)
            ?: return null

        return DecisionCard.parseCardsMap(json)[packageName]
    }

    /** Returns the stored cards json, seeding the bundled defaults on first use. */
    fun getCardsJson(context: Context): String {
        return SharedPrefsHelper.getSetDecisionCards(context, null)
            ?: seedAndGetDefaults(context)
            ?: "[]"
    }

    /** Returns the current state of the global feature switch. */
    fun getEnabled(context: Context): Boolean {
        return SharedPrefsHelper.getSetDecisionCardsEnabled(context, null)
    }

    /** Replaces all decision cards with the provided JSON array string. */
    fun updateCards(context: Context, jsonCards: String) {
        SharedPrefsHelper.getSetDecisionCards(context, jsonCards)
        Log.i(TAG, "updateCards: Decision cards updated (${jsonCards.length} chars)")
    }

    /** Turns the whole decision cards feature on or off. */
    fun setEnabled(context: Context, shouldEnable: Boolean) {
        SharedPrefsHelper.getSetDecisionCardsEnabled(context, shouldEnable)
        Log.i(TAG, "setEnabled: Decision cards feature => $shouldEnable")
    }

    /** Copies the bundled default cards into prefs once and returns their json. */
    private fun seedAndGetDefaults(context: Context): String? {
        val defaults = runCatching {
            context.resources.openRawResource(R.raw.decision_cards_default)
                .bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.e(TAG, "seedAndGetDefaults: Failed to read bundled decision cards", it)
            return null
        }

        SharedPrefsHelper.getSetDecisionCards(context, defaults)
        Log.i(TAG, "seedAndGetDefaults: Seeded bundled decision cards")
        return defaults
    }
}
