package com.ticketrackr.android

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SoundEffectConstants
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A Help button that opens support on a screen of its own ([TicketRackr.openSupport]), with a badge for the customer's
 * unread replies. The badge counts even while support is closed (sdks/protocol, section 7): it shows the last count at
 * once, then asks TicketRackr when the button appears and when your app comes back to the foreground, at most once a
 * minute for the whole app.
 *
 * Make it in code or place it in a layout (`android:text` for its text, `app:ticketrackrColor` for its color), then
 * call [setup]. An OnClickListener of yours hears each tap before support opens.
 */
class SupportButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LinearLayout(context, attrs, defStyleAttr) {
    /** The button's text. "Help", in the support language, when null. */
    var label: CharSequence? = null
        set(value) {
            field = value
            render()
        }

    /** The button's color (ARGB, such as 0xFF16776B): your brand color. Its text is white. */
    var color: Int = DEFAULT_COLOR
        set(value) {
            field = value
            paint()
        }

    private var getSupportLink: SupportLinkProvider? = null
    private var options = SupportOptions()
    private var listener: SupportListener? = null
    private var showing = false
    private var openedAt: Long? = null
    private val name = TextView(context)
    private val badge = TextView(context)
    private val shape = GradientDrawable()

    // Every change to the kept count (support's own, a check, signing out) shows at once. Held here because
    // SharedPreferences keeps its listeners only weakly.
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    init {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        isClickable = true
        isFocusable = true
        // A capsule at any height: corners are cut to half of it.
        shape.cornerRadius = 999 * density
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
        name.setTextColor(Color.WHITE)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        name.typeface = medium
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        badge.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 999 * density
        }
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        badge.typeface = medium
        badge.gravity = Gravity.CENTER
        badge.minWidth = dp(20)
        badge.setPadding(dp(6), dp(1), dp(6), dp(1))
        badge.visibility = GONE
        // The button reads as one: "Help, 2 unread".
        name.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        badge.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        val styled = context.obtainStyledAttributes(attrs, R.styleable.SupportButton, defStyleAttr, 0)
        try {
            label = styled.getText(R.styleable.SupportButton_android_text)
            color = styled.getColor(R.styleable.SupportButton_ticketrackrColor, DEFAULT_COLOR)
        } finally {
            styled.recycle()
        }
    }

    /**
     * What the button opens.
     *
     * @param getSupportLink gets a new support link from your server.
     * @param options what to open: a request type's form, filled in, or one of the customer's requests, in a language
     * (the button's own words follow it).
     * @param listener what support tells your app.
     */
    @JvmOverloads
    fun setup(getSupportLink: SupportLinkProvider, options: SupportOptions = SupportOptions(), listener: SupportListener? = null) {
        this.getSupportLink = getSupportLink
        this.options = options
        this.listener = listener
        render()
    }

    override fun performClick(): Boolean {
        // Your OnClickListener first (it plays the click sound when there is one), then support.
        if (!super.performClick()) playSoundEffect(SoundEffectConstants.CLICK)
        open()
        return true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) UnreadChecks.preferences(context).registerOnSharedPreferenceChangeListener(changes)
        render()
    }

    override fun onDetachedFromWindow() {
        if (!isInEditMode) UnreadChecks.preferences(context).unregisterOnSharedPreferenceChangeListener(changes)
        showing = false
        super.onDetachedFromWindow()
    }

    // The app coming back to the foreground, or support's own screen closing over it.
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        seen(visibility == VISIBLE && isShown)
    }

    // The button, or a view it's in, shown or hidden.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        seen(isVisible && windowVisibility == VISIBLE)
    }

    /** Each time the button appears: the last count at once, then TicketRackr is asked, unless it was this minute. */
    private fun seen(visible: Boolean) {
        if (visible == showing) return
        showing = visible
        if (!visible || isInEditMode) return
        render()
        UnreadChecks.refresh(context) { render() }
    }

    private fun open() {
        val getSupportLink = getSupportLink
        if (getSupportLink == null) {
            Log.w(TAG, "SupportButton opens support once setup(getSupportLink) is called.")
            return
        }
        // A second tap while support is opening doesn't open it twice.
        val now = SystemClock.elapsedRealtime()
        val openedAt = openedAt
        if (openedAt != null && now - openedAt < 1_000) return
        this.openedAt = now
        // Over the Activity the button is in, not in a task of its own.
        TicketRackr.openSupport(context.findActivity() ?: context, getSupportLink, options, listener)
    }

    private fun paint() {
        shape.setColor(color)
        badge.setTextColor(color)
    }

    /** The text, and the badge with the last count known (none for 0). */
    private fun render() {
        val words = SupportWords.forLanguage(options.language)
        val text = label ?: words.help
        val count = if (isAttachedToWindow && !isInEditMode) UnreadChecks.badge(context).last()?.takeIf { it > 0 } else null
        name.text = text
        badge.text = count?.toString()
        badge.visibility = if (count == null) GONE else VISIBLE
        contentDescription = if (count == null) text else "$text, $count ${words.unread}"
    }

    private companion object {
        const val TAG = "TicketRackr"
        const val DEFAULT_COLOR = 0xFF16776B.toInt()
    }
}
