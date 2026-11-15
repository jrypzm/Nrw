package app.years

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Opens a link in the browser. yrs. itself never goes online; the browser does. */
fun openLink(c: Context, url: String) {
    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun version(c: Context): String = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: ""

@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    YrsSheet(onDismiss) { AboutContent(onDismiss) }
}

@Composable
fun ColumnScope.AboutContent(onDone: () -> Unit) {
    val ctx = LocalContext.current
    Text("yrs.", style = T.h1.copy(fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-0.06).em), modifier = Modifier.padding(top = 6.dp))
    Text(
        "A small app that shows your life as dots, so you spend the rest of it on purpose.",
        style = T.h2.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 14.dp),
    )

    Caps("Who made it")
    Group { Item("One person", "yrs. is an independent project, made by one person in spare time. No company, no investors, no ads. Just an idea I wanted to exist.") }

    Caps("Yours to use")
    Group {
        Item("Free", "No price, no subscription, nothing locked. All of it is yours.")
        Divider()
        Item("Open source, MIT license", "Read every line of the code. Build it yourself, change anything, or take it and make your own version, your way.")
        Divider()
        Item("Private", "No account and no tracking. yrs. has no internet permission, so your birthday can't leave this phone.")
    }

    Caps("Links")
    Group {
        SettingRow("Website", "How it works, and the iPhone version", onClick = { openLink(ctx, "https://jrypzm.github.io/yrs/") }) { Chevron() }
        Divider()
        SettingRow("Source code", "On GitHub. A star helps others find it", onClick = { openLink(ctx, "https://github.com/jrypzm/yrs") }) { Chevron() }
        Divider()
        SettingRow("Ideas and bugs", "Every one is read", onClick = { openLink(ctx, "https://github.com/jrypzm/yrs/issues/new/choose") }) { Chevron() }
        Divider()
        SettingRow("License", "MIT", onClick = { openLink(ctx, "https://github.com/jrypzm/yrs/blob/HEAD/LICENSE") }) { Chevron() }
    }

    Text(
        "yrs. ${version(ctx)} · Make the next one count.",
        style = T.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Done", onDone)
}

@Composable
private fun Item(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(title, style = T.body.copy(fontWeight = FontWeight.Medium))
        Text(body, style = T.small.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(top = 3.dp))
    }
}
