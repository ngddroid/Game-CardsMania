package com.puzzel.lab.cardsmania.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Every game save file goes through here, one operation at a time and in the order they were asked for:
 * - two quick moves can't land out of order (an older position overwriting a newer one),
 * - a delete after a win can't be undone by a write that was still waiting,
 * - a write goes to "<name>.tmp" first and is then renamed over the save, so killing the app mid-write leaves the old
 *   save intact instead of a half-written file.
 */
object SaveFiles {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val queue = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + queue)

    /** Queues a write. [encode] runs off the main thread, in order with every other save operation. */
    fun write(file: File, encode: () -> String) {
        scope.launch { runCatching { writeAtomic(file, encode()) } }
    }

    /** Deletes after every write queued before it has finished. */
    suspend fun delete(file: File) = withContext(queue) {
        runCatching { file.delete() }
        File(file.parentFile, file.name + TMP).delete()
    }

    /** Reads after every write queued before it has finished; null if there is no save. */
    suspend fun read(file: File): String? = withContext(queue) {
        if (!file.exists()) null else runCatching { file.readText() }.getOrNull()
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + TMP)
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private const val TMP = ".tmp"
}
