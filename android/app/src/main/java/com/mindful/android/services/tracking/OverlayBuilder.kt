package com.mindful.android.services.tracking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.mindful.android.R
import com.mindful.android.enums.RestrictionType
import com.mindful.android.models.DecisionCard
import com.mindful.android.models.DecisionRoute
import com.mindful.android.models.RestrictionState
import com.mindful.android.utils.AppUtils
import com.mindful.android.utils.DateTimeUtils
import com.mindful.android.utils.MindfulQuotes
import com.mindful.android.utils.ThreadUtils

object OverlayBuilder {
    @MainThread
    fun buildToastOverlay(
        context: Context,
        packageName: String,
        screenTimeUsedInMins: Int,
    ): View {
        // Inflate the custom layout for the dialog
        val inflater = LayoutInflater.from(context)
        val toastView = inflater.inflate(R.layout.overlay_toast_layout, null)

        // Resolve app icon and label
        val (appName, appIcon) = getAppLabelAndIcon(context, packageName)

        // App infos
        val appNameTxt = toastView.findViewById<TextView>(R.id.overlay_toast_app_name)
        val appIconImg = toastView.findViewById<ImageView>(R.id.overlay_toast_app_icon)
        appNameTxt.text = appName
        appIconImg.setImageDrawable(appIcon)

        // limit spent text
        val limitSpentTxt = toastView.findViewById<TextView>(R.id.overlay_toast_screen_time)
        limitSpentTxt.text = context.getString(
            R.string.app_screen_time_usage_info,
            DateTimeUtils.minutesToTimeStr(screenTimeUsedInMins)
        )

        // Set initial state
        toastView.alpha = 0f

        // Set click listener
        toastView.setOnClickListener {
            // Open mindful
            context.applicationContext.startActivity(
                AppUtils.getIntentForMindfulUri(
                    context,
                    "com.mindful.android://open/appDashboard?package=$packageName"
                )
            )
        }

        return toastView
    }

