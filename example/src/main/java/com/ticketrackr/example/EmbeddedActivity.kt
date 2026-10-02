package com.ticketrackr.example

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ticketrackr.android.SupportListener
import com.ticketrackr.android.TicketRackrSupportView

/** Support inside a screen of the app's own, under its header. The app keeps it inside the system bars. */
class EmbeddedActivity : ComponentActivity() {
    private lateinit var support: TicketRackrSupportView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val header = TextView(this).apply {
            text = "Your app's screen"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.BLACK)
            setPadding(40, 30, 40, 30)
        }
        support = TicketRackrSupportView(this)
        support.listener = object : SupportListener {
            override fun onClose() = finish()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(header)
            addView(support, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val edges = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        support.show(supportLinks(intent), closable = true)
    }

    override fun onDestroy() {
        support.destroy()
        super.onDestroy()
    }
}
