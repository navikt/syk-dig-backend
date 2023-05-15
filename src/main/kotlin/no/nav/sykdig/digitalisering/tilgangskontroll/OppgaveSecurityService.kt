package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.logger
import no.nav.sykdig.securelog
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    val syfoTilgangskontrollOboClient: SyfoTilgangskontrollOboClient,
    val sykDigOppgaveService: SykDigOppgaveService,
) {

    companion object {
        private val log = logger()
        private val auditlog = auditlog()
        private val securelog = securelog()
    }
    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        if (!syfoTilgangskontrollOboClient.sjekkTilgangVeileder(oppgave.fnr)) {
            log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
            val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
            val navEmail = autentication.token.claims["preferred_username"].toString()
            securelog.info("navEmail: $navEmail")

            auditlog.info(
                AuditLogger().createcCefMessage(
                    fnr = oppgave.fnr,
                    navEmail = navEmail,
                    operation = AuditLogger.Operation.WRITE,
                    requestPath = "/api/graphql",
                    permit = AuditLogger.Permit.DENY,
                ),
            )

            return false
        }
        log.info("Innlogget bruker har tilgang til oppgave med id $oppgaveId")

        val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        val navEmail = autentication.token.claims["preferred_username"].toString()
        securelog.info("navEmail: $navEmail")

        auditlog.info(
            AuditLogger().createcCefMessage(
                fnr = oppgave.fnr,
                navEmail = navEmail,
                operation = AuditLogger.Operation.WRITE,
                requestPath = "/api/graphql",
                permit = AuditLogger.Permit.PERMIT,
            ),
        )

        return true
    }
}
