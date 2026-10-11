package com.newagedevs.gesturevolume.ui.screens.tutorials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.KitRow
import com.newagedevs.gesturevolume.ui.components.RowGroup
import com.newagedevs.gesturevolume.ui.components.Tutorial
import com.newagedevs.gesturevolume.ui.components.cardShape
import com.newagedevs.gesturevolume.ui.components.rememberTutorialOpener
import com.newagedevs.gesturevolume.ui.motion.IconButton

/**
 * The video tutorials, all nine in the order the series plays: for whoever the demos on each
 * screen did not reach.
 *
 * Every row says how long its video is, and the note at the top says where it opens, before
 * anything is tapped. A row that quietly opens another app is a surprise, and someone on a metered
 * connection should know a video is coming. The text in the videos is English, and the note says
 * so in the reader's own language rather than leaving them to find out.
 *
 * Reached from the drawer and from the top of the FAQ; each feature screen also has its own video
 * as a ▶ in its top bar (see [com.newagedevs.gesturevolume.ui.components.TutorialAction]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialsScreen(onNavigateBack: () -> Unit) {
    val open = rememberTutorialOpener()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tutorials)) },
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
        // The FAQ's frame, so the two help screens line up: see FaqScreen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tutorials_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 20.dp),
                )

                val tutorials = Tutorial.entries
                RowGroup {
                    tutorials.forEachIndexed { index, tutorial ->
                        KitRow(
                            title = stringResource(tutorial.title),
                            description = stringResource(tutorial.summary),
                            shape = cardShape(index, tutorials.size),
                            leading = { IconTile(iconFor(tutorial)) },
                            onClick = { open(tutorial) },
                            trailing = {
                                Text(
                                    text = tutorial.duration(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                // Leaves the app, rather than the chevron's "goes deeper in it".
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The home screen's widest column, as on the FAQ. */
private val CONTENT_MAX_WIDTH = 920.dp

/** The picture the FAQ gives the same feature, where it has one, so the two screens agree. */
private fun iconFor(tutorial: Tutorial): ImageVector = when (tutorial) {
    Tutorial.GETTING_STARTED -> Icons.Filled.RocketLaunch
    Tutorial.SWIPE_VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
    Tutorial.QUICK_SLIDER -> Icons.Filled.Tune
    Tutorial.DECK -> Icons.AutoMirrored.Filled.ViewSidebar
    Tutorial.SEARCH -> Icons.Filled.Search
    Tutorial.MENU -> Icons.Filled.Menu
    Tutorial.ACTIONS -> Icons.Filled.Swipe
    Tutorial.APPEARANCE -> Icons.Filled.Palette
    Tutorial.VISIBILITY -> Icons.Filled.VisibilityOff
}
