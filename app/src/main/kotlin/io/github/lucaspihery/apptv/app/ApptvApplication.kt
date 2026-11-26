package io.github.lucaspihery.apptv.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ApptvApplication : Application(), SingletonImageLoader.Factory {

    /**
     * Logos des chaînes : petite mémoire (TV avec 1 à 2 Go de RAM), cache disque pour ne
     * pas les retélécharger à chaque ouverture, pas de fondu (fluidité au défilement).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos")).maxSizeBytes(64L * 1024 * 1024).build() }
            .build()
}
