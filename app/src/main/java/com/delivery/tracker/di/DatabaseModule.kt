package com.delivery.tracker.di

import android.content.Context
import com.delivery.tracker.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()
    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideCycleDao(db: AppDatabase): ServiceCycleDao = db.serviceCycleDao()
}
