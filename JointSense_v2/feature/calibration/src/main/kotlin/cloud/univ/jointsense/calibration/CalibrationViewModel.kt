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
    suspend fun decode(uri: String): Bitmap
}

internal fun interface CalibrationSignalDetector {
    fun detect(bitmap: Bitmap, crop: CalibrationIntBounds): List<GridWellReading>
}

class CalibrationViewModel internal constructor(
    private val repository: CalibrationRepository,
    private val savedStateHandle: SavedStateHandle,
    private val decoder: CalibrationBitmapDecoder?,
    private val detector: CalibrationSignalDetector = CalibrationSignalDetector { bitmap, crop ->
        GridSignalDetector.detectGridSignals(bitmap, crop)
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

    init {
        legacyRevalidator?.let { revalidator ->
            viewModelScope.launch(ioDispatcher) {
                runCatching { revalidator.revalidateNeedsReview() }
            }
        }
        val restoredUri = state.value.imageUri
        if (restoredUri != null && decoder != null) {
            decodeImage(restoredUri, restoring = true)
        }
    }

    fun onImageSelected(uri: String) {
        if (uri.isBlank() || state.value.isSaving || state.value.isRestoringFactory) return
        decodeImage(uri, restoring = false)
    }

    fun consumeImageReady() {
        updateState { it.copy(imageReadyToOpenCrop = false) }
    }

    internal fun updateCrop(bounds: CalibrationIntBounds) {
        val bitmap = state.value.bitmap ?: return
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > bitmap.width ||
            bounds.bottom > bitmap.height || bounds.width <= 0 || bounds.height <= 0
        ) return
        updateState { it.copy(cropBounds = bounds, signals = emptyList(), validation = null) }
    }

    fun detectSignals() {
        val bitmap = state.value.bitmap ?: return
        val crop = state.value.cropBounds ?: return
        if (state.value.isDetecting) return
        val generation = ++detectionGeneration
        updateState { it.copy(isDetecting = true, errorMessage = null) }
        detectionJob = viewModelScope.launch {
            try {
                val readings = withContext(defaultDispatcher) { detector.detect(bitmap, crop) }
                if (generation != detectionGeneration || state.value.bitmap !== bitmap) return@launch
                setDetectedSignals(readings.sortedBy(GridWellReading::index).map(GridWellReading::signal))
                updateState { it.copy(isDetecting = false, signalsReadyToOpenAssign = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == detectionGeneration) {
                    updateState {
                        it.copy(
                            isDetecting = false,
                            errorMessage = error.message ?: "Unable to detect calibration wells",
                        )
                    }
                }
            }
        }
    }

    fun consumeSignalsReady() {
        updateState { it.copy(signalsReadyToOpenAssign = false) }
    }

    internal fun setDetectedSignals(signals: List<Float>) {
        updateState {
            it.copy(
                signals = signals,
                validation = null,
                concentrationFieldErrors = emptySet(),
                saveCompleted = false,
                savedFactor = null,
            )
        }
    }

    fun selectFactor(factor: InflammationFactor) {
        if (factor == state.value.factor) return
        updateState {
            it.copy(
                factor = factor,
                concentrationTexts = ladderTexts(factor),
                concentrationFieldErrors = emptySet(),
                validation = null,
                saveCompleted = false,
                savedFactor = null,
            )
        }
    }

    fun updateConcentration(index: Int, text: String) {
        if (index !in state.value.concentrationTexts.indices) return
        updateState { current ->
            current.copy(
                concentrationTexts = current.concentrationTexts.toMutableList().also {
                    it[index] = text
                },
                concentrationFieldErrors = current.concentrationFieldErrors - index,
                validation = null,
                saveCompleted = false,
            )
        }
    }

    /** Returns true when navigation to Review is scientifically meaningful. */
    fun review(): Boolean {
        val current = state.value
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
                    rawSignal = current.signals.getOrElse(index) { Float.NaN },
                )
            },
        )
        updateState {
            it.copy(
                concentrationFieldErrors = emptySet(),
                validation = validation,
                saveCompleted = false,
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
        if (state.value.isSaving || state.value.saveCompleted) return
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
                            savedFactor = factor,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
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

    fun consumeSaveCompleted() {
        updateState { it.copy(saveCompleted = false) }
    }

    fun resetForAnotherFactor() {
        persistenceGeneration += 1
        decodeGeneration += 1
        detectionGeneration += 1
        decodeJob?.cancel()
        detectionJob?.cancel()
        recycleCurrentBitmap()
        updateState {
            CalibrationUiState(
                factor = InflammationFactor.TNF_ALPHA,
                concentrationTexts = ladderTexts(InflammationFactor.TNF_ALPHA),
            )
        }
    }

    fun confirmRestoreFactory() {
        if (state.value.isRestoringFactory) return
        val generation = ++persistenceGeneration
        updateState { it.copy(isRestoringFactory = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.clearAll() }
                if (generation == persistenceGeneration) {
                    recycleCurrentBitmap()
                    updateState {
                        CalibrationUiState(
                            factor = InflammationFactor.TNF_ALPHA,
                            concentrationTexts = ladderTexts(InflammationFactor.TNF_ALPHA),
                            factoryRestoreCompleted = true,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
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

    fun consumeFactoryRestoreCompleted() {
        updateState { it.copy(factoryRestoreCompleted = false) }
    }

    override fun onCleared() {
        recycleCurrentBitmap()
        super.onCleared()
    }

    private fun decodeImage(uri: String, restoring: Boolean) {
        val imageDecoder = decoder ?: return
        decodeJob?.cancel()
        detectionJob?.cancel()
        val generation = ++decodeGeneration
        detectionGeneration += 1
        if (!restoring) recycleCurrentBitmap()
        updateState {
            it.copy(
                imageUri = uri,
                bitmap = null,
                cropBounds = if (restoring) it.cropBounds else null,
                signals = if (restoring) it.signals else emptyList(),
                validation = null,
                isDecoding = true,
                imageReadyToOpenCrop = false,
                errorMessage = null,
            )
        }
        decodeJob = viewModelScope.launch {
            var decoded: Bitmap? = null
            try {
                decoded = withContext(ioDispatcher) { imageDecoder.decode(uri) }
                if (generation != decodeGeneration) return@launch
                val restoredCrop = state.value.cropBounds?.takeIf { crop ->
                    crop.left >= 0 && crop.top >= 0 && crop.right <= decoded.width &&
                        crop.bottom <= decoded.height && crop.width > 0 && crop.height > 0
                }
                val crop = restoredCrop ?: CalibrationIntBounds(
                    decoded.width / 4,
                    decoded.height / 4,
                    decoded.width * 3 / 4,
                    decoded.height * 3 / 4,
                )
                updateState {
                    it.copy(
                        bitmap = decoded,
                        cropBounds = crop,
                        isDecoding = false,
                        imageReadyToOpenCrop = !restoring,
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
                            errorMessage = error.message ?: "Unable to read calibration image",
                        )
                    }
                }
            } finally {
                decoded?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }
    }

    private fun recycleCurrentBitmap() {
        mutableState.value.bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
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
            savedFactor = savedFactor,
        )
        if (savedStateHandle.get<Boolean>(KEY_REVIEWED) != true) return base
        val parsed = texts.map(::parseConcentration)
        val fieldErrors = parsed.mapIndexedNotNull { index, result ->
            index.takeIf { result is ConcentrationParseResult.Invalid }
        }.toSet()
        val validation = if (fieldErrors.isNotEmpty()) {
            CalibrationValidation.Invalid(listOf(CalibrationError.InvalidConcentration))
        } else {
            validator.validate(
                parsed.mapIndexed { index, result ->
                    CalibrationInput(
                        wellIndex = index,
                        concentration = (result as ConcentrationParseResult.Valid).concentration,
                        rawSignal = signals.getOrElse(index) { Float.NaN },
                    )
                },
            )
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
        savedStateHandle[KEY_SAVED_FACTOR] = state.savedFactor?.name
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
        const val KEY_FACTOR = "calibration.factor"
        const val KEY_CONCENTRATIONS = "calibration.concentrations"
        const val KEY_SIGNALS = "calibration.signals"
        const val KEY_IMAGE_URI = "calibration.imageUri"
        const val KEY_REVIEWED = "calibration.reviewed"
        const val KEY_SAVE_COMPLETED = "calibration.saveCompleted"
        const val KEY_SAVED_FACTOR = "calibration.savedFactor"
        const val KEY_CROP_LEFT = "calibration.crop.left"
        const val KEY_CROP_TOP = "calibration.crop.top"
        const val KEY_CROP_RIGHT = "calibration.crop.right"
        const val KEY_CROP_BOTTOM = "calibration.crop.bottom"
    }
}

internal fun formatConcentration(value: Float): String =
    if (value % 1f == 0f) String.format(Locale.ROOT, "%.0f", value) else value.toString()

private fun ladderTexts(factor: InflammationFactor): List<String> =
    FACTORY_LADDER.getValue(factor).map(::formatConcentration)
