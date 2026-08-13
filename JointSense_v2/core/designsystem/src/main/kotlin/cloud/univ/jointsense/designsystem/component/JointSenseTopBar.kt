package cloud.univ.jointsense.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cloud.univ.jointsense.designsystem.theme.jointSenseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointSenseTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val semanticColors = MaterialTheme.jointSenseColors
    TopAppBar(
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = semanticColors.topBarContainer,
            scrolledContainerColor = semanticColors.topBarContainer,
            titleContentColor = semanticColors.onTopBar,
            navigationIconContentColor = semanticColors.onTopBar,
            actionIconContentColor = semanticColors.onTopBar,
        ),
    )
}

@Composable
fun JointSenseBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
