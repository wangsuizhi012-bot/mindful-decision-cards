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
package com.mindful.android.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single guided route (branch) inside a [DecisionCard], e.g. "save money" vs "save time".
 *
 * @param label   Short label of the route shown on the option button.
 * @param sub     One-line hint about what this route trades (money/time/attention).
 * @param steps   Ordered list of steps played one by one.
 * @param doneMsg Message shown when all steps are finished.
 * @param doneSub Supporting line shown under [doneMsg].
 */
class DecisionRoute(
    val label: String,
    val sub: String,
    val steps: List<String>,
    val doneMsg: String,
    val doneSub: String,
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): DecisionRoute {
            val stepsJson = jsonObject.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<String>(stepsJson.length())
            for (i in 0 until stepsJson.length()) {
                val step = stepsJson.optString(i, "")
                if (step.isNotEmpty()) steps.add(step)
            }

            return DecisionRoute(
                label = jsonObject.optString("label", ""),
                sub = jsonObject.optString("sub", ""),
                steps = steps,
                doneMsg = jsonObject.optString("doneMsg", ""),
                doneSub = jsonObject.optString("doneSub", ""),
            )
        }
    }
}

/**
 * A "decision card" shown as an overlay sheet when a mapped app is launched.
 * Unlike the blocking sheet, it does not forbid anything: it walks the user
 * through an intention or a playbook before they continue into the app.
 *
 * Expected JSON shape:
 * ```json
 * {
 *   "appPackage": "com.example.app",
 *   "enabled": true,
 *   "title": "Before opening ...",
 *   "question": "What do you care about this time?",
 *   "routes": [ { "label": "...", "sub": "...", "steps": ["..."], "doneMsg": "...", "doneSub": "..." } ]
 * }
 * ```
 *
 * @param appPackage Package name of the app this card is attached to.
 * @param title      Card headline, shown under the app name.
 * @param question   Question the user answers by picking a route.
 * @param routes     Branches the user can choose from.
 * @param enabled    If FALSE the card is ignored at launch time.
 */
class DecisionCard(
    val appPackage: String,
    val title: String,
    val question: String,
    val routes: List<DecisionRoute>,
    val enabled: Boolean,
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): DecisionCard {
            val routesJson = jsonObject.optJSONArray("routes") ?: JSONArray()
            val routes = ArrayList<DecisionRoute>(routesJson.length())
            for (i in 0 until routesJson.length()) {
                routes.add(DecisionRoute.fromJson(routesJson.getJSONObject(i)))
            }

            return DecisionCard(
                appPackage = jsonObject.optString("appPackage", ""),
                title = jsonObject.optString("title", ""),
                question = jsonObject.optString("question", ""),
                routes = routes,
                enabled = jsonObject.optBoolean("enabled", true),
            )
        }

        /**
         * Parses a JSON array string of decision cards into a map keyed by app package.
         * Disabled cards, cards without package or without usable routes are dropped.
         */
        fun parseCardsMap(json: String?): Map<String, DecisionCard> {
            if (json.isNullOrEmpty()) return emptyMap()
            return runCatching {
                val array = JSONArray(json)
                val cards = HashMap<String, DecisionCard>(array.length())
                for (i in 0 until array.length()) {
                    val card = fromJson(array.getJSONObject(i))
                    if (card.enabled && card.appPackage.isNotEmpty() && card.routes.isNotEmpty()) {
                        cards[card.appPackage] = card
                    }
                }
                cards
            }.getOrElse { emptyMap() }
        }
    }
}
