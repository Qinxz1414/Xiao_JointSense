package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.analysis.calibration.CalibrationError
import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.analysis.calibration.ConcentrationParseResult
import cloud.univ.jointsense.analysis.calibration.parseConcentration
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun interface CalibrationBitmapDecoder {
    suspend fun decode(uri: String): CalibrationImage
}

internal fun interface CalibrationSignalDetector {
    fun detect(image: CalibrationImage, crop: CalibrationIntBounds): List<GridWellReading>
}

internal interface CalibrationImage {
    val width: Int
    val height: Int
    val isReleased: Boolean
    fun release()
}

internal class BitmapCalibrationImage(
    val bitmap: Bitmap,
) : CalibrationImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val isReleased: Boolean get() = bitmap.isRecycled

    override fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

class CalibrationViewModel internal constructor(
    private val repository: CalibrationRepository,
    private val savedStateHandle: SavedStateHandle,
    private val decoder: CalibrationBitmapDecoder?,
    private val detector: CalibrationSignalDetector = CalibrationSignalDetector { bitmap, crop ->
        val androidImage = bitmap as? BitmapCalibrationImage
            ?: error("Grid detection requires a bitmap-backed image")
        GridSignalDetector.detectGridSignals(androidImage.bitmap, crop)
    },
    private val validator: CalibrationValidator = CalibrationValidator(),
    private val legacyRevalidator: LegacyCalibrationRevalidator? = LegacyCalibrationRevalidator(repository),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(restoredState())
    internal val state: StateFlow<CalibrationUiState> = mutableState.asStateFlow()

    private var decodeGeneration = 0L
    private var detectionGeneration = 0L
    private var persistenceGeneration = 0L
    private var decodeJob: Job? = null
    private var detectionJob: Job? = null
    private var legacyRevalidationJob: Job? = null
    private var activeDetection: ActiveDetection? = null
    private var saveNavigationClaimed = false
    private var factoryRestoreNavigationClaimed = false

    init {
        startLegacyRevalidation()
        val restoredUri = state.value.imageUri
        if (restoredUri != null && decoder != null) {
            decodeImage(restoredUri, restoring = true)
        }
    }

    fun retryLegacyRevalidation() {
        if (state.value.legacyRevalidationSummary?.failures.isNullOrEmpty()) return
        startLegacyRevalidation()
    }

    fun onImageSelected(uri: String) {
        if (uri.isBlank() || state.value.isPersistenceBusy) return
        decodeImage(uri, restoring = false)
    }

    fun consumeImageReady() {
        updateState { it.copy(imageReadyToOpenCrop = false) }
    }

    internal fun updateCrop(bounds: CalibrationIntBounds) {
        if (state.value.isPersistenceBusy || state.value.isDetecting) return
        val image = state.value.image ?: return
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > image.width ||
            bounds.bottom > image.height || bounds.right <= bounds.left || bounds.bottom <= bounds.top
        ) return
        if (bounds == state.value.cropBounds) return
        updateState {
            it.copy(
                cropBounds = bounds,
                signals = emptyList(),
                validation = null,
                saveCompleted = false,
                saveDestinationAcknowledged = false,
                savedFactor = null,
            )
        }
    }

    fun detectSignals() {
        if (state.value.isPersistenceBusy) return
        val image = state.value.image ?: return
        val crop = state.value.cropBounds ?: return
        if (state.value.isDetecting) return
        val generation = ++detectionGeneration
        val operation = ActiveDetection(generation, image)
        activeDetection = operation
        updateState { it.copy(isDetecting = true, errorMessage = null) }
        val job = viewModelScope.launch {
            try {
                val readings = withContext(defaultDispatcher) { detector.detect(image, crop) }
                if (!isCurrent(operation)) return@launch
                setDetectedSignals(readings.sortedBy(GridWellReading::index).map(GridWellReading::signal))
                updateState { it.copy(isDetecting = false, signalsReadyToOpenAssign = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(operation)) {
                    updateState {
                        it.copy(
                            isDetecting = false,
                            errorMessage = error.message ?: "Unable to detect calibration wells",
                        )
                    }
                }
            }
        }
        detectionJob = job
        job.invokeOnCompletion { completeDetection(operation) }
    }

    fun consumeSignalsReady() {
        updateState { it.copy(signalsReadyToOpenAssign = false) }
    }

    internal fun setDetectedSignals(signals: List<Float>) {
        if (state.value.isPersistenceBusy) return
        if (signals == state.value.signals) return
        updateState {
            it.copy(
                signals = signals,
                validation = null,
                concentrationFieldErrors = emptySet(),
                saveCompleted = false,
                saveDestinationAcknowledged = false,
                savedFactor = null,
            )
        }
    }

    fun selectFactor(factor: InflammationFactor) {
        if (state.value.isPersistenceBusy) return
        if (factor == state.value.factor) return
        updateState {
            it.copy(
                factor = factor,
                concentrationTexts = ladderTexts(factor),
                concentrationFieldErrors = emptySet(),
                validation = null,
                saveCompleted = false,
                saveDestinationAcknowledged = false,
                savedFactor = null,
            )
        }
    }

    fun updateConcentration(index: Int, text: String) {
        if (state.value.isPersistenceBusy) return
        if (index !in state.value.concentrationTexts.indices) return
        if (text == state.value.concentrationTexts[index]) return
        updateState { current ->
            current.copy(
                concentrationTexts = current.concentrationTexts.toMutableList().also {
                    it[index] = text
                },
                concentrationFieldErrors = current.concentrationFieldErrors - index,
                validation = null,
                saveCompleted = false,
                saveDestinationAcknowledged = false,
            )
        }
    }

    /** Returns true when navigation to Review is scientifically meaningful. */
    fun review(): Boolean {
        val current = state.value
        if (current.isPersistenceBusy) return false
        val signalError = when {
            current.signals.size != REQUIRED_READING_COUNT -> CalibrationError.WrongReadingCount
            current.signals.any { !it.isFinite() } -> CalibrationError.NonFiniteSignal
            else -> null
        }
        if (signalError != null) {
            updateState {
                it.copy(validation = CalibrationValidation.Invalid(listOf(signalError)))
            }
            return false
        }
        val parsed = current.concentrationTexts.map(::parseConcentration)
        val fieldErrors = parsed.mapIndexedNotNull { index, result ->
            index.takeIf { result is ConcentrationParseResult.Invalid }
        }.toSet()
        if (fieldErrors.isNotEmpty()) {
            updateState {
                it.copy(
                    concentrationFieldErrors = fieldErrors,
                    validation = CalibrationValidation.Invalid(
                        listOf(CalibrationError.InvalidConcentration),
                    ),
                )
            }
            return false
        }
        val validation = validator.validate(
            parsed.mapIndexed { index, result ->
                CalibrationInput(
                    wellIndex = index,
                    concentration = (result as ConcentrationParseResult.Valid).concentration,
                    rawSignal = current.signals[index],
                )
            },
        )
        updateState {
            it.copy(
                concentrationFieldErrors = emptySet(),
                validation = validation,
            )
        }
        val blockingErrors = setOf(
            CalibrationError.WrongReadingCount,
            CalibrationError.InvalidConcentration,
            CalibrationError.MissingBlank,
            CalibrationError.MultipleBlanks,
            CalibrationError.DuplicateNonBlankConcentration,
            CalibrationError.NonFiniteSignal,
        )
        return validation !is CalibrationValidation.Invalid ||
            validation.errors.none(blockingErrors::contains)
    }

    fun save() {
        val valid = state.value.validation as? CalibrationValidation.Valid ?: return
        if (state.value.isPersistenceBusy || state.value.saveCompleted) return
        val factor = state.value.factor
        val generation = ++persistenceGeneration
        val calibration = Calibration(
            factor = factor,
            createdAt = clock(),
            version = 1,
            status = CalibrationStatus.ACTIVE,
            kitName = null,
            kitLot = null,
            knots = valid.knots.map { knot ->
                CalibrationKnot(
                    position = knot.wellIndex,
                    concentration = knot.concentration,
                    rawSignal = knot.rawSignal,
                    netSignal = knot.netSignal,
                    fittedSignal = knot.fittedSignal,
                    isBlank = knot.concentration == 0f,
                )
            },
        )
        updateState { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.save(calibration) }
                if (generation == persistenceGeneration) {
                    updateState {
                        it.copy(
                            isSaving = false,
                            saveCompleted = true,
                            saveDestinationAcknowledged = false,
                            savedFactor = factor,
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (viewModelScope.coroutineContext[Job]?.isActive != true) throw error
                if (generation == persistenceGeneration) {
                    updateState {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Calibration save was cancelled",
                        )
                    }
                }
            } catch (error: Exception) {
                if (generation == persistenceGeneration) {
                    updateState {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Unable to save calibration",
                        )
                    }
                }
            }
        }
    }

    fun claimSaveNavigation(): Boolean {
        if (!state.value.saveCompleted || state.value.saveDestinationAcknowledged || saveNavigationClaimed) {
            return false
        }
        saveNavigationClaimed = true
        return true
    }

    fun acknowledgeSaveDestination() {
        if (!state.value.saveCompleted || state.value.saveDestinationAcknowledged) return
        saveNavigationClaimed = true
        updateState { it.copy(saveDestinationAcknowledged = true) }
    }

    fun resetForAnotherFactor() {
        if (state.value.isPersistenceBusy) return
        persistenceGeneration += 1
        decodeGeneration += 1
        detectionGeneration += 1
        decodeJob?.cancel()
        invalidateActiveDetection(releaseImage = true)
        releaseStateImageUnlessBorrowed()
        saveNavigationClaimed = false
        factoryRestoreNavigationClaimed = false
        updateState {
            CalibrationUiState(
                factor = InflammationFactor.TNF_ALPHA,
                concentrationTexts = ladderTexts(InflammationFactor.TNF_ALPHA),
            )
        }
    }

    fun confirmRestoreFactory() {
        if (state.value.isPersistenceBusy) return
        val generation = ++persistenceGeneration
        updateState { it.copy(isRestoringFactory = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.clearAll() }
                if (generation == persistenceGeneration) {
                    invalidateActiveDetection(releaseImage = true)
                    releaseStateImageUnlessBorrowed()
                    saveNavigationClaimed = false
                    factoryRestoreNavigationClaimed = false
                    updateState {
                        CalibrationUiState(
                            factor = InflammationFactor.TNF_ALPHA,
                            concentrationTexts = ladderTexts(InflammationFactor.TNF_ALPHA),
                            factoryRestoreCompleted = true,
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (viewModelScope.coroutineContext[Job]?.isActive != true) throw error
                if (generation == persistenceGeneration) {
                    updateState {
                        it.copy(
                            isRestoringFactory = false,
                            errorMessage = error.message ?: "Factory restore was cancelled",
                        )
                    }
                }
            } catch (error: Exception) {
                if (generation == persistenceGeneration) {
                    updateState {
                        it.copy(
                            isRestoringFactory = false,
                            errorMessage = error.message ?: "Unable to restore factory curves",
                        )
                    }
                }
            }
        }
    }

    fun claimFactoryRestoreNavigation(): Boolean {
        if (!state.value.factoryRestoreCompleted || factoryRestoreNavigationClaimed) return false
        factoryRestoreNavigationClaimed = true
        return true
    }

    override fun onCleared() {
        decodeJob?.cancel()
        invalidateActiveDetection(releaseImage = true)
        releaseStateImageUnlessBorrowed()
        super.onCleared()
    }

    private fun startLegacyRevalidation() {
        val revalidator = legacyRevalidator ?: return
        if (legacyRevalidationJob?.isActive == true) return
        updateState { it.copy(isRevalidatingLegacy = true) }
        legacyRevalidationJob = viewModelScope.launch {
            try {
                val summary = withContext(ioDispatcher) { revalidator.revalidateNeedsReview() }
                updateState {
                    it.copy(
                        legacyRevalidationSummary = summary,
                        isRevalidatingLegacy = false,
                        errorMessage = if (summary.failures.isEmpty()) null else {
                            "Some legacy calibrations could not be reviewed automatically"
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateState {
                    it.copy(
                        isRevalidatingLegacy = false,
                        errorMessage = error.message
                            ?: "Unable to review legacy calibrations automatically",
                    )
                }
            }
        }
    }

    private fun decodeImage(uri: String, restoring: Boolean) {
        val imageDecoder = decoder ?: return
        decodeJob?.cancel()
        invalidateActiveDetection(releaseImage = true)
        val generation = ++decodeGeneration
        detectionGeneration += 1
        releaseStateImageUnlessBorrowed()
        updateState {
            it.copy(
                imageUri = uri,
                image = null,
                cropBounds = if (restoring) it.cropBounds else null,
                signals = if (restoring) it.signals else emptyList(),
                validation = if (restoring) it.validation else null,
                saveCompleted = if (restoring) it.saveCompleted else false,
                saveDestinationAcknowledged = if (restoring) {
                    it.saveDestinationAcknowledged
                } else {
                    false
                },
                savedFactor = if (restoring) it.savedFactor else null,
                isDecoding = true,
                isDetecting = false,
                imageReadyToOpenCrop = false,
                signalsReadyToOpenAssign = false,
                errorMessage = null,
            )
        }
        decodeJob = viewModelScope.launch {
            var decoded: CalibrationImage? = null
            try {
                // SampledBitmapDecoder owns its dispatcher hand-off and releases an allocation
                // if cancellation wins before delivery. Do not wrap this suspend boundary again.
                decoded = imageDecoder.decode(uri)
                if (generation != decodeGeneration) return@launch
                require(decoded.width > 0 && decoded.height > 0) { "Decoded image is empty" }
                val restoredCrop = state.value.cropBounds?.takeIf { crop ->
                    crop.left >= 0 && crop.top >= 0 && crop.right <= decoded.width &&
                        crop.bottom <= decoded.height && crop.right > crop.left && crop.bottom > crop.top
                }
                val restoredCropInvalid = restoring && state.value.cropBounds != null && restoredCrop == null
                val crop = restoredCrop ?: defaultCrop(decoded)
                updateState {
                    it.copy(
                        image = decoded,
                        cropBounds = crop,
                        validation = if (restoredCropInvalid) null else it.validation,
                        isDecoding = false,
                        imageReadyToOpenCrop = !restoring,
                        errorMessage = if (restoredCropInvalid) {
                            "Saved crop no longer fits this image; review calibration again"
                        } else {
                            null
                        },
                    )
                }
                decoded = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == decodeGeneration) {
                    updateState {
                        it.copy(
                            isDecoding = false,
                            validation = null,
                            errorMessage = error.message ?: "Unable to read calibration image",
                        )
                    }
                }
            } finally {
                decoded?.release()
            }
        }
    }

    private fun defaultCrop(image: CalibrationImage): CalibrationIntBounds {
        val left = image.width / 4
        val top = image.height / 4
        return CalibrationIntBounds(
            left = left,
            top = top,
            right = maxOf(image.width * 3 / 4, left + 1).coerceAtMost(image.width),
            bottom = maxOf(image.height * 3 / 4, top + 1).coerceAtMost(image.height),
        )
    }

    private fun isCurrent(operation: ActiveDetection): Boolean =
        activeDetection === operation &&
            operation.generation == detectionGeneration &&
            !operation.isInvalidated()

    private fun invalidateActiveDetection(releaseImage: Boolean) {
        activeDetection?.invalidate(releaseImage)
        detectionJob?.cancel()
    }

    private fun releaseStateImageUnlessBorrowed() {
        val image = state.value.image ?: return
        if (activeDetection?.image !== image) image.release()
    }

    private fun completeDetection(operation: ActiveDetection) {
        if (activeDetection === operation) {
            activeDetection = null
            detectionJob = null
        }
        operation.releaseIfRequested()
    }

    private fun restoredState(): CalibrationUiState {
        val factor = savedStateHandle.get<String>(KEY_FACTOR)
            ?.let { runCatching { InflammationFactor.valueOf(it) }.getOrNull() }
            ?: InflammationFactor.TNF_ALPHA
        val texts = savedStateHandle.get<ArrayList<String>>(KEY_CONCENTRATIONS)
            ?.takeIf { it.size == FACTORY_LADDER.getValue(factor).size }
            ?.toList()
            ?: ladderTexts(factor)
        val signals = savedStateHandle.get<FloatArray>(KEY_SIGNALS)?.toList().orEmpty()
        val savedFactor = savedStateHandle.get<String>(KEY_SAVED_FACTOR)
            ?.let { runCatching { InflammationFactor.valueOf(it) }.getOrNull() }
        val crop = if (listOf(KEY_CROP_LEFT, KEY_CROP_TOP, KEY_CROP_RIGHT, KEY_CROP_BOTTOM).all {
                savedStateHandle.contains(it)
            }
        ) {
            CalibrationIntBounds(
                savedStateHandle[KEY_CROP_LEFT] ?: 0,
                savedStateHandle[KEY_CROP_TOP] ?: 0,
                savedStateHandle[KEY_CROP_RIGHT] ?: 0,
                savedStateHandle[KEY_CROP_BOTTOM] ?: 0,
            )
        } else {
            null
        }
        val base = CalibrationUiState(
            factor = factor,
            concentrationTexts = texts,
            signals = signals,
            imageUri = savedStateHandle[KEY_IMAGE_URI],
            cropBounds = crop,
            saveCompleted = savedStateHandle.get<Boolean>(KEY_SAVE_COMPLETED) ?: false,
            saveDestinationAcknowledged =
                savedStateHandle.get<Boolean>(KEY_SAVE_DESTINATION_ACKNOWLEDGED) ?: false,
            savedFactor = savedFactor,
            factoryRestoreCompleted =
                savedStateHandle.get<Boolean>(KEY_FACTORY_RESTORE_COMPLETED) ?: false,
        )
        if (savedStateHandle.get<Boolean>(KEY_REVIEWED) != true) return base
        val parsed = texts.map(::parseConcentration)
        val fieldErrors = parsed.mapIndexedNotNull { index, result ->
            index.takeIf { result is ConcentrationParseResult.Invalid }
        }.toSet()
        val validation = when {
            signals.size != REQUIRED_READING_COUNT ->
                CalibrationValidation.Invalid(listOf(CalibrationError.WrongReadingCount))
            signals.any { !it.isFinite() } ->
                CalibrationValidation.Invalid(listOf(CalibrationError.NonFiniteSignal))
            fieldErrors.isNotEmpty() ->
                CalibrationValidation.Invalid(listOf(CalibrationError.InvalidConcentration))
            else -> {
            validator.validate(
                parsed.mapIndexed { index, result ->
                    CalibrationInput(
                        wellIndex = index,
                        concentration = (result as ConcentrationParseResult.Valid).concentration,
                        rawSignal = signals[index],
                    )
                },
            )
            }
        }
        return base.copy(
            concentrationFieldErrors = fieldErrors,
            validation = validation,
        )
    }

    private fun updateState(transform: (CalibrationUiState) -> CalibrationUiState) {
        mutableState.value = transform(mutableState.value)
        persist(mutableState.value)
    }

    private fun persist(state: CalibrationUiState) {
        savedStateHandle[KEY_FACTOR] = state.factor.name
        savedStateHandle[KEY_CONCENTRATIONS] = ArrayList(state.concentrationTexts)
        savedStateHandle[KEY_SIGNALS] = state.signals.toFloatArray()
        savedStateHandle[KEY_IMAGE_URI] = state.imageUri
        savedStateHandle[KEY_REVIEWED] = state.validation != null
        savedStateHandle[KEY_SAVE_COMPLETED] = state.saveCompleted
        savedStateHandle[KEY_SAVE_DESTINATION_ACKNOWLEDGED] = state.saveDestinationAcknowledged
        savedStateHandle[KEY_SAVED_FACTOR] = state.savedFactor?.name
        savedStateHandle[KEY_FACTORY_RESTORE_COMPLETED] = state.factoryRestoreCompleted
        state.cropBounds?.let { crop ->
            savedStateHandle[KEY_CROP_LEFT] = crop.left
            savedStateHandle[KEY_CROP_TOP] = crop.top
            savedStateHandle[KEY_CROP_RIGHT] = crop.right
            savedStateHandle[KEY_CROP_BOTTOM] = crop.bottom
        } ?: run {
            savedStateHandle.remove<Int>(KEY_CROP_LEFT)
            savedStateHandle.remove<Int>(KEY_CROP_TOP)
            savedStateHandle.remove<Int>(KEY_CROP_RIGHT)
            savedStateHandle.remove<Int>(KEY_CROP_BOTTOM)
        }
    }

    private companion object {
        const val REQUIRED_READING_COUNT = 9
        const val KEY_FACTOR = "calibration.factor"
        const val KEY_CONCENTRATIONS = "calibration.concentrations"
        const val KEY_SIGNALS = "calibration.signals"
        const val KEY_IMAGE_URI = "calibration.imageUri"
        const val KEY_REVIEWED = "calibration.reviewed"
        const val KEY_SAVE_COMPLETED = "calibration.saveCompleted"
        const val KEY_SAVE_DESTINATION_ACKNOWLEDGED = "calibration.saveDestinationAcknowledged"
        const val KEY_SAVED_FACTOR = "calibration.savedFactor"
        const val KEY_FACTORY_RESTORE_COMPLETED = "calibration.factoryRestoreCompleted"
        const val KEY_CROP_LEFT = "calibration.crop.left"
        const val KEY_CROP_TOP = "calibration.crop.top"
        const val KEY_CROP_RIGHT = "calibration.crop.right"
        const val KEY_CROP_BOTTOM = "calibration.crop.bottom"
    }

    private class ActiveDetection(
        val generation: Long,
        val image: CalibrationImage,
    ) {
        private var invalidated = false
        private var releaseWhenFinished = false
        private var imageReleased = false

        @Synchronized
        fun invalidate(releaseImage: Boolean) {
            invalidated = true
            releaseWhenFinished = releaseWhenFinished || releaseImage
        }

        @Synchronized
        fun isInvalidated(): Boolean = invalidated

        @Synchronized
        fun releaseIfRequested() {
            if (!releaseWhenFinished || imageReleased) return
            imageReleased = true
            image.release()
        }
    }
}

internal fun formatConcentration(value: Float): String =
    if (value % 1f == 0f) String.format(Locale.ROOT, "%.0f", value) else value.toString()

private fun ladderTexts(factor: InflammationFactor): List<String> =
    FACTORY_LADDER.getValue(factor).map(::formatConcentration)
