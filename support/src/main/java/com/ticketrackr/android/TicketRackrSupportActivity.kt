package com.ticketrackr.android

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Support on a screen of its own, opened with [TicketRackr.openSupport]. */
class TicketRackrSupportActivity : ComponentActivity() {
    private var support: TicketRackrSupportView? = null
    private var session: TicketRackr.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened again after Android closed the app in the background: there's no one to ask for a link.
        val session = TicketRackr.session ?: return finish()
        this.session = session
        val support = TicketRackrSupportView(this)
        this.support = support
        setContentView(support)
        Screen.fill(this, support)
        support.listener = object : SupportListener {
            override fun onReady() {
                session.listener?.onReady()
            }

            override fun onUnreadChange(count: Int) {
                session.listener?.onUnreadChange(count)
            }

            override fun onClose() {
                finish()
            }
        }
        support.show(session.getSupportLink, session.options, closable = true)
    }

    override fun onDestroy() {
        support?.destroy()
        // Closed for good (its Close button or Back), not just rotated.
        if (isFinishing) {
            session?.listener?.onClose()
            if (TicketRackr.session === session) TicketRackr.session = null
        }
        super.onDestroy()
    }
}
