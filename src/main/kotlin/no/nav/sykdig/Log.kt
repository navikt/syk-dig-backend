package no.nav.sykdig

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.applog(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

fun applog(clazz: String): Logger {
    return LoggerFactory.getLogger(clazz)
}

inline fun <reified T> T.securelog(): Logger {
    return LoggerFactory.getLogger("securelog")
}

inline fun <reified T> T.auditlog(): Logger {
    return LoggerFactory.getLogger("auditLogger")
}

data class LoggingMeta(
    val mottakId: String,
    val journalpostId: String?,
    val dokumentInfoId: String?,
    val msgId: String,
    val sykmeldingId: String,
)
