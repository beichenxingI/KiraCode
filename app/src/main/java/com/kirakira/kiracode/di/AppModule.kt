package com.kirakira.kiracode.di

import android.content.Context
import com.kirakira.kiracode.data.local.db.AppDatabase
import com.kirakira.kiracode.data.local.preferences.PreferencesRepositoryImpl
import com.kirakira.kiracode.data.mapper.CharacterCardMapper
import com.kirakira.kiracode.data.mapper.DraftMapper
import com.kirakira.kiracode.data.mapper.MessageMapper
import com.kirakira.kiracode.data.mapper.ReferenceCardMapper
import com.kirakira.kiracode.data.mapper.SessionMapper
import com.kirakira.kiracode.data.remote.mock.MockApiService
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.data.repository.CharacterCardRepositoryImpl
import com.kirakira.kiracode.data.repository.MessageRepositoryImpl
import com.kirakira.kiracode.data.repository.ReferenceCardRepositoryImpl
import com.kirakira.kiracode.data.repository.PreferenceRepositoryImpl
import com.kirakira.kiracode.data.repository.SessionRepositoryImpl
import com.kirakira.kiracode.domain.repository.ApiService
import com.kirakira.kiracode.domain.repository.CharacterCardRepository
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.domain.repository.PreferenceRepository
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import com.kirakira.kiracode.domain.usecase.ExportCharacterCardUseCase
import com.kirakira.kiracode.domain.usecase.ChatEngine
import com.kirakira.kiracode.domain.usecase.GenerateCharacterCardUseCase
import com.kirakira.kiracode.domain.usecase.ImportCharacterCardUseCase
import com.kirakira.kiracode.domain.usecase.LoadDraftsUseCase
import com.kirakira.kiracode.domain.usecase.RenderCardToBitmapUseCase
import com.kirakira.kiracode.domain.usecase.SaveDraftUseCase

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：提供 Repository、ApiService、DataStore、Database、HTTP Server 等单例实现
 * 安装在 SingletonComponent 中，生命周期随应用进程
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 Room 数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /**
     * 提供 DataStore Preferences 仓库实现
     */
    @Provides
    @Singleton
    fun providePreferencesRepository(@ApplicationContext context: Context): PreferencesRepository {
        return PreferencesRepositoryImpl(context)
    }

    /**
     * 提供 CharacterCard Repository 实现
     */
    @Provides
    @Singleton
    fun provideCharacterCardRepository(
        database: AppDatabase,
        characterCardMapper: CharacterCardMapper,
        draftMapper: DraftMapper,
        warehouse: com.kirakira.kiracode.data.local.WarehouseStorage
    ): CharacterCardRepository {
        return CharacterCardRepositoryImpl(
            database = database,
            characterCardMapper = characterCardMapper,
            draftMapper = draftMapper,
            warehouse = warehouse
        )
    }

    /**
     * 提供 Session Repository 实现
     */
    @Provides
    @Singleton
    fun provideSessionRepository(
        database: AppDatabase,
        sessionMapper: SessionMapper
    ): SessionRepository {
        return SessionRepositoryImpl(database = database, sessionMapper = sessionMapper)
    }

    /**
     * 提供 Preference Repository 实现
     */
    @Provides
    @Singleton
    fun providePreferenceRepository(database: AppDatabase): PreferenceRepository {
        return PreferenceRepositoryImpl(database.preferenceDao())
    }

    /**
     * 提供 Message Repository 实现
     */
    @Provides
    @Singleton
    fun provideMessageRepository(
        database: AppDatabase,
        messageMapper: MessageMapper
    ): MessageRepository {
        return MessageRepositoryImpl(database = database, messageMapper = messageMapper)
    }

    /**
     * 提供 ReferenceCard Repository 实现
     */
    @Provides
    @Singleton
    fun provideReferenceCardRepository(
        database: AppDatabase,
        referenceCardMapper: ReferenceCardMapper,
        warehouse: com.kirakira.kiracode.data.local.WarehouseStorage
    ): ReferenceCardRepository {
        return ReferenceCardRepositoryImpl(database = database, referenceCardMapper = referenceCardMapper, warehouse = warehouse)
    }

    /**
     * 提供 Mock API 服务实现
     */
    @Provides
    @Singleton
    fun provideMockApiService(): MockApiService = MockApiService()

    /**
     * 提供 Real API 服务实现
     */
    @Provides
    @Singleton
    fun provideRealApiService(): RealApiService = RealApiService(RealApiService.createClient())

    /**
     * 提供 API 服务实现
     */
    @Provides
    @Singleton
    fun provideApiService(realApiService: RealApiService): ApiService {
        return realApiService
    }

    /**
     * 提供 Mapper 实例
     */
    @Provides
    @Singleton
    fun provideCharacterCardMapper(): CharacterCardMapper = CharacterCardMapper()

    @Provides
    @Singleton
    fun provideDraftMapper(): DraftMapper = DraftMapper()

    @Provides
    @Singleton
    fun provideSessionMapper(): SessionMapper = SessionMapper()

    @Provides
    @Singleton
    fun provideMessageMapper(): MessageMapper = MessageMapper()

    @Provides
    @Singleton
    fun provideReferenceCardMapper(): ReferenceCardMapper = ReferenceCardMapper()

    // ============ UseCase 提供者 ============
    // UseCase 类已用 @Inject 标注构造函数，Hilt 可自动注入。
    // 但为清晰显式声明 Provides（也可选择不写，Hilt 会自动构造）。
    // 这里因 ImportCharacterCardUseCase 无构造参数，Hilt 自动注入；
    // 其余 UseCase 有依赖参数，Hilt 也能自动注入。故无需此处重复 Provides。
    // 保留注释以说明：UseCase 注入交给 Hilt 自动完成。

    @Provides
    fun provideImportCharacterCardUseCase(): ImportCharacterCardUseCase = ImportCharacterCardUseCase()

    @Provides
    fun provideGenerateCharacterCardUseCase(
        apiService: ApiService,
        realApiService: RealApiService,
        preferencesRepository: PreferencesRepository,
        chatEngine: ChatEngine
    ): GenerateCharacterCardUseCase =
        GenerateCharacterCardUseCase(apiService, realApiService, preferencesRepository, chatEngine)

    @Provides
    fun provideSaveDraftUseCase(repository: CharacterCardRepository): SaveDraftUseCase =
        SaveDraftUseCase(repository)

    @Provides
    fun provideLoadDraftsUseCase(repository: CharacterCardRepository): LoadDraftsUseCase =
        LoadDraftsUseCase(repository)

    @Provides
    fun provideExportCharacterCardUseCase(@ApplicationContext context: Context): ExportCharacterCardUseCase =
        ExportCharacterCardUseCase(context)

    @Provides
    fun provideRenderCardToBitmapUseCase(): RenderCardToBitmapUseCase = RenderCardToBitmapUseCase()
}