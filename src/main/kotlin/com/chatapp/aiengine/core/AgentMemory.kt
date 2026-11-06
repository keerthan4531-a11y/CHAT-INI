package com.chatapp.aiengine.core

import com.chatapp.aiengine.model.MemoryFact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Three-tier agent memory:
 * - **working**: the current run's observations (in memory only)
 * - **episodic**: short summaries of previous runs
 * - **semantic**: durable facts about the user/project
 *
 * Keeping the tiers separate is what stops the context from turning into a
 * giant pile of stale tool output.
 */
class AgentMemory(
    private val storageFile: File? = null
) {
    private val facts = mutableListOf<MemoryFact>()
    private val observations = mutableListOf<String>()

    init {
        load()
    }

    @Synchronized
    fun addFact(text: String, kind: String = "semantic"): MemoryFact {
        val fact = MemoryFact(text = text, kind = kind)
        facts.add(fact)
        persist()
        return fact
    }

    @Synchronized
    fun rememberObservation(text: String) {
        if (text.isBlank()) return
        observations.add(text.take(800))
        if (observations.size > 40) observations.removeAt(0)
    }

    @Synchronized
    fun recall(query: String, limit: Int = 6): List<MemoryFact> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        if (words.isEmpty()) return facts.takeLast(limit)
        return facts.sortedByDescending { fact ->
            fact.text.lowercase().split(Regex("\\W+")).count { it in words }
        }.take(limit)
    }

    @Synchronized
    fun recentObservations(limit: Int = 12): List<String> = observations.takeLast(limit)

    @Synchronized
    fun clear() {
        facts.clear()
        observations.clear()
        persist()
    }

    @Synchronized
    fun allFacts(): List<MemoryFact> = facts.toList()

    private fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val json = org.json.JSONObject(file.readText())
            val array = json.optJSONArray("facts") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                facts.add(
                    MemoryFact(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        text = item.optString("text", ""),
                        kind = item.optString("kind", "semantic"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    @Synchronized
    private fun persist() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            facts.forEach { fact ->
                array.put(
                    JSONObject()
                        .put("id", fact.id)
                        .put("text", fact.text)
                        .put("kind", fact.kind)
                        .put("createdAt", fact.createdAt)
                )
            }
            file.writeText(JSONObject().put("facts", array).toString())
        }
    }
}
