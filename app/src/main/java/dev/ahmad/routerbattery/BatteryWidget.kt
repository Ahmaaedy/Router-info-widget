package dev.ahmad.routerbattery

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Refresher.now(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Refresher.cancel(context)
    }
}

class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = Store.snapshot(context)
        provideContent { GlanceTheme { Content(context, snap) } }
    }
}

/** Same thresholds as the router portal's own icon logic. */
private fun bars(pct: Int, status: String): Int = when {
    status == "2" -> 4
    pct <= 5 -> 0
    pct <= 25 -> 1
    pct <= 50 -> 2
    pct <= 90 -> 3
    else -> 4
}

private fun fmtBytes(v: String?): String? {
    var n = v?.toDoubleOrNull() ?: return null
    for (u in listOf("B", "KB", "MB", "GB")) {
        if (n < 1024 || u == "GB") return if (u == "B") "${n.toInt()} B" else "%.1f %s".format(n, u)
        n /= 1024
    }
    return null
}

@Composable
private fun Content(ctx: Context, s: Snapshot) {
    // Two colors: surface (background/text) + primary (accent), both from Material You.
    val primary = GlanceTheme.colors.primary
    val dim = ColorProvider(primary.getColor(ctx).copy(alpha = 0.25f))
    val onSurface = GlanceTheme.colors.onSurface

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<ConfigActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (s.percent != null) {
                val filled = bars(s.percent, s.charging)
                Row(verticalAlignment = Alignment.Bottom) {
                    for (i in 1..4) {
                        Box(
                            modifier = GlanceModifier.width(9.dp).height((8 + i * 7).dp)
                                .cornerRadius(3.dp)
                                .background(if (i <= filled) primary else dim),
                        ) {}
                        if (i < 4) Spacer(GlanceModifier.width(3.dp))
                    }
                }
                Spacer(GlanceModifier.width(14.dp))
                Column {
                    Text("${s.percent}%", style = TextStyle(color = onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                    val label = when (s.charging) { "0" -> "On battery"; "2" -> "Full"; else -> "Charging" }
                    Text(label, style = TextStyle(color = primary, fontSize = 13.sp))
                }
            } else {
                Text(s.error ?: "Loading…", style = TextStyle(color = onSurface, fontSize = 16.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(primary),
                modifier = GlanceModifier.size(30.dp).clickable(actionRunCallback<RefreshCallback>()),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        val down = fmtBytes(s.rx)
        val up = fmtBytes(s.tx)
        if (down != null && up != null) {
            Text("↓ $down    ↑ $up", style = TextStyle(color = onSurface, fontSize = 14.sp))
        }
        val footer = when {
            s.busy -> "Refreshing…"
            s.percent != null && s.error != null -> "${s.error} · last ${hhmm(s.time)}"
            s.percent != null -> "Updated ${hhmm(s.time)}"
            else -> ""
        }
        if (footer.isNotEmpty()) {
            Text(footer, style = TextStyle(color = primary, fontSize = 11.sp))
        }
    }
}

private fun hhmm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
