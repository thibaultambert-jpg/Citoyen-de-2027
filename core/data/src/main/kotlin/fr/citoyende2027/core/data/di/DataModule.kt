package fr.citoyende2027.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.data.repository.AskRepository
import fr.citoyende2027.core.data.repository.CandidatesRepository
import fr.citoyende2027.core.data.repository.DataStoreUserPreferencesRepository
import fr.citoyende2027.core.data.repository.LegalRepository
import fr.citoyende2027.core.data.repository.OfflineAskRepository
import fr.citoyende2027.core.data.repository.OfflineCandidatesRepository
import fr.citoyende2027.core.data.repository.OfflineLegalRepository
import fr.citoyende2027.core.data.repository.UserPreferencesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindsCandidatesRepository(impl: OfflineCandidatesRepository): CandidatesRepository

    @Binds
    abstract fun bindsAskRepository(impl: OfflineAskRepository): AskRepository

    @Binds
    abstract fun bindsLegalRepository(impl: OfflineLegalRepository): LegalRepository

    @Binds
    abstract fun bindsUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun providesPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_preferences") }
    }
}
