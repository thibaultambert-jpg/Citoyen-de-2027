package fr.citoyende2027.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.testing.FictionalCandidates
import javax.inject.Inject

/** Debug only: fills an empty database with the fictional Candidat A, B, C. */
internal class FictionalDataSeeder @Inject constructor(private val corePackStore: CorePackStore) : StartupDataSeeder {
    override suspend fun seedIfNeeded() {
        if (!corePackStore.hasCorePack()) corePackStore.install(FictionalCandidates.corePack)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SeederModule {
    @Binds
    abstract fun bindsSeeder(impl: FictionalDataSeeder): StartupDataSeeder
}
