package fr.citoyende2027.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release: only the signed embedded pack, never any fixture (AGENTS.md §2.2, §2.6). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SeederModule {
    @Binds
    abstract fun bindsSeeder(impl: EmbeddedPackSeeder): StartupDataSeeder
}
