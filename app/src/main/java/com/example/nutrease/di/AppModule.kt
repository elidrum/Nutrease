package com.example.nutrease.di

import android.content.Context
import com.example.nutrease.BuildConfig
import com.example.nutrease.data.repository.AuthRepositoryImpl
import com.example.nutrease.data.repository.BadgeStateRepositoryImpl
import com.example.nutrease.data.repository.ChatRepositoryImpl
import com.example.nutrease.data.repository.DiaryRepositoryImpl
import com.example.nutrease.data.repository.FoodRepositoryImpl
import com.example.nutrease.data.repository.LinkRequestRepositoryImpl
import com.example.nutrease.data.repository.LinkedPatientsRepositoryImpl
import com.example.nutrease.data.repository.ReminderRepositoryImpl
import com.example.nutrease.data.repository.SpecialistDirectoryRepositoryImpl
import com.example.nutrease.data.repository.SymptomRepositoryImpl
import com.example.nutrease.data.repository.UserRepositoryImpl
import com.example.nutrease.data.scheduler.AlarmManagerReminderScheduler
import com.example.nutrease.domain.repository.AuthRepository
import com.example.nutrease.domain.repository.BadgeStateRepository
import com.example.nutrease.domain.repository.ChatRepository
import com.example.nutrease.domain.repository.DiaryRepository
import com.example.nutrease.domain.repository.FoodRepository
import com.example.nutrease.domain.repository.LinkRequestRepository
import com.example.nutrease.domain.repository.LinkedPatientsRepository
import com.example.nutrease.domain.repository.ReminderRepository
import com.example.nutrease.domain.repository.ReminderScheduler
import com.example.nutrease.domain.repository.SpecialistDirectoryRepository
import com.example.nutrease.domain.repository.SymptomRepository
import com.example.nutrease.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

/**
 * Modulo Hilt unico dell'app (`SingletonComponent` = oggetti vivi quanto l'app).
 * Qui si decide quale implementazione concreta soddisfa ogni interfaccia di dominio:
 * i ViewModel/UseCase dichiarano `AuthRepository` e Hilt inietta `AuthRepositoryImpl`.
 * Tutto `@Singleton`: i repository sono stateless (o con cache volutamente condivisa,
 * come quella degli alimenti) e condividono lo stesso [SupabaseClient].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Client Supabase condiviso, con i tre plugin usati dall'app: Auth (sessione),
     * Postgrest (CRUD sulle tabelle), Realtime (chat). URL e chiave anon arrivano da
     * `local.properties` via [BuildConfig]: mai hardcodate né committate.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(supabase: SupabaseClient): AuthRepository =
        AuthRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideUserRepository(supabase: SupabaseClient): UserRepository =
        UserRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideFoodRepository(supabase: SupabaseClient): FoodRepository =
        FoodRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideDiaryRepository(supabase: SupabaseClient): DiaryRepository =
        DiaryRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideSymptomRepository(supabase: SupabaseClient): SymptomRepository =
        SymptomRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideSpecialistDirectoryRepository(supabase: SupabaseClient): SpecialistDirectoryRepository =
        SpecialistDirectoryRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideLinkRequestRepository(supabase: SupabaseClient): LinkRequestRepository =
        LinkRequestRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideLinkedPatientsRepository(supabase: SupabaseClient): LinkedPatientsRepository =
        LinkedPatientsRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideReminderRepository(supabase: SupabaseClient): ReminderRepository =
        ReminderRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideChatRepository(supabase: SupabaseClient): ChatRepository =
        ChatRepositoryImpl(supabase)

    @Provides
    @Singleton
    fun provideBadgeStateRepository(
        @ApplicationContext context: Context,
        supabase: SupabaseClient
    ): BadgeStateRepository =
        BadgeStateRepositoryImpl(context, supabase)

    @Provides
    @Singleton
    fun provideReminderScheduler(@ApplicationContext context: Context): ReminderScheduler =
        AlarmManagerReminderScheduler(context)
}