package com.aura.player

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.aura.player.art.ArtFetcher
import com.aura.player.art.ArtKeyer
import com.aura.player.data.AuraDatabase
import com.aura.player.data.AuraSettings
import com.aura.player.data.MusicRepository
import com.aura.player.diag.CrashLog
import com.aura.player.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AuraApp : Application(), ImageLoaderFactory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repo: MusicRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var settings: AuraSettings
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        val db = Room.databaseBuilder(this, AuraDatabase::class.java, "aura.db")
            .fallbackToDestructiveMigration()
            .build()
        repo = MusicRepository(this, db)
        settings = AuraSettings(this)
        player = PlayerController(this, appScope, repo)
    }

    /** One shared loader: bounded memory cache (low-RAM friendly) + custom artwork fetcher. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtKeyer())
            add(ArtFetcher.Factory(this@AuraApp))
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.18).build() }
        .crossfade(false)
        .build()
}

val Context.auraApp: AuraApp get() = applicationContext as AuraApp
