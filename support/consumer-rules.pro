# The page's messages reach this interface by name on devices without web message listeners.
-keepclassmembers class com.ticketrackr.android.TicketRackrSupportView$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
