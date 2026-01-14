package com.pace.legends.di

import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.data.repository.StepRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStepRepository(
        stepRepositoryImpl: StepRepositoryImpl
    ): StepRepository


    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        firebaseTrackRepository: com.pace.legends.data.repository.FirebaseTrackRepository
    ): com.pace.legends.domain.repository.TrackRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        firebaseAuthRepository: com.pace.legends.data.repository.FirebaseAuthRepository
    ): com.pace.legends.domain.repository.AuthRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(
        firebaseLeaderboardRepository: com.pace.legends.data.repository.FirebaseLeaderboardRepository
    ): com.pace.legends.domain.repository.LeaderboardRepository
    
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        firebaseUserRepository: com.pace.legends.data.repository.FirebaseUserRepository
    ): com.pace.legends.domain.repository.UserRepository
    
    @Binds
    @Singleton
    abstract fun bindLeagueRepository(
        firebaseLeagueRepository: com.pace.legends.data.repository.FirebaseLeagueRepository
    ): com.pace.legends.domain.repository.LeagueRepository
    
    @Binds
    @Singleton
    abstract fun bindStatsRepository(
        statsRepositoryImpl: com.pace.legends.data.repository.StatsRepositoryImpl
    ): com.pace.legends.domain.repository.StatsRepository
    
    @Binds
    @Singleton
    abstract fun bindSystemRepository(
        systemRepositoryImpl: com.pace.legends.data.repository.SystemRepositoryImpl
    ): com.pace.legends.domain.repository.SystemRepository
    
    @Binds
    @Singleton
    abstract fun bindBadgeRepository(
        badgeRepositoryImpl: com.pace.legends.data.repository.BadgeRepositoryImpl
    ): com.pace.legends.domain.repository.BadgeRepository
    
    @Binds
    @Singleton
    abstract fun bindStepSyncRepository(
        stepSyncRepositoryImpl: com.pace.legends.data.repository.StepSyncRepositoryImpl
    ): com.pace.legends.domain.repository.StepSyncRepository
}

