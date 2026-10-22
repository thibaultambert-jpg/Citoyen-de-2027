package fr.citoyende2027.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.citoyende2027.llm.assistant.Assistant
import fr.citoyende2027.llm.assistant.DefaultAssistant
import fr.citoyende2027.llm.benchmark.Benchmark
import fr.citoyende2027.llm.benchmark.LlmBenchmark
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface LlmModule {
    @Binds
    @Singleton
    fun assistant(impl: DefaultAssistant): Assistant

    @Binds
    fun benchmark(impl: LlmBenchmark): Benchmark
}
