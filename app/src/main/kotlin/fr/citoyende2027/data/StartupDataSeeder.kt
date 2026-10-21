package fr.citoyende2027.data

/**
 * Installs initial data at startup. Debug builds install the fictional fixtures; release builds
 * install nothing until the embedded official `core` pack arrives (J2).
 */
fun interface StartupDataSeeder {
    suspend fun seedIfNeeded()
}
