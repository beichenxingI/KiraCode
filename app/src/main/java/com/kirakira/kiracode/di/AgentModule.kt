package com.kirakira.kiracode.di

import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.agent.AgentController
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.ToolRegistry
import com.kirakira.kiracode.domain.usecase.ChatEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun provideToolRegistry(): ToolRegistry {
        return ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }
    }

    @Provides
    @Singleton
    fun provideSearchProviderChain(
        bing: com.kirakira.kiracode.data.remote.search.BingSearchProvider,
        searxng: com.kirakira.kiracode.data.remote.search.SearxngSearchProvider
    ): com.kirakira.kiracode.data.remote.search.SearchProviderChain {
        // 顺序策略：Bing 主源 → SearXNG 备用源（不并行）
        return com.kirakira.kiracode.data.remote.search.SearchProviderChain(
            primary = bing,
            fallbacks = listOf(searxng)
        )
    }

    @Provides
    @Singleton
    fun provideAgentController(
        apiService: RealApiService,
        toolRegistry: ToolRegistry,
        chatEngine: ChatEngine,
        searchChain: com.kirakira.kiracode.data.remote.search.SearchProviderChain,
        webpageFetcher: com.kirakira.kiracode.data.remote.WebpageFetcher,
        subAgentRunner: com.kirakira.kiracode.domain.usecase.SubAgentRunner
    ): AgentController {
        return AgentController(apiService, toolRegistry, chatEngine, searchChain, webpageFetcher, subAgentRunner)
    }
}
