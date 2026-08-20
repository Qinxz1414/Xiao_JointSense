package cloud.univ.jointsense.measurement

import java.math.BigInteger

fun nextSessionName(existingNames: List<String>, prefix: String): String {
    val pattern = Regex("^${Regex.escape(prefix)} #(\\d+)$")
    val highestSuffix = existingNames.asSequence()
        .mapNotNull(pattern::matchEntire)
        .mapNotNull { match -> match.groupValues[1].toBigIntegerOrNull() }
        .maxOrNull()
        ?: BigInteger.ZERO
    return "$prefix #${highestSuffix + BigInteger.ONE}"
}
