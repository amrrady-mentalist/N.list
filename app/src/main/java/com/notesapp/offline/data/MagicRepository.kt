package com.notesapp.offline.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MagicRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File = File(context.filesDir, "magic_effect.json")

    suspend fun load(): MagicEffect = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext MagicEffect()
        runCatching {
            json.decodeFromString<MagicEffect>(file.readText())
        }.getOrDefault(MagicEffect())
    }

    suspend fun save(effect: MagicEffect) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(effect))
    }
}
