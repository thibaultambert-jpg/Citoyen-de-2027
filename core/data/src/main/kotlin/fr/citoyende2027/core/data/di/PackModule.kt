package fr.citoyende2027.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.data.BuildConfig
import fr.citoyende2027.core.data.pack.PackFileReader
import fr.citoyende2027.core.data.pack.PackInstaller
import fr.citoyende2027.core.data.pack.PackRuntime
import fr.citoyende2027.core.data.pack.PackSynchronizer
import fr.citoyende2027.core.data.pack.PackVerifier
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.database.CorePackReader
import fr.citoyende2027.core.database.LegalPackReader
import fr.citoyende2027.core.database.ProgramsPackReader
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
        coreReader: CorePackReader,
        programsReader: ProgramsPackReader,
        legalReader: LegalPackReader,
        store: CorePackStore,
    ): PackRuntime = PackRuntime(
        BuildConfig.PACKS_PUBLIC_KEY.takeIf { it.isNotBlank() }?.let { key ->
            val reader = object : PackFileReader {
                override fun readCore(file: File) = coreReader.read(file)

                override fun readPrograms(file: File) = programsReader.read(file)

                override fun readLegal(file: File) = legalReader.read(file)
            }
            PackInstaller(PackVerifier(key), reader, store, File(context.filesDir, "packs"))
        },
    )

    @Provides
    fun providesSynchronizer(http: PackHttpClient, runtime: PackRuntime): PackSynchronizer =
        PackSynchronizer(BuildConfig.PACKS_BASE_URL, http, runtime.installer)
}
