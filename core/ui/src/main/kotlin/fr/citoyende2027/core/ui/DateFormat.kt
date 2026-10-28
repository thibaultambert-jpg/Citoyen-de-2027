package fr.citoyende2027.core.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val frenchDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

/** JJ/MM/AAAA, the only date format used in the UI. */
fun LocalDate.toFrenchDate(): String = format(frenchDate)
