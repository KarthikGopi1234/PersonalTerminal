package dev.personalterminal.data.repo

import android.content.Context
import dev.personalterminal.data.db.AppDatabase
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.WatchCount
import dev.personalterminal.data.db.WearLog
import dev.personalterminal.data.db.WearLogWithWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Watches, wear logs and the on-disk photo store (filesDir/watch_photos). */
class WatchRepository(private val context: Context, private val db: AppDatabase) {
    private val watchDao = db.watchDao()
    private val wearDao = db.wearLogDao()

    val photoDir: File get() = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    fun observeWatches(): Flow<List<Watch>> = watchDao.observeActive()
    fun observeAllWatches(): Flow<List<Watch>> = watchDao.observeAll()
    fun observeWatch(id: Long): Flow<Watch?> = watchDao.observeById(id)
    fun observeWearCounts(): Flow<List<WatchCount>> = watchDao.observeWearCounts()
    fun observeWearForDay(date: LocalDate): Flow<List<WearLogWithWatch>> = wearDao.observeForDay(date.toEpochDay())
    fun observeWearRange(from: LocalDate, to: LocalDate): Flow<List<WearLogWithWatch>> =
        wearDao.observeRange(from.toEpochDay(), to.toEpochDay())
    fun observeAllWear(): Flow<List<WearLogWithWatch>> = wearDao.observeAllWithWatch()
    fun observeWearForWatch(watchId: Long): Flow<List<WearLogWithWatch>> = wearDao.observeForWatch(watchId)

    suspend fun watch(id: Long): Watch? = watchDao.getById(id)

    suspend fun saveWatch(watch: Watch): Long =
        if (watch.id == 0L) watchDao.insert(watch) else { watchDao.update(watch); watch.id }

    suspend fun deleteWatch(watch: Watch) = withContext(Dispatchers.IO) {
        // remove photos belonging to this watch's logs + profile photo
        wearDao.getAll().filter { it.watchId == watch.id }.forEach { it.photoPath?.let { p -> deletePhoto(p) } }
        watch.photoPath?.let { deletePhoto(it) }
        watchDao.delete(watch)
    }

    suspend fun logWear(watchId: Long, date: LocalDate, photoPath: String? = null, note: String = ""): Long =
        wearDao.insert(WearLog(watchId = watchId, day = date.toEpochDay(), photoPath = photoPath, note = note))

    suspend fun updateWear(log: WearLog) = wearDao.update(log)

    suspend fun deleteWear(log: WearLog) = withContext(Dispatchers.IO) {
        log.photoPath?.let { deletePhoto(it) }
        wearDao.delete(log)
    }

    suspend fun wearLog(id: Long): WearLog? = wearDao.getById(id)

    /** Returns the absolute file for a stored relative photo path. */
    fun photoFile(relative: String): File = File(photoDir, relative)

    /** Creates a fresh destination file for a new photo. Returns the *relative* name. */
    fun newPhotoName(prefix: String = "wrist"): String = "${prefix}_${System.currentTimeMillis()}.jpg"

    private fun deletePhoto(relative: String) {
        runCatching { photoFile(relative).takeIf { it.exists() }?.delete() }
    }

    companion object {
        const val PHOTO_DIR = "watch_photos"
    }
}