    @MainThread
    fun buildFullScreenOverlay(
        context: Context,
        packageName: String,
        state: RestrictionState,
        dismissOverlay: () -> Unit,
        addReminderDelay: ((futureMinutes: Int) -> Unit)? = null,
    ): View {
        // Inflate the custom layout for the dialog
        val inflater = LayoutInflater.from(context)
        val sheetView = inflater.inflate(R.layout.overlay_full_screen_layout, null)

        // Set quote and author
        val quoteTxt = sheetView.findViewById<TextView>(R.id.overlay_sheet_quote)
        val quoteAuthorTxt = sheetView.findViewById<TextView>(R.id.overlay_sheet_quote_author)
        val randomQuote = MindfulQuotes.getRandomQuote()
        quoteTxt.text = buildString {
            append("\"")
            append(randomQuote.value)
            append("\"")
        }
        quoteAuthorTxt.text = buildString {
            append("— ")
            append(randomQuote.key)
        }

        // Resolve app icon and label
        val (appName, appIcon) = getAppLabelAndIcon(context, packageName)

        // App infos
        val appNameTxt = sheetView.findViewById<TextView>(R.id.overlay_sheet_app_name)
        val appIconImg = sheetView.findViewById<ImageView>(R.id.overlay_sheet_app_icon)
        appNameTxt.text = appName
        appIconImg.setImageDrawable(appIcon)

        // Emergency button (Visible only if limit is exhausted)
        if (state.screenTimeUsed >= state.screenTimeLimit) {
            val emergencyBtn = sheetView.findViewById<Button>(R.id.overlay_sheet_btn_emergency)
            emergencyBtn.visibility = View.VISIBLE
            emergencyBtn.setOnClickListener {
                ThreadUtils.runOnMainThread {
                    /// Open mindful
                    context.applicationContext.startActivity(
                        AppUtils.getIntentForMindfulUri(
                            context,
                            "com.mindful.android://open/appDashboard?package=$packageName"
                        )
                    )

                    /// Remove overlay
                    dismissOverlay.invoke()
                }
            }
        }

        // Limit type
        val limitType = sheetView.findViewById<TextView>(R.id.overlay_sheet_limit_type)
        limitType.text = context.getString(
            when (state.type) {
                RestrictionType.FOCUS -> R.string.app_paused_restriction_focus_mode
                RestrictionType.BEDTIME -> R.string.app_paused_restriction_bedtime_mode
                RestrictionType.LAUNCH_COUNT -> R.string.app_paused_restriction_launch_count
                RestrictionType.APP_TIMER -> R.string.app_paused_restriction_app_timer
                RestrictionType.APP_ACTIVE_PERIOD -> R.string.app_paused_restriction_app_active_period
                RestrictionType.GROUP_TIMER -> R.string.app_paused_restriction_group_timer
                RestrictionType.GROUP_ACTIVE_PERIOD -> R.string.app_paused_restriction_group_active_period
            }
        )

        // Limit information
        val limitInfo = sheetView.findViewById<TextView>(R.id.overlay_sheet_limit_info)
        limitInfo.text = getRestrictionInfo(context, state)

        // Limit progress, and use more layout
        if (state.screenTimeLimit > 0 && state.screenTimeUsed > 0) {
            // Make limit parent container visible
            val limitContainer =
                sheetView.findViewById<LinearLayout>(R.id.overlay_sheet_limit_container)
            limitContainer.visibility = View.VISIBLE

            // calculate limit in minutes
            val usedLimitMins = (state.screenTimeUsed / 60).toInt()
            val totalLimitMins = (state.screenTimeLimit / 60).toInt()
            val leftLimitMins = if (usedLimitMins < totalLimitMins) totalLimitMins - usedLimitMins
            else 0

            // Progress bar
            val progressBar = sheetView.findViewById<ProgressBar>(R.id.overlay_sheet_limit_progress)
            progressBar.max = state.screenTimeLimit.toInt()
            progressBar.setProgress(state.screenTimeUsed.toInt(), true)

            // limit spent text
            val limitSpentTxt = sheetView.findViewById<TextView>(R.id.overlay_sheet_limit_spent)
            limitSpentTxt.text = DateTimeUtils.minutesToTimeStr(usedLimitMins)

            // limit left text
            val limitLeftTxt = sheetView.findViewById<TextView>(R.id.overlay_sheet_limit_left)
            limitLeftTxt.text = DateTimeUtils.minutesToTimeStr(leftLimitMins)

            // Wish to use more? options layout
            if (leftLimitMins > 0) {
                val useMoreOptions =
                    sheetView.findViewById<LinearLayout>(R.id.overlay_sheet_limit_options_use_more)
                useMoreOptions.visibility = View.VISIBLE

                // Iterate over reminders and the button ids map and set click listener
                // and make them visible if the left limit is more than the specified reminder time
                addReminderDelay?.let { callback ->
                    mapOf(
                        2 to R.id.overlay_sheet_reminder_option_btn_two_mins,
                        5 to R.id.overlay_sheet_reminder_option_btn_five_mins,
                        10 to R.id.overlay_sheet_reminder_option_btn_ten_mins,
                        20 to R.id.overlay_sheet_reminder_option_btn_twenty_mins
                    ).forEach { (reminder, btnId) ->
                        // Always show 2 minute option if left minutes > 0
                        if (reminder == 2 || leftLimitMins >= reminder) {
                            val button = sheetView.findViewById<Button>(btnId)
                            button.visibility = View.VISIBLE
                            button.setOnClickListener {
                                callback(reminder)
                                dismissOverlay.invoke()
                            }
                        }
                    }
                }
            }
        }

        // Close app button
        val closeAppBtn = sheetView.findViewById<Button>(R.id.overlay_sheet_btn_close_app)
        closeAppBtn.text = context.getString(R.string.app_paused_overlay_button_close_app, appName)
        closeAppBtn.setOnClickListener {
            ThreadUtils.runOnMainThread {
                /// Go to home
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.applicationContext.startActivity(homeIntent)

                /// Remove overlay
                dismissOverlay.invoke()
            }
        }

        return sheetView
    }


