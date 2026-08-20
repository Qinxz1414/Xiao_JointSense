package cloud.univ.jointsense.image

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class ResourceOwnership<T : Any>(
    initialResource: T,
    private val releaseResource: (T) -> Unit,
) {
    private var ownedResource: T? = initialResource

    val current: T
        get() = checkNotNull(ownedResource) { "Resource ownership has been transferred" }

    fun replace(replacement: T) {
        val previous = current
        if (previous === replacement) return
        ownedResource = replacement
        releaseResource(previous)
    }

    fun transfer(): T = current.also { ownedResource = null }

    fun release() {
        val resource = ownedResource ?: return
        ownedResource = null
        releaseResource(resource)
    }
}

internal fun <T : Any, R> withResourceOwnership(
    resource: T,
    release: (T) -> Unit,
    block: (ResourceOwnership<T>) -> R,
): R {
    val ownership = ResourceOwnership(resource, release)
    return try {
        block(ownership)
    } finally {
        ownership.release()
    }
}

internal suspend fun <T : Any> withContextResourceOwnership(
    dispatcher: CoroutineDispatcher,
    acquire: () -> T,
    release: (T) -> Unit,
): T {
    var owned: T? = null
    return try {
        val delivered = withContext(dispatcher) {
            acquire().also { owned = it }
        }
        owned = null
        delivered
    } finally {
        owned?.let(release)
    }
}
