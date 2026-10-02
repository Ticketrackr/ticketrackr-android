package com.ticketrackr.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ticketrackr.android.SupportListener
import com.ticketrackr.android.TicketRackr

/** Two ways in: a Help button that opens support on a screen of its own, and support inside a screen of yours. */
class MainActivity : ComponentActivity() {
    private val log = mutableListOf<String>()
    private lateinit var events: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "Support Example"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.BLACK)
        }
        val help = Button(this).apply {
            text = "Help"
            isAllCaps = false
            setOnClickListener {
                note("opened")
                TicketRackr.openSupport(this@MainActivity, supportLinks(intent), listener = object : SupportListener {
                    override fun onReady() = note("ready")
                    override fun onUnreadChange(count: Int) = note("unread $count")
                    override fun onClose() = note("closed")
                })
            }
        }
        val inView = Button(this).apply {
            text = "Support in a view"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, EmbeddedActivity::class.java).putExtras(intent)) }
        }
        events = TextView(this).apply {
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            contentDescription = "log"
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(title)
            addView(help)
            addView(inView)
            addView(events)
        })
    }

    private fun note(line: String) {
        runOnUiThread {
            log += line
            events.text = log.takeLast(5).joinToString(" | ")
        }
    }
}
