package com.example.platform.ui.windowmanager.demos

import com.google.android.catalog.framework.base.*

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
class WindowDemosActivityModule {

    @Provides
    @IntoSet
    fun provideWindowDemosActivitySample(): CatalogSample {
        return CatalogSample(
            "WindowManager",
            "Demonstrates how to use the Jetpack WindowManager library.",
            listOf(),
            "https://developer.android.com/jetpack/androidx/releases/window",
            "user-interface/windowmanager/src/main/java/com/example/platform/ui/windowmanager/demos/WindowDemosActivity.kt",
            "user-interface/windowmanager",
            listOf(),
            targetActivity<com.example.platform.ui.windowmanager.demos.WindowDemosActivity>(),
            0,
            "user-interface/windowmanager/src/main/java/com/example/platform/ui/windowmanager/demos/WindowDemosActivity.kt-WindowDemosActivity",
        )
    }
}