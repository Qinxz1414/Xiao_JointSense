package cloud.univ.jointsense

import android.app.Application
import cloud.univ.jointsense.di.AppContainer

class JointSenseApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
