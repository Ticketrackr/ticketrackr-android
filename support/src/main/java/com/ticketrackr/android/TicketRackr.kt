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
}
