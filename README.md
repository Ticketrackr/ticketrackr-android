# TicketRackr Support for Android

Your company's TicketRackr support inside your Android app, without sending customers to a browser: requests and
reports with their forms, the conversation, file uploads, the AI assistant and satisfaction surveys. Kotlin or Java,
Views or Compose, Android 7.0 (API 24) or later.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.ticketrackr:support-android:0.3.0")
}
```

It's on Maven Central, and needs only the `INTERNET` permission, which it declares.

## 1. Your server makes a support link

Your TicketRackr key stays on your server, never in the app. Add an endpoint that, for the signed-in customer:

1. calls `POST https://api.ticketrackr.com/v1/customer-sessions` with `Authorization: Bearer <your key>` and
   `{"externalCustomerId": "<your id for them>", "email": "…", "name": "…"}`, which returns a `token`;
2. calls `POST https://api.ticketrackr.com/v1/support-portal/links` with `Authorization: Bearer <that token>` and `{}`;
3. returns that link's `{"url": "…"}` to the app.

Your key is one from TicketRackr (Settings → Companies → Connect) as `clientId.clientSecret`. A sandbox key shows the
sandbox's test data; a live key, your real customers'. Use your database's id for the customer, not something that
changes like an email address.

## 2. Show support

Give support a way to get a link from your endpoint (on any thread):

```kotlin
val getSupportLink = SupportLinkProvider { callback ->
    lifecycleScope.launch {
        try {
            callback.onLink(yourApi.supportLink().url) // POST to your endpoint
        } catch (error: Exception) {
            callback.onError(error)
        }
    }
}
```

**A Help button**, which opens support on a screen of its own and badges the customer's unread replies:

```kotlin
val help = SupportButton(this)
help.setup(getSupportLink)
```

Or place it in a layout and set it up in code:

```xml
<com.ticketrackr.android.SupportButton
    android:id="@+id/help"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:ticketrackrColor="#16776B" />
```

```kotlin
findViewById<SupportButton>(R.id.help).setup(getSupportLink)
```

**Support on a screen of its own**, from a button of yours, with Close (and the back button):

```kotlin
helpButton.setOnClickListener {
    TicketRackr.openSupport(this, getSupportLink, listener = object : SupportListener {
        override fun onUnreadChange(count: Int) { /* a badge */ }
    })
}
```

**Support inside a screen of yours** (keep it inside the system bars, as your other content):

```kotlin
val support = TicketRackrSupportView(this)
support.show(getSupportLink)
// …and support.destroy() in onDestroy.
```

**Compose:**

```kotlin
AndroidView(factory = { SupportButton(it).apply { setup(getSupportLink) } })
// or a button of yours:
Button(onClick = { TicketRackr.openSupport(context, getSupportLink) }) { Text("Help") }
// or inline:
AndroidView(factory = { TicketRackrSupportView(it).apply { show(getSupportLink) } }, onRelease = { it.destroy() })
```

**Java:**

```java
helpButton.setup(callback -> yourApi.supportLink(callback::onLink, callback::onError));
// or a button of yours:
TicketRackr.openSupport(this, callback -> yourApi.supportLink(callback::onLink, callback::onError));
```

## Options

| | |
| --- | --- |
| `getSupportLink` | Required. Calls your endpoint and passes the link's `url` to `callback.onLink` (or `onError`). Called on open, and again if the session ends. |
| `SupportOptions(requestType = …)` | Open the form for one request type, by its key, such as a report: `"report_problem"`. |
| `SupportOptions(subject = …, fields = …)` | Fill in the request's subject and its type's fields (by key). |
| `SupportOptions(ticket = …)` | Open one of the customer's requests, by its id: `ticket.id` from the `ticket.message.created` webhook, for example when the customer taps a notification about a reply. Another customer's request isn't opened; support shows their own requests instead. |
| `SupportOptions(language = …)` | `en`, `es`, `fr`, `de` or `pt`. The device's language when left out. |
| `SupportListener` | `onReady`, `onUnreadChange(count)` and `onClose` (its Close button, or Back on support's own screen). |
| `closable` | `TicketRackrSupportView.show` only: show a Close button. |
| `label` (`android:text`) | `SupportButton` only: its text. "Help", in the support language, when left out. |
| `color` (`app:ticketrackrColor`) | `SupportButton` only: its color, your brand color. `#16776B` when left out. |

## Unread replies

The Help button's badge counts the customer's unread replies even while support is closed: an agent's answer shows
on the button before the customer opens support again. Each time support opens, it leaves a token that reads only that
count, for 30 days. The button asks TicketRackr with it when it appears and when your app comes back to the
foreground, at most once a minute. It needs no support link and no session, so it costs you nothing.

For a badge of your own (a tab bar, a menu), ask for the count. It comes on the main thread, and it's `null` when it
isn't known (support hasn't opened on this device yet):

```kotlin
TicketRackr.unreadCount(context) { count -> tabBadge.text = count?.takeIf { it > 0 }?.toString() }
```

```java
TicketRackr.unreadCount(this, count -> { /* null when it isn't known */ });
```

When your app's user signs out, forget their count, so the next person on the device doesn't see it:

```kotlin
TicketRackr.signOut(context)
```

## Request types and reports

What customers can ask for (a problem report, a billing question, reporting a user) is set in TicketRackr, not in
your code: make case types with their forms in Settings → Companies → Case types, and choose which customers see in
Settings → Companies → Support page. They appear in your app right away, with no new build.

## Good to know

- Sessions renew themselves: when one ends, support asks your endpoint for a new link.
- A file the customer opens goes to the system's downloads, with a notification to open it from; support stays as it
  was. TicketRackr pages, like the status page or a help article, open on a screen with Back. Other websites, email and
  phone links open in their own apps.
- Customers attach photos and files with the system's picker. `TicketRackrSupportView` needs a `ComponentActivity`
  (`AppCompatActivity` is one) for it.
- On support's own screen, support stays inside the system bars and above the keyboard.
- Support uses your brand color and logo from Settings → Companies → Support page.
- `example/` is an app that shows both ways in: the Help button, and support inside a screen.
- Full guide and the API: https://ticketrackr.com/docs/support-api#embedded-support

## License

MIT: use it freely. It shows your support from TicketRackr, so it needs a TicketRackr account (sign up at
https://ticketrackr.com/signup).
