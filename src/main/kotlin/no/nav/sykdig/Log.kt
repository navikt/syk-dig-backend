package no.nav.sykdig

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

inline fun <reified T> T.securelog(): Logger {
    return LoggerFactory.getLogger("securelog")
}
