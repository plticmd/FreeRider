import io.nodle.sdk.android.Nodle

class App:Application() {
    override fun onCreate() {
        super.onCreate()
        Nodle.init(this)
    }
}
