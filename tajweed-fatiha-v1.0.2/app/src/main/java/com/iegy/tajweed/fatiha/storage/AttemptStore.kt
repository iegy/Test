package com.iegy.tajweed.fatiha.storage

import android.content.Context
import com.iegy.tajweed.fatiha.engine.AnalysisResult

class AttemptStore(context: Context) {
    data class Item(
        val time: Long,
        val ayah: Int,
        val accepted: Boolean,
        val status: String,
        val confidence: Double,
        val title: String
    )

    private val prefs = context.getSharedPreferences("tajweed_attempts_v2", Context.MODE_PRIVATE)

    fun add(ayah: Int, result: AnalysisResult) {
        val items = list().toMutableList()
        items.add(0, Item(System.currentTimeMillis(), ayah, result.accepted, result.overall.name, result.confidence, result.title))
        val encoded = items.take(20).joinToString("\n") { encode(it) }
        prefs.edit().putString("items", encoded).apply()
    }

    fun list(): List<Item> = prefs.getString("items", "").orEmpty()
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { decode(it) }
        .toList()

    private fun encode(i: Item): String = listOf(
        i.time.toString(), i.ayah.toString(), if (i.accepted) "1" else "0", i.status,
        i.confidence.toString(), i.title.replace("|", " ").replace("\n", " ")
    ).joinToString("|")

    private fun decode(s: String): Item? = runCatching {
        val p = s.split("|", limit = 6)
        Item(p[0].toLong(), p[1].toInt(), p[2] == "1", p[3], p[4].toDouble(), p[5])
    }.getOrNull()
}
