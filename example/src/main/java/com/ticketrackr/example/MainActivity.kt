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
import com.ticketrackr.android.SupportButton
import com.ticketrackr.android.SupportListener

/**
 * Two ways in: the SDK's Help button, which opens support on a screen of its own and badges unread replies, and support
 * inside a screen of yours.
 */
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
        val help = SupportButton(this).apply {
            setup(supportLinks(intent), listener = object : SupportListener {
                override fun onReady() = note("ready")
                override fun onUnreadChange(count: Int) = note("unread $count")
                override fun onClose() = note("closed")
            })
            // Hears the tap; support opens after it.
            setOnClickListener { note("opened") }
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
        val spaced = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val space = (12 * resources.displayMetrics.density).toInt()
            setMargins(0, space, 0, space)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(title)
            addView(help, spaced)
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
