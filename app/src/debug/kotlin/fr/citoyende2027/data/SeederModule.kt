package fr.citoyende2027.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.data.repository.CorePackStore
import fr.citoyende2027.core.testing.FictionalCandidates
import javax.inject.Inject

/**
 * Debug only: the signed embedded pack when there is one, otherwise the fictional Candidat A, B, C.
 * A real pack later downloaded by the sync replaces the fixtures.
 */
internal class DebugDataSeeder @Inject constructor(
    private val embedded: EmbeddedPackSeeder,
    private val corePackStore: CorePackStore,
) : StartupDataSeeder {
    override suspend fun seedIfNeeded() {
        if (!embedded.install() && !corePackStore.hasCorePack()) {
            corePackStore.install(FictionalCandidates.corePack)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SeederModule {
    @Binds
    abstract fun bindsSeeder(impl: DebugDataSeeder): StartupDataSeeder
}
