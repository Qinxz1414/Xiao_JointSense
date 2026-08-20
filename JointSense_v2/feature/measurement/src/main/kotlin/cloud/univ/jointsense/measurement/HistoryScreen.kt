package cloud.univ.jointsense.measurement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.measurementBatchCount
import cloud.univ.jointsense.feature.measurement.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

const val SCREEN_HISTORY_TAG = "screen_history"

fun historyDeleteTag(sessionId: String): String = "history_delete_$sessionId"

fun historySessionTag(sessionId: String): String = "history_session_$sessionId"

/**
 * History Screen - Shows all saved test sessions.
 * User can tap a session to view its results or delete sessions.
 */
@Composable
fun HistoryScreen(
    sessions: List<TestSession>,
    onSessionClick: (TestSession) -> Unit,
    onDeleteSession: (TestSession) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }

    Scaffold(
        modifier = modifier.testTag(SCREEN_HISTORY_TAG),
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_history_title),
                navigationIcon = {
                    JointSenseBarAction(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.measurement_action_back), onBack)
                },
            )
        }
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.measurement_history_empty_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.measurement_history_empty_message),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions.reversed()) { session ->
                    val displayName = session.localizedDisplayName()
                    val dateLabel = dateFormat.format(Date(session.createdAt))
                    val resultCount = session.measurementBatchCount()
                    val resultCountLabel = pluralStringResource(
                        R.plurals.measurement_history_result_count_plural,
                        resultCount,
                        resultCount,
                    )
                    val factorsSummary = session.results
                        .map { it.factor.shortName }
                        .distinct()
                        .joinToString(stringResource(R.string.measurement_factor_separator))
                        .ifBlank { stringResource(R.string.measurement_history_no_factors) }
                    val openDescription = stringResource(
                        R.string.measurement_history_open_session_summary,
                        displayName,
                        dateLabel,
                        resultCountLabel,
                        factorsSummary,
                    )
                    val deleteDescription = stringResource(R.string.measurement_history_delete_session, displayName)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ClinicalCard(
                            onClick = { onSessionClick(session) },
                            accessibilityLabel = openDescription,
                            accessibilityTestTag = historySessionTag(session.id),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            HistoryOpenCardContent(
                                displayName = displayName,
                                dateLabel = dateLabel,
                                resultCountLabel = resultCountLabel,
                                factorsSummary = factorsSummary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = { onDeleteSession(session) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag(historyDeleteTag(session.id))
                                    .semantics {
                                        contentDescription = deleteDescription
                                        role = Role.Button
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryOpenCardContent(
    displayName: String,
    dateLabel: String,
    resultCountLabel: String,
    factorsSummary: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactLargeText = maxWidth < 400.dp && LocalDensity.current.fontScale >= 1.5f
        if (compactLargeText) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                HistorySessionIcon()
                Spacer(Modifier.height(12.dp))
                HistorySessionDetails(displayName, dateLabel, resultCountLabel, factorsSummary)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistorySessionIcon()
                Spacer(Modifier.width(12.dp))
                HistorySessionDetails(
                    displayName,
                    dateLabel,
                    resultCountLabel,
                    factorsSummary,
                    Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HistorySessionIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HistorySessionDetails(
    displayName: String,
    dateLabel: String,
    resultCountLabel: String,
    factorsSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(dateLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                resultCountLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(factorsSummary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
