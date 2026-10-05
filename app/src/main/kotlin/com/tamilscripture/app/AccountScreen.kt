package com.tamilscripture.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsListRow
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.launch

/**
 * கணக்கு · Account (M6-3, M6-8, M6-9): sign in with the website's account, sync, export,
 * sign out, delete. Everything here also works on the website; nothing here is needed to read.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, onMine: () -> Unit) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by graph.account.session.collectAsStateWithLifecycle()
    val data by graph.userData.data.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    fun t(ta: String, en: String) = if (lang == UiLang.Tamil) ta else en
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { graph.userData.exportFromAccount() }.getOrNull()
            if (text == null) { toast(t("ஏற்றுமதி தோல்வி", "Export failed")); return@launch }
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                .onSuccess { toast(t("சேமிக்கப்பட்டது", "Saved")) }
                .onFailure { toast(t("சேமிக்க முடியவில்லை", "Could not save")) }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            Text(tr("கணக்கு", "Account"), style = Ts.type.barTitle, color = c.ink)
        }
        HDivider(thickness = 1.5.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                val s = session
                if (s == null) {
                    Text(
                        tr(
                            "tamilscripture.com கணக்குடன் உள்நுழைந்து முனைப்பிடலாம், குறிப்பெழுதலாம்; அவையும் குறிகளும் வாசிப்பு வரலாறும் இணையதளத்துடனும் உங்கள் மற்ற சாதனங்களுடனும் ஒத்திசையும். குறிகளும் வரலாறும் உள்நுழையாமலும் இந்தச் சாதனத்தில் இருக்கும்.",
                            "Sign in with your tamilscripture.com account to highlight and write notes; they, your bookmarks and your reading history then follow you to the website and your other devices. Bookmarks and history also work without signing in, on this device.",
                        ),
                        style = Ts.type.body, color = c.ink2, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                    Kicker(tr("மின்னஞ்சல் இணைப்பு", "Email link"), Modifier.padding(start = 22.dp, top = 8.dp, bottom = 8.dp))
                    Box(
                        Modifier.padding(horizontal = 22.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, c.line2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        if (email.isEmpty()) Text("name@example.com", style = Ts.type.body, color = c.faint)
                        BasicTextField(
                            email, { email = it.trim() }, singleLine = true,
                            textStyle = Ts.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TsPillButton(t("இணைப்பை அனுப்பு", "Send link"), {
                            if (busy || !email.contains('@')) return@TsPillButton
                            busy = true
                            scope.launch {
                                runCatching { graph.account.sendEmailLink(email) }
                                    .onSuccess { sentTo = email }
                                    .onFailure { toast(t("அனுப்ப முடியவில்லை. இணைப்பைச் சரிபார்த்து மீண்டும் முயலுங்கள்.", "Could not send. Check the connection and try again.")) }
                                busy = false
                            }
                        }, style = PillStyle.Filled)
                    }
                    sentTo?.let { to ->
                        Text(
                            t("$to முகவரிக்கு இணைப்பு அனுப்பப்பட்டது. இந்தத் தொலைபேசியிலேயே அதைத் திறங்கள்.", "A link is on its way to $to. Open it on this phone."),
                            style = Ts.type.caption, color = c.accent, modifier = Modifier.padding(horizontal = 22.dp),
                        )
                    }
                    Kicker(tr("அல்லது", "Or"), Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp))
                    TsPillButton(t("Google மூலம் தொடர்க", "Continue with Google"), {
                        scope.launch {
                            val url = graph.account.googleUrl()
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    }, style = PillStyle.Outlined, modifier = Modifier.padding(horizontal = 22.dp))
                    HDivider(Modifier.padding(top = 24.dp))
                    TsListRow(
                        tr("இந்தச் சாதனத்தில்", "On this device"),
                        subtitle = counts(data.highlights.size, data.notes.size, data.bookmarks.size, lang),
                        onClick = onMine,
                        trailing = { Icon(TsIcons.ChevronRight, null, tint = c.muted) },
                    )
                } else {
                    TsListRow(s.email ?: "", subtitle = tr("உள்நுழைந்துள்ளீர்கள்", "Signed in"))
                    HDivider()
                    TsListRow(
                        tr("என் முனைப்புகளும் குறிப்புகளும்", "My highlights and notes"),
                        subtitle = counts(data.highlights.size, data.notes.size, data.bookmarks.size, lang),
                        onClick = onMine,
                        trailing = { Icon(TsIcons.ChevronRight, null, tint = c.muted) },
                    )
                    HDivider()
                    TsListRow(
                        tr("இப்போது ஒத்திசை", "Sync now"),
                        subtitle = status ?: tr("மாற்றங்கள் தானாக ஒத்திசைகின்றன", "Changes sync by themselves"),
                        trailing = {
                            TsPillButton(t("ஒத்திசை", "Sync"), {
                                if (busy) return@TsPillButton
                                busy = true
                                status = t("ஒத்திசைகிறது…", "Syncing…")
                                scope.launch {
                                    status = runCatching { graph.syncAccount() }.fold(
                                        { t("ஒத்திசைந்தது", "Up to date") },
                                        { t("ஒத்திசைக்க முடியவில்லை", "Could not sync") },
                                    )
                                    busy = false
                                }
                            }, height = 36.dp)
                        },
                    )
                    HDivider()
                    TsListRow(
                        tr("என் தரவை ஏற்றுமதி செய்", "Export my data"),
                        subtitle = tr("கணக்கில் உள்ள அனைத்தும், ஒரு JSON கோப்பாக", "Everything in the account, as one JSON file"),
                        onClick = { exportFile.launch("tamilscripture-export.json") },
                    )
                    HDivider()
                    TsListRow(tr("வெளியேறு", "Sign out"), subtitle = tr("கணக்கின் தரவு இந்தச் சாதனத்திலிருந்து நீக்கப்படும்", "The account's data leaves this device"), onClick = { confirm = "signout" })
                    HDivider()
                    TsListRow(
                        tr("கணக்கை நீக்கு", "Delete account"),
                        subtitle = tr("கணக்கும் அதன் அனைத்துத் தரவும் நிரந்தரமாக நீக்கப்படும்", "The account and everything in it, for good"),
                        titleColor = c.amber,
                        onClick = { confirm = "delete" },
                    )
                }
            }
        }
    }

    confirm?.let { what ->
        val delete = what == "delete"
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.surface,
            title = { Text(if (delete) t("கணக்கை நீக்கவா?", "Delete your account?") else t("வெளியேறவா?", "Sign out?"), color = c.ink) },
            text = {
                Text(
                    if (delete) t("உங்கள் முனைப்புகள், குறிப்புகள், வரலாறு, திட்ட முன்னேற்றம் அனைத்தும் இணையதளத்திலும் நீக்கப்படும். இதைத் திரும்பப் பெற முடியாது.",
                        "Your highlights, notes, history and plan progress are deleted from the website too. This cannot be undone.")
                    else t("உங்கள் தரவு கணக்கில் பாதுகாப்பாக இருக்கும்; மீண்டும் உள்நுழைந்தால் திரும்ப வரும்.", "Your data stays safe in the account and comes back when you sign in again."),
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton({
                    confirm = null
                    scope.launch {
                        if (delete) {
                            runCatching { graph.userData.deleteAccount(); graph.plans.clear(); graph.settingsSync.forget() }
                                .onSuccess { toast(t("கணக்கு நீக்கப்பட்டது", "Account deleted")) }
                                .onFailure { toast(t("நீக்க முடியவில்லை", "Could not delete")) }
                        } else {
                            // What has not reached the account yet goes first.
                            runCatching { graph.syncAccount() }
                            graph.userData.signOutAndWipe()
                            graph.plans.clear()
                            graph.settingsSync.forget()
                        }
                    }
                }) { Text(if (delete) t("நீக்கு", "Delete") else t("வெளியேறு", "Sign out"), color = if (delete) c.amber else c.accent) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text(t("வேண்டாம்", "Cancel"), color = c.ink2) } },
        )
    }
}

private fun counts(highlights: Int, notes: Int, bookmarks: Int, lang: UiLang) =
    if (lang == UiLang.Tamil) "$highlights முனைப்புகள் · $notes குறிப்புகள் · $bookmarks குறிகள்"
    else "$highlights highlight${if (highlights == 1) "" else "s"} · $notes note${if (notes == 1) "" else "s"} · $bookmarks bookmark${if (bookmarks == 1) "" else "s"}"
