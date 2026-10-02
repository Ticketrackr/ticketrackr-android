package com.ticketrackr.android

import android.app.Activity
import android.content.Context
import android.content.Intent

/** TicketRackr support in your app. */
object TicketRackr {
    internal class Session(val getSupportLink: SupportLinkProvider, val options: SupportOptions, val listener: SupportListener?)

    // The support screen reads what to show from here; it's the one place an Activity can be handed objects.
    internal var session: Session? = null

    /**
     * Opens support on a screen of its own, with a Close button: requests and reports with their forms, the
     * conversation, files, the AI assistant and surveys. The back button closes it too.
     */
    @JvmStatic
    @JvmOverloads
    fun openSupport(context: Context, getSupportLink: SupportLinkProvider, options: SupportOptions = SupportOptions(), listener: SupportListener? = null) {
        session = Session(getSupportLink, options, listener)
        val intent = Intent(context, TicketRackrSupportActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * The customer's unread replies, for a badge of your own (a tab bar, a menu); [SupportButton] shows its own. Each
     * time support opens it leaves a token that reads only this count, so asking needs no support link and no session.
     * [callback] hears on the main thread: the number, or null when it isn't known (support hasn't opened on this device
     * yet, its token expired or was refused, or [signOut]). When TicketRackr can't be reached, it hears the last number
     * known.
     */
    @JvmStatic
    fun unreadCount(context: Context, callback: UnreadCountCallback) {
        UnreadChecks.count(context, callback)
    }

    /**
     * Forgets the unread token and count. Call it when your app's user signs out, so the next person on the device
     * doesn't see their count; the badge counts again once support opens for someone.
     */
    @JvmStatic
    fun signOut(context: Context) {
        UnreadChecks.badge(context).signOut()
    }
}

/** Hears the customer's unread replies, from [TicketRackr.unreadCount]. */
fun interface UnreadCountCallback {
    /** On the main thread: the number of unread replies, or null when it isn't known. */
    fun onUnreadCount(count: Int?)
}
