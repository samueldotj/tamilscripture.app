package com.tamilscripture.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.theme.Ts

val Pill = RoundedCornerShape(999.dp)

enum class PillStyle { Filled, Outlined, Tinted, Ghost }

/** The design's 40–44 dp pill buttons ("வசனங்களுடன் படிக்க", "‹ அதி 2", "▶ கேள்"). */
@Composable
fun TsPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Outlined,
    height: Dp = 44.dp,
    icon: ImageVector? = null,
    textStyle: TextStyle = Ts.type.label,
) {
    val c = Ts.colors
    val (bg, fg, border) = when (style) {
        PillStyle.Filled -> Triple(c.accent, c.onAccent, null)
        PillStyle.Outlined -> Triple(Color.Transparent, c.ink2, BorderStroke(1.5.dp, c.line2))
        PillStyle.Tinted -> Triple(c.accentSoft, c.accent, null)
        PillStyle.Ghost -> Triple(Color.Transparent, c.ink2, null)
    }
    Row(
        modifier = modifier
            .height(height)
            .clip(Pill)
            .background(bg)
            .then(if (border != null) Modifier.border(border, Pill) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Text(text, style = textStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Filter / source chip: filled accent when selected, outlined otherwise. */
@Composable
fun TsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    textStyle: TextStyle = Ts.type.labelSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    val c = Ts.colors
    val shape = if (square) RoundedCornerShape(12.dp) else Pill
    val bg by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "chipBg")
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, c.line2, shape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = textStyle, color = if (selected) c.onAccent else c.ink2,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** Small non-interactive badge ("G25", "யோவான் 3:15, 16, 36"). */
@Composable
fun TsBadge(text: String, modifier: Modifier = Modifier, tinted: Boolean = true) {
    val c = Ts.colors
    Box(
        modifier
            .clip(Pill)
            .background(if (tinted) c.accentSoft else Color.Transparent)
            .then(if (tinted) Modifier else Modifier.border(1.dp, c.line2, Pill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = Ts.type.captionSmall.copy(fontWeight = FontWeight.Bold), color = if (tinted) c.accent else c.ink2)
    }
}

/** Segmented control ("இன்று | திட்டங்கள் | புள்ளிவிவரம்", "இருள் | ஒளி | தானியங்கி"). */
@Composable
fun TsSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    fill: Boolean = false,
    textStyle: TextStyle = Ts.type.label,
) {
    val c = Ts.colors
    Row(
        modifier
            .height(height)
            .clip(Pill)
            .border(1.5.dp, c.line2, Pill),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (on) c.accent else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = if (fill) 6.dp else 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label, style = textStyle.copy(color = if (on) c.onAccent else c.ink2), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = textStyle.fontSize),
                )
            }
        }
    }
}

/** The design's 52×32 switch: accent track with a dark knob when on, outlined when off. */
@Composable
fun TsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ts.colors
    val knobOffset by animateDpAsState(if (checked) 24.dp else 5.dp, label = "knob")
    val knobSize by animateDpAsState(if (checked) 24.dp else 18.dp, label = "knobSize")
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(Pill)
            .background(if (checked) c.accent else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(2.dp, c.lineStrong, Pill))
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (checked) c.onAccent else c.faint.copy(alpha = 1f)),
        )
    }
}

/** Uppercase letter-spaced section label ("தொடர்ந்து வாசிக்க"). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Ts.colors.muted) {
    Text(text.uppercase(), modifier, style = Ts.type.kicker, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Raised card (#262930 / white) or flat card (#20232A / warm), 20 dp radius. */
@Composable
fun TsCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    radius: Dp = 20.dp,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ts.colors
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(if (raised) c.surface else c.surface2)
            .border(1.5.dp, if (raised) c.line2 else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** 44 dp square hit target with a centred icon. */
@Composable
fun IconBox(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ts.colors.ink2,
    iconSize: Dp = 22.dp,
    background: Color = Color.Transparent,
    badge: Boolean = false,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Ts.colors.amber),
            )
        }
    }
}

/** The centred "யோவான் 3  IRV ▾" pill in the reader's top bar. */
@Composable
fun ReferencePill(title: String, version: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val c = Ts.colors
    Row(
        modifier
            .height(height)
            .clip(Pill)
            .background(c.surface)
            .border(1.5.dp, c.line2, Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(title, style = Ts.type.barTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$version ▾", style = Ts.type.caption, color = c.muted, maxLines = 1)
    }
}

/** Bottom-sheet drag handle (36×4). */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 4.dp).clip(Pill).background(Ts.colors.lineStrong))
    }
}

/** The gold cross mark next to "தமிழ் வேதாகமம்". */
@Composable
fun CrossMark(modifier: Modifier = Modifier, height: Dp = 22.dp, color: Color = Ts.colors.accent) {
    val w = height * (16f / 22f)
    val bar = height * (4f / 22f)
    Box(modifier.size(w, height)) {
        Box(Modifier.align(Alignment.TopCenter).size(bar, height).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.offset(y = height * (6f / 22f)).size(w, bar).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

/** Round profile avatar ("அ"). */
@Composable
fun Avatar(initial: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val c = Ts.colors
    Box(
        modifier.size(size).clip(CircleShape).background(c.brand).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = Ts.type.label.copy(fontSize = 14.sp), color = c.onBrand)
    }
}

/** A keyboard key cap in the Chromebook hint strip. Tappable so the strip also works by touch. */
@Composable
fun KeyHint(keys: List<String>, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ts.colors
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { k ->
            Box(Modifier.border(1.dp, c.line2, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(k, style = Ts.type.labelSmall, color = c.ink)
            }
        }
        Text(label, style = Ts.type.caption, color = c.muted, maxLines = 1)
    }
}

@Composable
fun HDivider(modifier: Modifier = Modifier, color: Color = Ts.colors.line, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun VDivider(modifier: Modifier = Modifier, color: Color = Ts.colors.line, thickness: Dp = 1.5.dp) {
    Box(modifier.fillMaxHeight().width(thickness).background(color))
}

/** A settings-style row: title, optional subtitle, trailing content. */
@Composable
fun TsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    titleStyle: TextStyle = Ts.type.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleColor: Color = Ts.colors.ink,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = titleColor)
            if (subtitle != null) Text(subtitle, style = Ts.type.caption, color = Ts.colors.muted)
        }
        trailing?.invoke(this)
    }
}

/** Thin progress bar (4 dp, accent on line). */
@Composable
fun TsProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val c = Ts.colors
    Box(modifier.fillMaxWidth().height(height).clip(Pill).background(c.line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(Pill).background(c.accent))
    }
}
