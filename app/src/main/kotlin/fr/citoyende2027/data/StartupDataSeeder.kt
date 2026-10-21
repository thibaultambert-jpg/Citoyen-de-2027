package fr.citoyende2027.data

/**
 * Installs initial data at startup: the signed `core` pack embedded in the APK when present; debug
 * builds fall back to the fictional fixtures.
 */
fun interface StartupDataSeeder {
    suspend fun seedIfNeeded()
}
