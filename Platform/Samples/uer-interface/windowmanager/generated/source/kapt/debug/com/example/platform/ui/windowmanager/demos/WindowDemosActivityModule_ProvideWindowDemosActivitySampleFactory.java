package com.example.platform.ui.windowmanager.demos;

import com.google.android.catalog.framework.base.CatalogSample;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class WindowDemosActivityModule_ProvideWindowDemosActivitySampleFactory implements Factory<CatalogSample> {
  private final WindowDemosActivityModule module;

  public WindowDemosActivityModule_ProvideWindowDemosActivitySampleFactory(
      WindowDemosActivityModule module) {
    this.module = module;
  }

  @Override
  public CatalogSample get() {
    return provideWindowDemosActivitySample(module);
  }

  public static WindowDemosActivityModule_ProvideWindowDemosActivitySampleFactory create(
      WindowDemosActivityModule module) {
    return new WindowDemosActivityModule_ProvideWindowDemosActivitySampleFactory(module);
  }

  public static CatalogSample provideWindowDemosActivitySample(WindowDemosActivityModule instance) {
    return Preconditions.checkNotNullFromProvides(instance.provideWindowDemosActivitySample());
  }
}
