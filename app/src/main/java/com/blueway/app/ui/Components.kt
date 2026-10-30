package com.blueway.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueway.app.data.DataSource
import com.blueway.app.data.NoiseLevel
import java.util.Locale

/** Fondo claro con las ondas azules del diseño. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val bw = LocalBw.current
    if (bw.highContrast) {
        Box(modifier.fillMaxSize().background(Color.White))
        return
    }
    Canvas(modifier.fillMaxSize().background(bw.background)) {
        val w = size.width
        val h = size.height
        val top = Path().apply {
            moveTo(w * 0.42f, 0f)
            cubicTo(w * 0.52f, h * 0.07f, w * 0.72f, h * 0.03f, w * 0.85f, h * 0.09f)
            cubicTo(w * 0.93f, h * 0.13f, w * 0.92f, h * 0.17f, w, h * 0.18f)
            lineTo(w, 0f)
            close()
        }
        drawPath(top, Color(0xFFD3EBFC))
        val top2 = Path().apply {
            moveTo(w * 0.62f, 0f)
            cubicTo(w * 0.7f, h * 0.05f, w * 0.85f, h * 0.02f, w, h * 0.09f)
            lineTo(w, 0f)
            close()
        }
        drawPath(top2, Color(0xFFBFE2FA))
        val bottom = Path().apply {
            moveTo(0f, h * 0.9f)
            cubicTo(w * 0.1f, h * 0.91f, w * 0.14f, h * 0.97f, w * 0.3f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(bottom, Color(0xFFD3EBFC))
    }
}

@Composable
fun BackCircle(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Volver") {
    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, label, onClick, modifier)
}

/** Botón circular blanco con sombra suave. El área táctil es de al menos 48 dp. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = LocalBw.current.primary,
) {
    val bw = LocalBw.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bw.surface,
        shadowElevation = 5.dp,
        border = if (bw.highContrast) BorderStroke(2.dp, bw.outline) else null,
        modifier = modifier.size(size).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    showChevron: Boolean = true,
    height: Dp = 60.dp,
) {
    val bw = LocalBw.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .shadow(8.dp, RoundedCornerShape(50), ambientColor = bw.primary, spotColor = bw.primary)
            .clip(RoundedCornerShape(50))
            .background(bw.gradient)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(28.dp),
            )
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = LocalBw.current.tint,
    onClick: (() -> Unit)? = null,
    corner: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    val bw = LocalBw.current
    val border = if (bw.highContrast) BorderStroke(1.5.dp, bw.outline) else null
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(corner), color = if (bw.highContrast) Color.White else color, border = border, modifier = modifier) { content() }
    } else {
        Surface(shape = RoundedCornerShape(corner), color = if (bw.highContrast) Color.White else color, border = border, modifier = modifier) { content() }
    }
}

/** Fila de lista con icono, título, subtítulo y flecha. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalBw.current.surface,
    trailing: @Composable (() -> Unit)? = null,
    elevated: Boolean = true,
) {
    val bw = LocalBw.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (bw.highContrast) Color.White else color,
        shadowElevation = if (elevated && !bw.highContrast) 2.dp else 0.dp,
        border = if (bw.highContrast) BorderStroke(1.5.dp, bw.outline) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = bw.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = bw.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (trailing != null) {
                trailing()
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = bw.muted, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = LocalBw.current.primary, bg: Color = LocalBw.current.tint, size: Dp = 52.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun ImageTile(@DrawableRes image: Int, size: Dp = 52.dp) {
    Image(painterResource(image), null, modifier = Modifier.size(size))
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** Etiqueta obligatoria de procedencia del dato. */
@Composable
fun SourceTag(source: DataSource, modifier: Modifier = Modifier) {
    val bw = LocalBw.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bw.tint)
            .border(1.dp, bw.outline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Origen: ${source.label}", color = bw.inkSoft, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LevelPill(level: NoiseLevel, modifier: Modifier = Modifier) {
    val bw = LocalBw.current
    Pill(
        "${level.label}: ${level.tag}",
        if (bw.highContrast) bw.ink else level.color,
        if (bw.highContrast) Color.White else level.soft,
        modifier,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = LocalBw.current.inkSoft, modifier = modifier)
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val bw = LocalBw.current
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BackCircle(onBack)
            trailing?.invoke()
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = bw.ink)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = bw.muted)
        }
    }
}

/** Barra apilada con los metros de cada nivel de ruido; siempre acompañada de texto. */
@Composable
fun NoiseBar(metersByLevel: DoubleArray, modifier: Modifier = Modifier) {
    val total = metersByLevel.sum().coerceAtLeast(1.0)
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(LocalBw.current.outline)) {
        NoiseLevel.entries.forEach { level ->
            val fraction = (metersByLevel[level.ordinal] / total).toFloat()
            if (fraction > 0.005f) {
                Box(Modifier.weight(fraction).height(10.dp).background(level.color))
            }
        }
    }
}

fun formatMeters(m: Double): String = if (m >= 1000) {
    String.format(Locale.forLanguageTag("es"), "%.1f km", m / 1000)
} else {
    "${(m / 10).toInt() * 10} m"
}

/** "1 banco", "3 bancos". */
fun countOf(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

fun formatMinutes(seconds: Double): String {
    val minutes = Math.ceil(seconds / 60).toInt().coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

fun timeAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - millis) / 60000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Ahora mismo"
        minutes < 60 -> "Hace $minutes min"
        minutes < 1440 -> "Hace ${minutes / 60} h"
        else -> "Hace ${minutes / 1440} d"
    }
}
