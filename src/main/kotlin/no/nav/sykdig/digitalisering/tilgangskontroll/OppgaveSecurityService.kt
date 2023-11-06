package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottakerIdType.FNR
import no.nav.sykdig.securelog
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    private val syfoTilgangskontrollOboClient: SyfoTilgangskontrollOboClient,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safGraphQlClient: SafJournalpostGraphQlClient,
) {

    companion object {
        private val securelog = securelog()
        private val auditlog = auditlog()
    }

    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val navEmail = getNavEmail()
        val tilgang = hasAccess(oppgave.fnr, navEmail)
        securelog.info("Innlogget bruker: $navEmail har${ if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
        return tilgang
    }

    fun hasAccessToJournalpost(journalpostId: String): Boolean {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        securelog.info("journalpost hentet: ${journalpostId}")
        val fnr = when (journalpost.journalpost?.avsenderMottaker?.type) {
            FNR -> journalpost.journalpost.avsenderMottaker.id
            else -> null
        } ?: return false
        securelog.info("Fødselsnummer: $fnr")
        val navEmail = getNavEmail()
        val tilgang = hasAccess(fnr, journalpostId)
        securelog.info("Innlogget bruker: $navEmail har${ if (!tilgang) " ikke" else ""} til journalpost med id $journalpostId")
        return tilgang
    }

    private fun hasAccess(fnr: String, navEmail: String): Boolean {
        val tilgang = syfoTilgangskontrollOboClient.sjekkTilgangVeileder(fnr)
        auditlog.info(
            AuditLogger().createcCefMessage(
                fnr = fnr,
                navEmail = navEmail,
                operation = AuditLogger.Operation.READ,
                requestPath = "/api/graphql",
                permit = when (tilgang) {
                    true -> AuditLogger.Permit.PERMIT
                    false -> AuditLogger.Permit.DENY
                },
            ),
        )

        return tilgang
    }

    private fun getNavEmail(): String {
        val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        val navEmail = autentication.token.claims["preferred_username"].toString()
        return navEmail
    }
}
