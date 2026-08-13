package cloud.univ.jointsense.measurement

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.feature.measurement.R

internal enum class BuiltInSampleKind { TEST, CLIPBOARD }

internal data class BuiltInSampleDisplayName(
    val kind: BuiltInSampleKind,
    val index: Int,
)

/**
 * Resolves built-in presentation metadata without persisting localized copy.
 * Stable IDs are authoritative; legacy English names keep older database rows compatible.
 */
internal fun builtInSampleDisplayName(session: TestSession): BuiltInSampleDisplayName? {
    if (session.source != DataSource.BUILT_IN) return null
    STABLE_TEST.matchEntire(session.id)?.groupValues?.get(1)?.toIntOrNull()?.let {
        return BuiltInSampleDisplayName(BuiltInSampleKind.TEST, it)
    }
    STABLE_CLIPBOARD.matchEntire(session.id)?.groupValues?.get(1)?.toIntOrNull()?.let {
        return BuiltInSampleDisplayName(BuiltInSampleKind.CLIPBOARD, it)
    }
    LEGACY_TEST.matchEntire(session.name)?.groupValues?.get(1)?.toIntOrNull()?.let {
        return BuiltInSampleDisplayName(BuiltInSampleKind.TEST, it)
    }
    LEGACY_CLIPBOARD.matchEntire(session.name)?.groupValues?.get(1)?.toIntOrNull()?.let {
        return BuiltInSampleDisplayName(BuiltInSampleKind.CLIPBOARD, it)
    }
    return null
}

@Composable
internal fun TestSession.localizedDisplayName(): String = when (val metadata = builtInSampleDisplayName(this)) {
    null -> name
    else -> stringResource(
        when (metadata.kind) {
            BuiltInSampleKind.TEST -> R.string.measurement_builtin_test_plate
            BuiltInSampleKind.CLIPBOARD -> R.string.measurement_builtin_clipboard_plate
        },
        metadata.index,
    )
}

private val STABLE_TEST = Regex("^builtin-tc(\\d+)$")
private val STABLE_CLIPBOARD = Regex("^builtin-clip-(\\d+)$")
private val LEGACY_TEST = Regex("^Test Plate (\\d+)(?:\\s*·.*)?$")
private val LEGACY_CLIPBOARD = Regex("^Clipboard Plate (\\d+)(?:\\s*·.*)?$")
