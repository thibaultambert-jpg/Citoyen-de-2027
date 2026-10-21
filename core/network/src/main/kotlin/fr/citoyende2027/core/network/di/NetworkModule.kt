package fr.citoyende2027.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.core.network.ConnectionFactory
import java.net.HttpURLConnection

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    fun providesConnectionFactory(): ConnectionFactory = ConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    }
}
