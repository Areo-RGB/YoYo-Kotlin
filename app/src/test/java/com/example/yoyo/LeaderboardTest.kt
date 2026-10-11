package com.example.yoyo

import com.example.yoyo.domain.LeaderboardMetric
import com.example.yoyo.domain.SessionResult
import com.example.yoyo.domain.TestSession
import com.example.yoyo.domain.TestType
import com.example.yoyo.domain.buildLeaderboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun session(id: String, results: List<SessionResult>) = TestSession(
    id = id,
    testType = TestType.YoYoIr1,
    title = "Test $id",
    timestampMs = 0,
    durationSeconds = 0,
    maxDistanceAchieved = 0,
    maxLevelAchieved = "",
    totalAthletesCount = results.size,
    completedAthletesCount = results.size,
    results = results,
)

internal fun result(name: String, distance: Int) = SessionResult(
    athleteName = name,
    finalDistanceMeters = distance,
    finalLevel = "",
    rank = 1,
    vo2Max = 0.0,
)

class LeaderboardTest {
    @Test
    fun emptyWithoutSavedHistory() {
        assertTrue(buildLeaderboard(emptyList()).isEmpty())
    }

    @Test
    fun ranksPlayersByAverageDistanceFromSavedSessions() {
        val history = listOf(
            session("new", listOf(result(" Alex ", 400), result("Sam", 300))),
            session("old", listOf(result("alex", 200), result("Sam", 500), result("Jo", 350))),
        )
        val leaderboard = buildLeaderboard(history)
        assertEquals(listOf("Sam", "Jo", "Alex"), leaderboard.map { it.playerName })
        assertEquals(listOf(1, 2, 3), leaderboard.map { it.rank })
        assertEquals(listOf(400.0, 350.0, 300.0), leaderboard.map { it.averageDistanceMeters })
        assertEquals(listOf(2, 1, 2), leaderboard.map { it.sessionCount })
        assertEquals(600, leaderboard.last().totalDistanceMeters)
    }

    @Test
    fun preservesFractionalAveragesAndTiedRanks() {
        val leaderboard = buildLeaderboard(
            listOf(
                session("one", listOf(result("A", 101), result("B", 100), result("C", 99))),
                session("two", listOf(result("A", 100))),
            ),
        )
        assertEquals(listOf("A", "B", "C"), leaderboard.map { it.playerName })
        assertEquals(100.5, leaderboard.first().averageDistanceMeters, 0.0)

        val tied = buildLeaderboard(
            listOf(
                session("one", listOf(result("A", 400), result("B", 200), result("C", 100))),
                session("two", listOf(result("B", 600))),
            ),
        )
        assertEquals(listOf(1, 1, 3), tied.map { it.rank })
        assertEquals(listOf("B", "A", "C"), tied.map { it.playerName })
    }

    @Test
    fun totalDistanceRanksByTheSumOfAllSavedTests() {
        val history = listOf(
            session("recent", listOf(result("Alex", 400), result("Sam", 310))),
            session("older", listOf(result("Sam", 290))),
        )
        val averages = buildLeaderboard(history)
        assertEquals(listOf("Alex", "Sam"), averages.map { it.playerName })
        assertEquals(listOf(1, 2), averages.map { it.rank })

        val totals = buildLeaderboard(history, LeaderboardMetric.TotalDistance)
        assertEquals(listOf("Sam", "Alex"), totals.map { it.playerName })
        assertEquals(listOf(600, 400), totals.map { it.totalDistanceMeters })
        assertEquals(listOf(1, 2), totals.map { it.rank })
        assertEquals(300.0, totals.first().averageDistanceMeters, 0.0)
    }

    @Test
    fun totalDistanceTiesShareRanksIndependentOfTestCounts() {
        val totals = buildLeaderboard(
            listOf(
                session("one", listOf(result("A", 400), result("B", 150), result("C", 100))),
                session("two", listOf(result("B", 250))),
            ),
            LeaderboardMetric.TotalDistance,
        )
        assertEquals(listOf("B", "A", "C"), totals.map { it.playerName })
        assertEquals(listOf(1, 1, 3), totals.map { it.rank })
    }
}
