package com.mdskun.forcehabits.di

import android.content.Context
import com.mdskun.forcehabits.data.db.HabitDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): HabitDatabase =
        HabitDatabase.getInstance(ctx)   // reuses the same singleton instance

    @Provides @Singleton
    fun provideHabitDao(db: HabitDatabase) = db.habitDao()
}
