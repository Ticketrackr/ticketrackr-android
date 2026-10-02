package com.ticketrackr.android

/**
 * Gets a new one-time support link from your server: the `url` from POST /v1/support-portal/links, made for the
 * signed-in customer. Called when support opens and again whenever its session ends. Your TicketRackr key stays on
 * your server.
 */
fun interface SupportLinkProvider {
    /** Get a link (on any thread) and pass its url, or what went wrong, to [callback]. */
    fun getSupportLink(callback: SupportLinkCallback)
}

/** Where [SupportLinkProvider] sends the link. Either may be called from any thread. */
interface SupportLinkCallback {
    fun onLink(url: String)
    fun onError(error: Throwable)
}

/** What support tells your app. Every method is optional. */
interface SupportListener {
    /** Support has loaded and signed in. */
    fun onReady() {}

    /** The customer's unread replies, whenever the number changes. */
    fun onUnreadChange(count: Int) {}

    /**
     * Support closed: its Close button (support shows Close when it's closable), or, for support opened with
     * [TicketRackr.openSupport], the back button.
     */
    fun onClose() {}
}
