package fr.citoyende2027.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.database.CitoyenDatabase
import fr.citoyende2027.core.database.dao.CorePackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): CitoyenDatabase =
        Room.databaseBuilder(context, CitoyenDatabase::class.java, "citoyen.db").build()

    @Provides
    fun providesCorePackDao(database: CitoyenDatabase): CorePackDao = database.corePackDao()
}
