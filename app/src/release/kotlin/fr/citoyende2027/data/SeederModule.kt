package fr.citoyende2027.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release: no fixture is ever installed (AGENTS.md §2.2, §2.6). */
@Module
@InstallIn(SingletonComponent::class)
internal object SeederModule {
    @Provides
    fun providesSeeder(): StartupDataSeeder = StartupDataSeeder {}
}
