package com.example.yoyo.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

data class Athlete(
    val id: String,
    val name: String,
    val status: AthleteStatus = AthleteStatus.Running,
    val isSelected: Boolean = true,
    val consecutiveMisses: Int = 0,
    val lastMissShuttle: Int? = null,
    val warningDistanceMeters: Int? = null,
    val warningLevel: String? = null,
    val warningTimestampMs: Int? = null,
    val finalDistanceMeters: Int? = null,
    val finalLevel: String? = null,
    val finishTimestampMs: Int? = null,
    val rank: Int? = null,
    val vo2Max: Double? = null,
) {
    /** Clears all per-test progress, keeping identity and selection. */
    fun resetForTest(): Athlete = Athlete(id = id, name = name, isSelected = isSelected)

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("status", status.key)
        put("isSelected", isSelected)
        put("consecutiveMisses", consecutiveMisses)
        put("lastMissShuttle", lastMissShuttle)
        put("warningDistanceMeters", warningDistanceMeters)
        put("warningLevel", warningLevel)
        put("warningTimestampMs", warningTimestampMs)
        put("finalDistanceMeters", finalDistanceMeters)
        put("finalLevel", finalLevel)
        put("finishTimestampMs", finishTimestampMs)
        put("rank", rank)
        put("vo2Max", vo2Max)
    }

    companion object {
        fun fromJson(json: JsonObject): Athlete = Athlete(
            id = json.string("id") ?: "athlete_${json.string("name")}",
            name = json.string("name") ?: "Athlete",
            status = AthleteStatus.fromKey(json.string("status")),
            isSelected = json.bool("isSelected") ?: true,
            consecutiveMisses = json.int("consecutiveMisses") ?: 0,
            lastMissShuttle = json.int("lastMissShuttle"),
            warningDistanceMeters = json.int("warningDistanceMeters"),
            warningLevel = json.string("warningLevel"),
            warningTimestampMs = json.int("warningTimestampMs"),
            finalDistanceMeters = json.int("finalDistanceMeters"),
            finalLevel = json.string("finalLevel"),
            finishTimestampMs = json.int("finishTimestampMs"),
            rank = json.int("rank"),
            vo2Max = json.double("vo2Max"),
        )
    }
}

private val defaultAthleteNames = listOf(
    "Ethan", "Finley", "Arvid", "Lion", "Jakob", "Paul", "Lennox", "Levi",
    "Lasse", "Milan", "Lionel", "Arturo", "Peter", "Tommy", "Alex", "Tayo",
)

fun defaultRoster(): List<Athlete> =
    defaultAthleteNames.mapIndexed { index, name -> Athlete(id = "athlete_${index + 1}", name = name) }

data class SessionResult(
    val athleteName: String,
    val finalDistanceMeters: Int,
    val finalLevel: String,
    val rank: Int,
    val vo2Max: Double,
    val warningDistanceMeters: Int? = null,
    val warningLevel: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("athleteName", athleteName)
        put("finalDistanceMeters", finalDistanceMeters)
        put("finalLevel", finalLevel)
        put("rank", rank)
        put("vo2Max", vo2Max)
        put("warningDistanceMeters", warningDistanceMeters)
        put("warningLevel", warningLevel)
    }

    companion object {
        fun fromJson(json: JsonObject): SessionResult = SessionResult(
            athleteName = json.string("athleteName") ?: "Athlete",
            finalDistanceMeters = json.int("finalDistanceMeters") ?: 0,
            finalLevel = json.string("finalLevel") ?: "",
            rank = json.int("rank") ?: 0,
            vo2Max = json.double("vo2Max") ?: 0.0,
            warningDistanceMeters = json.int("warningDistanceMeters"),
            warningLevel = json.string("warningLevel"),
        )
    }
}

data class TestSession(
    val id: String,
    val testType: TestType,
    val title: String,
    val timestampMs: Long,
    val durationSeconds: Int,
    val maxDistanceAchieved: Int,
    val maxLevelAchieved: String,
    val totalAthletesCount: Int,
    val completedAthletesCount: Int,
    val results: List<SessionResult>,
    val notes: String = "",
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("testType", testType.key)
        put("title", title)
        put("timestampMs", timestampMs)
        put("durationSeconds", durationSeconds)
        put("maxDistanceAchieved", maxDistanceAchieved)
        put("maxLevelAchieved", maxLevelAchieved)
        put("totalAthletesCount", totalAthletesCount)
        put("completedAthletesCount", completedAthletesCount)
        put("notes", notes)
        put("results", buildJsonArray { results.forEach { add(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JsonObject): TestSession = TestSession(
            id = json.string("id") ?: (System.currentTimeMillis() * 1000).toString(),
            testType = TestType.fromKey(json.string("testType")),
            title = json.string("title") ?: "Test session",
            timestampMs = json.double("timestampMs")?.toLong() ?: 0L,
            durationSeconds = json.int("durationSeconds") ?: 0,
            maxDistanceAchieved = json.int("maxDistanceAchieved") ?: 0,
            maxLevelAchieved = json.string("maxLevelAchieved") ?: "",
            totalAthletesCount = json.int("totalAthletesCount") ?: 0,
            completedAthletesCount = json.int("completedAthletesCount") ?: 0,
            notes = json.string("notes") ?: "",
            results = (json["results"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .map(SessionResult::fromJson),
        )
    }
}

private fun JsonObject.primitive(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.booleanOrNull

private fun JsonObject.double(key: String): Double? = primitive(key)?.takeUnless { it.isString }?.doubleOrNull

private fun JsonObject.int(key: String): Int? = double(key)?.toInt()
