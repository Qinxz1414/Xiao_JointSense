package cloud.univ.jointsense.calibration

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import cloud.univ.jointsense.image.SampledBitmapDecoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class CalibrationViewModelFactory internal constructor(
    private val decoder: CalibrationBitmapDecoder,
    private val legacyRevalidator: LegacyCalibrationRevalidator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModelProvider.Factory {
    constructor(
        decoder: SampledBitmapDecoder,
        legacyRevalidator: LegacyCalibrationRevalidator,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        decoder = CalibrationBitmapDecoder { uri ->
            BitmapCalibrationImage(decoder.decode(Uri.parse(uri)).bitmap)
        },
        legacyRevalidator = legacyRevalidator,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher,
        clock = clock,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalibrationViewModel::class.java))
        return create(SavedStateHandle()) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(CalibrationViewModel::class.java))
        return create(extras.createSavedStateHandle()) as T
    }

    fun create(savedStateHandle: SavedStateHandle): CalibrationViewModel = CalibrationViewModel(
        savedStateHandle = savedStateHandle,
        decoder = decoder,
        legacyRevalidator = legacyRevalidator,
        clock = clock,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher,
    )
}
