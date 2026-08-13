package cloud.univ.jointsense.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.designsystem.theme.gradeColor

@Composable
fun GradeScale(
    currentGrade: Int,
    labels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    require(labels.size == 5) { "GradeScale requires labels for grades 0 through 4" }
    val selectedGrade = currentGrade.coerceIn(0, 4)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { grade, label ->
            val selected = grade == selectedGrade
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        color = gradeColor(grade).copy(alpha = if (selected) 1f else 0.20f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .then(
                        if (selected) Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(8.dp),
                        ) else Modifier,
                    )
                    .semantics { this.selected = selected }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
