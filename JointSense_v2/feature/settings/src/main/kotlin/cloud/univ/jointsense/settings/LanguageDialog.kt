package cloud.univ.jointsense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.feature.settings.R
import cloud.univ.jointsense.settings.locale.LanguageOption

@Composable
internal fun LanguageDialog(
    selected: LanguageOption,
    onSelect: (LanguageOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column {
                LanguageRow(
                    option = LanguageOption.SYSTEM,
                    label = stringResource(R.string.settings_language_system),
                    selected = selected,
                    testTag = LANGUAGE_SYSTEM_TAG,
                    onSelect = onSelect,
                )
                LanguageRow(
                    option = LanguageOption.SIMPLIFIED_CHINESE,
                    label = stringResource(R.string.settings_language_zh_cn),
                    selected = selected,
                    testTag = LANGUAGE_ZH_CN_TAG,
                    onSelect = onSelect,
                )
                LanguageRow(
                    option = LanguageOption.ENGLISH,
                    label = stringResource(R.string.settings_language_en),
                    selected = selected,
                    testTag = LANGUAGE_EN_TAG,
                    onSelect = onSelect,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun LanguageRow(
    option: LanguageOption,
    label: String,
    selected: LanguageOption,
    testTag: String,
    onSelect: (LanguageOption) -> Unit,
) {
    val isSelected = option == selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { onSelect(option) },
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}

const val LANGUAGE_SYSTEM_TAG = "language_system"
const val LANGUAGE_ZH_CN_TAG = "language_zh_cn"
const val LANGUAGE_EN_TAG = "language_en"
