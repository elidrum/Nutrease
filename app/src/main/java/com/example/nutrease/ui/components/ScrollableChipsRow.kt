package com.example.nutrease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nutrease.R

/**
 * Riga di [androidx.compose.material3.FilterChip] scorrevole orizzontalmente con un indicatore
 * visivo (sfumatura + chevron) che compare sul lato verso cui si può ancora scorrere: così
 * l'utente capisce che la lista continua oltre il bordo e non resta nascosta (problema noto su
 * tipo pasto, filtro nutrienti, intervallo date). Usata da tutti i selettori a chip dell'app
 * per uniformità.
 *
 * È solo layout: lo stato di selezione resta hoisted nei chiamanti. [edgeColor] deve combaciare
 * con lo sfondo reale dietro la riga (background dello Scaffold oppure container della Card),
 * altrimenti la sfumatura si vedrebbe come una macchia colorata.
 */
@Composable
fun ScrollableChipsRow(
    modifier: Modifier = Modifier,
    edgeColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalSpacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val canScrollStart by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollEnd by remember { derivedStateOf { scrollState.canScrollForward } }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            content = content
        )
        if (canScrollStart) {
            ScrollAffordance(
                alignment = Alignment.CenterStart,
                brush = Brush.horizontalGradient(listOf(edgeColor, Color.Transparent)),
                icon = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.scroll_hint_left)
            )
        }
        if (canScrollEnd) {
            ScrollAffordance(
                alignment = Alignment.CenterEnd,
                brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor)),
                icon = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.scroll_hint_right)
            )
        }
    }
}

@Composable
private fun BoxScope.ScrollAffordance(
    alignment: Alignment,
    brush: Brush,
    icon: ImageVector,
    contentDescription: String
) {
    // matchParentSize → alta esattamente quanto i chip (niente ambiguità di vincoli verticali).
    // Nessun pointerInput sull'overlay: i tap attraversano e raggiungono i chip sottostanti.
    Box(
        modifier = Modifier.matchParentSize(),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(36.dp)
                .background(brush),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignment == Alignment.CenterStart) Arrangement.Start else Arrangement.End
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
