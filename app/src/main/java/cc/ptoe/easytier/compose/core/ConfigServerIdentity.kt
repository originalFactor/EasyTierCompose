package cc.ptoe.easytier.compose.core

import android.content.Context
import java.util.UUID

/** One random UUID per installation, shared by app and root sessions. */
internal object ConfigServerIdentity {
    @Synchronized
    fun get(context: Context): String {
        val file = java.io.File(context.noBackupFilesDir, "config-server-machine-id")
        val stored = if (file.exists()) file.readText().trim() else null
        if (stored != null && runCatching { UUID.fromString(stored) }.isSuccess) return stored
        val id = UUID.randomUUID().toString()
        file.writeText(id)
        return id
    }
}
