package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
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
        private val securelog = securelog()
        private val auditlog = auditlog()
    }
    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        if (!syfoTilgangskontrollOboClient.sjekkTilgangVeileder(oppgave.fnr)) {
            val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
            val navEmail = autentication.token.claims["preferred_username"].toString()

            securelog.warn("Innlogget bruker: $navEmail har ikke tilgang til oppgave med id $oppgaveId")
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

        val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        val navEmail = autentication.token.claims["preferred_username"].toString()
        securelog.info("Innlogget bruker: $navEmail har tilgang til oppgave med id $oppgaveId")
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
