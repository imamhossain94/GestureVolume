package com.newagedevs.gesturevolume.ui.screens.upgrade

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.isLandscape
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

/**
 * The accent Pro is drawn in: the app's own primary, a little lighter at one end and a little
 * deeper at the other.
 *
 * It was gold. Gold said "offer", and it also said "a different app": the one warm colour on a
 * screen of indigo, on the page and on the crown that leads to it. Pro is part of this app, so it
 * wears this app's colour, and follows the theme into dark mode with everything else.
 */
@Composable
private fun proGradient(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    return listOf(lerp(primary, Color.White, 0.22f), primary, lerp(primary, Color.Black, 0.18f))
}

/**
 * What Pro is, and the way to buy it. Reached from the crown on the home screen's bar.
 *
 * A page rather than a dialog: three reasons, a price and a button want room, and a dialog on a
 * small phone would cut the reasons short to fit the button in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val price by viewModel.billingManager.lifetimePrice.collectAsState()
    val restoring by viewModel.billingManager.isRestoring.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upgrade_to_pro)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        // Everything under the crown: the reasons, then the button or the thanks. Shared by both
        // layouts so the purchase path is written once.
        val details: @Composable () -> Unit = {
            Benefit(
                icon = Icons.Default.Block,
                title = R.string.upgrade_ad_free_title,
                description = R.string.upgrade_ad_free_desc,
            )
            Benefit(
                icon = Icons.Default.AutoAwesome,
                title = R.string.upgrade_features_title,
                description = R.string.upgrade_features_desc,
            )
            Benefit(
                icon = Icons.Default.Favorite,
                title = R.string.upgrade_support_title,
                description = R.string.upgrade_support_desc,
            )

            Spacer(modifier = Modifier.height(24.dp))
            if (state.isProActivated) {
                Thanks()
            } else {
                BuyButton(price = price.formattedPrice) {
                    (context as? Activity)?.let { viewModel.purchasePro(it) }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.upgrade_one_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = { viewModel.billingManager.restorePurchases() },
                    enabled = !restoring
                ) {
                    Text(stringResource(R.string.upgrade_restore))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (isLandscape()) {
            // On its side the page is a strip a few rows tall, and the hero card alone filled it.
            // So the crown takes two fifths of the width and the reasons and the button the other
            // three fifths beside it, each scrolling on its own, inside the home screen's frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = LANDSCAPE_MAX_WIDTH)
                        .fillMaxHeight()
                        .padding(horizontal = SIDE_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(PANE_GAP)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(HERO_SHARE)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 24.dp)
                    ) {
                        Hero()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f - HERO_SHARE)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        details()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Hero()
                    Spacer(modifier = Modifier.height(20.dp))
                    details()
                }
            }
        }
    }
}

/** The home screen's side margin. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column. */
private val LANDSCAPE_MAX_WIDTH = 920.dp

/** Between the two panes on its side, as on the preview screens. */
private val PANE_GAP = 20.dp

/** How much of the width the hero card takes on its side. */
private const val HERO_SHARE = 0.4f

/**
 * The crown on a card, under a glow that breathes.
 *
 * The same surface, corner and padding as the cards on every other screen: the page opens from the
 * home screen's crown, and a dark gradient panel here read as a different app. What carries Pro is
 * the crown itself, in the app's own colour.
 *
 * The glow is the one moving thing on the page, and slow enough to read as light rather than as
 * something asking to be tapped.
 */
@Composable
private fun Hero() {
    val glow by rememberInfiniteTransition(label = "crown").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val primary = MaterialTheme.colorScheme.primary
    val gradient = proGradient()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to primary.copy(alpha = 0.45f * glow),
                                0.55f to primary.copy(alpha = 0.14f * glow),
                                1f to Color.Transparent,
                            ),
                            radius = size.minDimension / 2f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_crown_2),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.upgrade_title, stringResource(R.string.app_name)),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.upgrade_tagline),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** One reason, on a chip in the app's colour, like every other list of settings in it. */
@Composable
private fun Benefit(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(description),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The button, in the crown's colours: the crown's promise, priced. */
@Composable
private fun BuyButton(price: String, onClick: () -> Unit) {
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(proGradient()))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_crown_2),
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.upgrade_buy, price),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = onAccent
            )
        }
    }
}

/** What someone who already has Pro sees where the button would be. */
@Composable
private fun Thanks() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.upgrade_thanks_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.upgrade_thanks_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
