package com.example.yoyo.domain

import androidx.annotation.DrawableRes
import com.example.yoyo.R

private val separator = Regex("[\\s_-]+")

private fun firstNameKey(name: String): String? =
    name.trim().lowercase().split(separator).firstOrNull()

private val avatarFiles: Map<String, Int> = mapOf(
    "alex" to R.drawable.avatar_alex_moore,
    "arturo" to R.drawable.avatar_arturo_montes_hernandez,
    "arvid" to R.drawable.avatar_arvid_lorenz,
    "finley" to R.drawable.avatar_finley_charlier,
    "jakob" to R.drawable.avatar_jakob_merkle,
    "lasse" to R.drawable.avatar_lasse_schmidt,
    "lennox" to R.drawable.avatar_lennox_niemann,
    "levi" to R.drawable.avatar_levi_petko,
    "lion" to R.drawable.avatar_lion_macak,
    "lionel" to R.drawable.avatar_lionel,
    "milan" to R.drawable.avatar_milan_wekiaroglou,
    "paul" to R.drawable.avatar_paul_miethe,
    "peter" to R.drawable.avatar_peter_grelle,
    "ethan" to R.drawable.avatar_silas_aldenhoff,
    "tayo" to R.drawable.avatar_tayo_mohammed,
    "tommy" to R.drawable.avatar_tommy,
)

private val fullNames = mapOf(
    "alex" to "Alex Moore",
    "arturo" to "Arturo Montes Hernandez",
    "arvid" to "Arvid Lorenz",
    "finley" to "Finley Charlier",
    "jakob" to "Jakob Merkle",
    "lasse" to "Lasse Schmidt",
    "lennox" to "Lennox Niemann",
    "levi" to "Levi Petko",
    "lion" to "Lion Macak",
    "lionel" to "Lionel",
    "milan" to "Milan Wekiaroglou",
    "paul" to "Paul Miethe",
    "peter" to "Peter Grelle",
    "ethan" to "Ethan",
    "tayo" to "Tayo Mohammed",
    "tommy" to "Tommy",
)

@DrawableRes
fun avatarResFor(name: String): Int? = firstNameKey(name)?.let(avatarFiles::get)

fun fullNameFor(name: String): String = firstNameKey(name)?.let(fullNames::get) ?: name

fun initialsFor(name: String): String {
    val parts = name.trim().split(separator).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size > 1) return "${parts[0][0]}${parts[1][0]}".uppercase()
    return parts.first().take(2).uppercase()
}

private val avatarColors = longArrayOf(
    0xff0f62fe, 0xff0072c3, 0xff007d79, 0xff198038,
    0xff8a3ffc, 0xffd12771, 0xfffa4d56, 0xffba4e00,
)

/** ARGB colour for an athlete without a bundled photo; stable per name. */
fun avatarColorFor(name: String): Long {
    var hash = 0
    for (code in name) {
        hash = ((hash shl 5) - hash + code.code) and 0x7fffffff
    }
    return avatarColors[hash % avatarColors.size]
}