    /**
     * Builds the "decision card" overlay: the user picks one of the card's routes
     * and walks through its steps one by one before continuing into the app.
     * Nothing is blocked — [dismissOverlay] simply reveals the app underneath.
     */
    @MainThread
    fun buildDecisionCardOverlay(
        context: Context,
        packageName: String,
        card: DecisionCard,
        dismissOverlay: () -> Unit,
    ): View {
        val sheetView = LayoutInflater.from(context).inflate(R.layout.overlay_decision_card_layout, null)

        // Resolve app icon and label
        val (appName, appIcon) = getAppLabelAndIcon(context, packageName)
        sheetView.findViewById<ImageView>(R.id.overlay_card_app_icon).setImageDrawable(appIcon)
        sheetView.findViewById<TextView>(R.id.overlay_card_app_name).text = appName
        sheetView.findViewById<TextView>(R.id.overlay_card_title).text = card.title

        val questionTxt = sheetView.findViewById<TextView>(R.id.overlay_card_question)
        questionTxt.text = card.question

        val routesContainer = sheetView.findViewById<LinearLayout>(R.id.overlay_card_routes)
        val stepsContainer = sheetView.findViewById<LinearLayout>(R.id.overlay_card_steps)
        val footContainer = sheetView.findViewById<LinearLayout>(R.id.overlay_card_foot)
        val progressTxt = sheetView.findViewById<TextView>(R.id.overlay_card_progress)
        val nextBtn = sheetView.findViewById<Button>(R.id.overlay_card_btn_next)
        val doneContainer = sheetView.findViewById<LinearLayout>(R.id.overlay_card_done)

        val density = sheetView.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val selectedRoute = intArrayOf(-1)
        val currentStep = intArrayOf(0)

        fun refreshSteps() {
            val route = card.routes[selectedRoute[0]]
            val stepIdx = currentStep[0]

            stepsContainer.removeAllViews()
            route.steps.forEachIndexed { index, step ->
                val isCurrent = index == stepIdx
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    alpha = if (isCurrent || index < stepIdx) 1f else 0.55f
                    if (isCurrent) {
                        background = ContextCompat.getDrawable(context, R.drawable.shape_rounded_md)
                        backgroundTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.primaryContainer)
                        )
                    }

                    addView(TextView(context).apply {
                        text = "${index + 1}"
                        textSize = 13f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(
                            ContextCompat.getColor(
                                context,
                                if (isCurrent) R.color.textPrimary else R.color.textSecondary
                            )
                        )
                    })

                    addView(TextView(context).apply {
                        text = step
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                        setPadding(dp(12), 0, 0, 0)
                    })
                }
                stepsContainer.addView(row)
            }

            progressTxt.text = context.getString(
                R.string.decision_card_progress,
                stepIdx + 1,
                route.steps.size,
            )
            val isLast = stepIdx >= route.steps.size - 1
            nextBtn.text = context.getString(
                if (isLast) R.string.decision_card_btn_finish else R.string.decision_card_btn_next
            )
        }

        fun showDone(route: DecisionRoute) {
            stepsContainer.visibility = View.GONE
            footContainer.visibility = View.GONE
            doneContainer.visibility = View.VISIBLE
            sheetView.findViewById<TextView>(R.id.overlay_card_done_msg).text = route.doneMsg
            sheetView.findViewById<TextView>(R.id.overlay_card_done_sub).text = route.doneSub
        }

        fun selectRoute(index: Int) {
            selectedRoute[0] = index
            currentStep[0] = 0
            questionTxt.visibility = View.GONE
            routesContainer.visibility = View.GONE
            stepsContainer.visibility = View.VISIBLE
            footContainer.visibility = View.VISIBLE
            refreshSteps()
        }

        // Route options (dynamically created because cards can have any number of routes)
        card.routes.forEachIndexed { index, route ->
            val option = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.shape_rounded_md)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.primaryContainer)
                )
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(8), 0, 0) }

                addView(TextView(context).apply {
                    text = route.label
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                })

                if (route.sub.isNotEmpty()) {
                    addView(TextView(context).apply {
                        text = route.sub
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                        setPadding(0, dp(2), 0, 0)
                    })
                }

                setOnClickListener { selectRoute(index) }
            }
            routesContainer.addView(option)
        }

        // Next step / finish
        nextBtn.setOnClickListener {
            val route = card.routes[selectedRoute[0]]
            if (currentStep[0] < route.steps.size - 1) {
                currentStep[0]++
                refreshSteps()
            } else {
                showDone(route)
            }
        }

        // Enter app and skip both just reveal the app underneath
        sheetView.findViewById<Button>(R.id.overlay_card_btn_enter)
            .setOnClickListener { dismissOverlay.invoke() }
        sheetView.findViewById<Button>(R.id.overlay_card_btn_skip)
            .setOnClickListener { dismissOverlay.invoke() }

        return sheetView
    }


    fun getAppLabelAndIcon(
        context: Context,
        packageName: String,
    ): Pair<String, Drawable> {
        val packageManager = context.packageManager
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val appName = packageManager.getApplicationLabel(info).toString()
        val appIcon = packageManager.getApplicationIcon(info)

        return appName to appIcon
    }

    private fun getRestrictionInfo(
        context: Context,
        state: RestrictionState,
    ): String {
        val isLimitExhausted = state.screenTimeUsed >= state.screenTimeLimit

        return when (state.type) {
            RestrictionType.FOCUS ->
                context.getString(R.string.app_paused_reason_focus_session)

            RestrictionType.BEDTIME ->
                context.getString(R.string.app_paused_reason_bedtime)

            RestrictionType.LAUNCH_COUNT ->
                context.getString(R.string.app_paused_reason_launch_count_out)

            RestrictionType.APP_TIMER ->
                if (isLimitExhausted) context.getString(R.string.app_paused_reason_app_timer_out)
                else context.getString(R.string.app_paused_reason_app_timer_left)

            RestrictionType.APP_ACTIVE_PERIOD ->
                context.getString(R.string.app_paused_reason_app_active_period_over)

            RestrictionType.GROUP_TIMER ->
                if (isLimitExhausted) context.getString(
                    R.string.app_paused_reason_group_timer_out,
                    state.groupName
                )
                else context.getString(R.string.app_paused_reason_group_timer_left, state.groupName)

            RestrictionType.GROUP_ACTIVE_PERIOD ->
                context.getString(
                    R.string.app_paused_reason_group_active_period_over,
                    state.groupName
                )
        }
    }
}