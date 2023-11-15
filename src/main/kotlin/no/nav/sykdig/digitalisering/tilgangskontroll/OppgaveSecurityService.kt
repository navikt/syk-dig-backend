package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    private val syfoTilgangskontrollOboClient: SyfoTilgangskontrollOboClient,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
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
    fun hasAccessToJournalpost(journalpostResult: JournalpostResult): Boolean {
        return when (journalpostResult) {
            is Journalpost -> return hasAccess(journalpostResult.fnr, getNavEmail())
            is JournalpostStatus -> return true
            else -> false
        }
    }
    fun hasAccessToJournalpostId(journalpostId: String): Boolean {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        securelog.info("journalpostid $journalpostId ble hentet: ${objectMapper.writeValueAsString(journalpost)}")

        val id = when (journalpost.journalpost?.bruker?.type) {
            Type.ORGNR -> null
            Type.AKTOERID -> null
            else -> journalpost.journalpost?.bruker?.id
        }

        if (id == null) {
            securelog.info("Fant ikke id i journalpost: $journalpostId")
            return false
        }

        val fnr = personService.hentPerson(id, journalpostId).fnr

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
        return autentication.token.claims["preferred_username"].toString()
    }
}
