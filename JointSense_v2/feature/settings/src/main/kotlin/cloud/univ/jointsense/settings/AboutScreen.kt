package cloud.univ.jointsense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.feature.settings.R

@Composable
fun AboutRouteScreen(
    appVersionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.settings_about_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(ABOUT_SCREEN_TAG)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_about_version, appVersionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            AboutSection(
                title = stringResource(R.string.settings_about_method_heading),
                paragraphs = listOf(
                    stringResource(R.string.settings_about_rgb_features),
                    stringResource(R.string.settings_about_tealness),
                ),
            )
            Spacer(Modifier.height(12.dp))
            AboutSection(
                title = stringResource(R.string.settings_about_curve_heading),
                paragraphs = listOf(
                    stringResource(R.string.settings_about_curve_behavior),
                    stringResource(R.string.settings_about_factory_ranges),
                ),
            )
            Spacer(Modifier.height(12.dp))
            AboutSection(
                title = stringResource(R.string.settings_about_index_heading),
                paragraphs = listOf(stringResource(R.string.settings_about_oa_weights, 40, 35, 25)),
            )
            Spacer(Modifier.height(12.dp))
            AboutSection(
                title = stringResource(R.string.settings_about_disclaimer_heading),
                paragraphs = listOf(stringResource(R.string.research_disclaimer)),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

const val ABOUT_SCREEN_TAG = "screen_about"

@Composable
private fun AboutSection(title: String, paragraphs: List<String>) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            paragraphs.forEach { paragraph ->
                Spacer(Modifier.height(8.dp))
                Text(text = paragraph, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
