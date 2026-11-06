package com.liquidtv.app.di

import android.content.Context
import androidx.room.Room
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.dao.ChannelDao
import com.liquidtv.app.data.local.dao.WatchHistoryDao
import com.liquidtv.app.data.parser.M3UParser
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.repository.ChannelRepositoryImpl
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiquidTVDatabase =
        Room.databaseBuilder(context, LiquidTVDatabase::class.java, "liquid_tv.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideChannelDao(db: LiquidTVDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideWatchHistoryDao(db: LiquidTVDatabase): WatchHistoryDao = db.watchHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "LiquidTV/1.0 (Android TV)")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://iptv-org.github.io/")
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideIptvApiService(retrofit: Retrofit): IptvApiService =
        retrofit.create(IptvApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesDataStore): UserPreferencesRepository
}

