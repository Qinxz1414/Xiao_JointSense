package cloud.univ.jointsense.measurement

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.image.SampledBitmapDecoder
import cloud.univ.jointsense.measurement.image.DurablePickedImageResolver
import cloud.univ.jointsense.measurement.image.MeasurementTempFileStore
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class MeasurementViewModelFactory(
    private val repository: TestSessionRepository,
    private val analyzer: BaselinePhotoAnalysisAdapter,
    private val decoder: MeasurementImageDecoder? = null,
    private val captureStoreFactory: (SavedStateHandle) -> MeasurementCaptureStore? = { null },
    private val pickedImageResolverFactory: (MeasurementCaptureStore?) -> MeasurementPickedImageResolver = {
        MeasurementPickedImageResolver { uri -> MeasurementImageInput(uri, null) }
    },
    private val cameraPermissionHistoryStore: CameraPermissionHistoryStore =
        VolatileCameraPermissionHistoryStore(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sessionNamePrefix: String = "Test",
    private val draftIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModelProvider.Factory {
    constructor(
        repository: TestSessionRepository,
        analyzer: BaselinePhotoAnalysisAdapter,
        decoder: SampledBitmapDecoder,
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
        sessionNamePrefix: String = "Test",
        draftIdFactory: () -> String = { UUID.randomUUID().toString() },
    ) : this(
        repository = repository,
        analyzer = analyzer,
        decoder = SampledMeasurementImageDecoder(decoder),
        captureStoreFactory = { savedStateHandle ->
            MeasurementTempFileStore(context.applicationContext, savedStateHandle)
        },
        pickedImageResolverFactory = { captureStore ->
            DurablePickedImageResolver(
                contentResolver = context.applicationContext.contentResolver,
                captureStore = requireNotNull(captureStore),
            )
        },
        cameraPermissionHistoryStore = ApplicationCameraPermissionHistoryStore(
            context.applicationContext,
        ),
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher,
        sessionNamePrefix = sessionNamePrefix,
        draftIdFactory = draftIdFactory,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MeasurementViewModel::class.java))
        return create(SavedStateHandle()) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(MeasurementViewModel::class.java))
        return create(extras.createSavedStateHandle()) as T
    }

    fun create(savedStateHandle: SavedStateHandle): MeasurementViewModel {
        val captureStore = captureStoreFactory(savedStateHandle)
        return MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = draftIdFactory,
            savedStateHandle = savedStateHandle,
            decoder = decoder,
            captureStore = captureStore,
            pickedImageResolver = pickedImageResolverFactory(captureStore),
            cameraPermissionHistoryStore = cameraPermissionHistoryStore,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher,
            sessionNamePrefix = sessionNamePrefix,
        )
    }
}
