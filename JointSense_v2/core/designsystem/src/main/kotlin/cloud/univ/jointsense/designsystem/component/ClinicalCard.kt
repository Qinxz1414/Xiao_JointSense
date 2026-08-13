package cloud.univ.jointsense.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.designsystem.theme.jointSenseColors

@Composable
fun ClinicalCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.jointSenseColors.cardContainer,
    ),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.jointSenseColors.cardOutline),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

/**
 * A card that behaves as one concise accessibility action. Visual descendants
 * remain visible, while assistive technology receives only [accessibilityLabel]
 * and a button action instead of repeating every child label.
 */
@Composable
fun ClinicalCard(
    onClick: () -> Unit,
    accessibilityLabel: String,
    accessibilityTestTag: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.jointSenseColors.cardContainer,
    ),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.jointSenseColors.cardOutline),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                accessibilityTestTag?.let { this[SemanticsProperties.TestTag] = it }
                onClick(label = accessibilityLabel) {
                    onClick()
                    true
                }
            },
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}
