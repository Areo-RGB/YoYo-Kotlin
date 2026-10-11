package com.example.yoyo.domain

/** Which saved-history distance measure determines the leaderboard order. */
enum class LeaderboardMetric { AverageDistance, TotalDistance }

/** One player's ranking across saved history (not the current unsaved test). */
data class LeaderboardEntry(
    val rank: Int,
    val playerName: String,
    val totalDistanceMeters: Int,
    val sessionCount: Int,
) {
    val averageDistanceMeters: Double get() = totalDistanceMeters.toDouble() / sessionCount
}

private class PlayerDistanceTotal(val name: String) {
    var totalDistanceMeters = 0
    var sessionCount = 0
}

/**
 * Aggregates completed results from saved sessions.
 *
 * Older history records identify athletes by name only. Group case-insensitively
 * after trimming spaces, and display the name from the most recent entry.
 */
fun buildLeaderboard(
    sessions: Iterable<TestSession>,
    metric: LeaderboardMetric = LeaderboardMetric.AverageDistance,
): List<LeaderboardEntry> {
    val totals = LinkedHashMap<String, PlayerDistanceTotal>()
    for (session in sessions) {
        for (result in session.results) {
            val name = result.athleteName.trim()
            if (name.isEmpty()) continue
            val player = totals.getOrPut(name.lowercase()) { PlayerDistanceTotal(name) }
            player.totalDistanceMeters += result.finalDistanceMeters
            player.sessionCount++
        }
    }

    // Compare the selected ranking metric; averages use exact ratios so
    // rounding the displayed value never changes an athlete's rank.
    fun compareMetric(a: PlayerDistanceTotal, b: PlayerDistanceTotal): Int =
        if (metric == LeaderboardMetric.TotalDistance) {
            b.totalDistanceMeters.compareTo(a.totalDistanceMeters)
        } else {
            (b.totalDistanceMeters.toLong() * a.sessionCount)
                .compareTo(a.totalDistanceMeters.toLong() * b.sessionCount)
        }

    val players = totals.values.sortedWith { a, b ->
        val distanceOrder = compareMetric(a, b)
        if (distanceOrder != 0) return@sortedWith distanceOrder
        val countOrder = b.sessionCount.compareTo(a.sessionCount)
        if (countOrder != 0) return@sortedWith countOrder
        a.name.lowercase().compareTo(b.name.lowercase())
    }

    val leaderboard = mutableListOf<LeaderboardEntry>()
    players.forEachIndexed { index, player ->
        val tiedWithPrevious = index > 0 && compareMetric(player, players[index - 1]) == 0
        leaderboard += LeaderboardEntry(
            rank = if (tiedWithPrevious) leaderboard.last().rank else index + 1,
            playerName = player.name,
            totalDistanceMeters = player.totalDistanceMeters,
            sessionCount = player.sessionCount,
        )
    }
    return leaderboard
}
