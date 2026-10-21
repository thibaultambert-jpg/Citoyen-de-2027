package fr.citoyende2027.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.data.BuildConfig
import fr.citoyende2027.core.data.pack.CorePackInstaller
import fr.citoyende2027.core.data.pack.PackRuntime
import fr.citoyende2027.core.data.pack.PackSynchronizer
import fr.citoyende2027.core.data.pack.PackVerifier
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.database.CorePackReader
import fr.citoyende2027.core.network.PackHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PackModule {
    @Provides
    @Singleton
    fun providesPackRuntime(
        @ApplicationContext context: Context,
        reader: CorePackReader,
        store: CorePackStore,
    ): PackRuntime = PackRuntime(
        BuildConfig.PACKS_PUBLIC_KEY.takeIf { it.isNotBlank() }?.let { key ->
            CorePackInstaller(PackVerifier(key), reader::read, store, File(context.filesDir, "packs"))
        },
    )

    @Provides
    fun providesSynchronizer(http: PackHttpClient, runtime: PackRuntime): PackSynchronizer =
        PackSynchronizer(BuildConfig.PACKS_BASE_URL, http, runtime.installer)
}
