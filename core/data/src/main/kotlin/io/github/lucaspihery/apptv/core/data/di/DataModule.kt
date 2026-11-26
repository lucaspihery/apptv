package io.github.lucaspihery.apptv.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.lucaspihery.apptv.core.data.account.AccountRepository
import io.github.lucaspihery.apptv.core.data.account.DefaultAccountRepository
import io.github.lucaspihery.apptv.core.data.core.IptvCore
import io.github.lucaspihery.apptv.core.data.core.UniffiIptvCore
import io.github.lucaspihery.apptv.core.data.crypto.AesGcmCipher
import io.github.lucaspihery.apptv.core.data.crypto.KeystoreKeyProvider
import io.github.lucaspihery.apptv.core.data.crypto.SecretCipher
import io.github.lucaspihery.apptv.core.data.live.DefaultLiveRepository
import io.github.lucaspihery.apptv.core.data.live.LiveRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    abstract fun bindLiveRepository(impl: DefaultLiveRepository): LiveRepository

    @Binds
    abstract fun bindIptvCore(impl: UniffiIptvCore): IptvCore

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("account") }

        @Provides
        @Singleton
        fun provideSecretCipher(): SecretCipher = AesGcmCipher(KeystoreKeyProvider::getOrCreate)
    }
}
