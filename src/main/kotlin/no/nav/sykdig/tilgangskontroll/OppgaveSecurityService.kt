package no.nav.sykdig.tilgangskontroll

import no.nav.sykdig.shared.auditLogger.AuditLogger
import no.nav.sykdig.shared.auditlog
import no.nav.sykdig.utenlandsk.services.SykDigOppgaveService
import no.nav.sykdig.nasjonal.mapping.NasjonalSykmeldingMapper
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.saf.graphql.Type
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.services.NasjonalDbService
import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.shared.securelog
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    private val istilgangskontrollOboClient: IstilgangskontrollOboClient,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
    private val nasjonalSykmeldingMapper: NasjonalSykmeldingMapper,
    private val nasjonalDbService: NasjonalDbService,
) {
    companion object {
        private val securelog = securelog()
        private val auditlog = auditlog()
    }

    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val navEmail = nasjonalSykmeldingMapper.getNavEmail()
        val tilgang = hasAccess(oppgave.fnr, navEmail)
        securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
        return tilgang
    }

    fun hasAccessToNasjonalOppgave(oppgaveId: String, requestPath: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")

        val oppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
        return validateAccess(oppgave, requestPath)
    }

    fun hasAccessToNasjonalSykmelding(sykmeldingId: String, requestPath: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på sykmelding $sykmeldingId")
        val oppgave = nasjonalDbService.getOppgaveBySykmeldingId(sykmeldingId)
        return validateAccess(oppgave, requestPath)
    }

    private fun validateAccess(oppgave: NasjonalManuellOppgaveDAO?, requestPath: String): Boolean {
        val navEmail = nasjonalSykmeldingMapper.getNavEmail()
        val fnr = oppgave?.fnr
        if (oppgave != null && fnr != null) {
            val tilgang = hasAccess(fnr, navEmail, requestPath)
            securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id ${oppgave.oppgaveId}")
            auditlog.info(
                AuditLogger().createcCefMessage(
                    fnr = fnr,
                    navEmail = navEmail,
                    operation = AuditLogger.Operation.READ,
                    requestPath = requestPath,
                    permit =
                        when (tilgang) {
                            true -> AuditLogger.Permit.PERMIT
                            false -> AuditLogger.Permit.DENY
                        },
                ),
            )
            return tilgang
        }
        return false
    }

    //TODO: remove sykmeldingId after merge
    fun hasSuperUserAccessToNasjonalSykmelding(sykmeldingId: String?, oppgaveId: String?, requestPath: String): Boolean {
        securelog.info("sjekker om bruker har super bruker tilgang på sykmelding $sykmeldingId")
        val oppgave = if (sykmeldingId != null) nasjonalDbService.getOppgaveBySykmeldingId(sykmeldingId) else if (oppgaveId != null) nasjonalDbService.getOppgaveByOppgaveId(oppgaveId) else null
        val navEmail = nasjonalSykmeldingMapper.getNavEmail()
        val fnr = oppgave?.fnr
        if (oppgave != null && fnr != null) {
            val tilgang = hasSuperUserAccess(fnr, navEmail, requestPath)
            securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $sykmeldingId")
            auditlog.info(
                AuditLogger().createcCefMessage(
                    fnr = fnr,
                    navEmail = navEmail,
                    operation = AuditLogger.Operation.READ,
                    requestPath = requestPath,
                    permit =
                        when (tilgang) {
                            true -> AuditLogger.Permit.PERMIT
                            false -> AuditLogger.Permit.DENY
                        },
                ),
            )
            return tilgang
        }
        return false
    }

    fun hasAccessToSykmelding(sykmeldingId: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på sykmelding $sykmeldingId")
        val oppgave = sykDigOppgaveService.getOppgaveFromSykmeldingId(sykmeldingId)
        val navEmail = nasjonalSykmeldingMapper.getNavEmail()
        val tilgang = hasAccess(oppgave.fnr, navEmail)
        securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $sykmeldingId")
        return tilgang
    }

    fun hasAccessToJournalpost(journalpostResult: JournalpostResult): Boolean {
        return when (journalpostResult) {
            is Journalpost -> return hasAccess(journalpostResult.fnr, nasjonalSykmeldingMapper.getNavEmail())
            is JournalpostStatus -> return true
            else -> false
        }
    }

    fun hasAccessToJournalpostId(journalpostId: String): Boolean {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        securelog.info("journalpostid $journalpostId ble hentet: ${objectMapper.writeValueAsString(journalpost)}")

        val id =
            when (journalpost.journalpost?.bruker?.type) {
                Type.ORGNR -> null
                else -> journalpost.journalpost?.bruker?.id
            }

        if (id == null) {
            securelog.info("Fant ikke id i journalpost: $journalpostId")
            return false
        }

        val fnr = personService.getPerson(id, journalpostId).fnr

        securelog.info("Fødselsnummer: $fnr")
        val navEmail = nasjonalSykmeldingMapper.getNavEmail()
        val tilgang = hasAccess(fnr, journalpostId)
        securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} til journalpost med id $journalpostId")
        return tilgang
    }

    private fun hasAccess(
        fnr: String,
        navEmail: String,
        requestPath: String = "/api/graphql",
    ): Boolean {
        val tilgang = istilgangskontrollOboClient.sjekkTilgangVeileder(fnr)
        auditlog.info(
            AuditLogger().createcCefMessage(
                fnr = fnr,
                navEmail = navEmail,
                operation = AuditLogger.Operation.READ,
                requestPath = requestPath,
                permit =
                    when (tilgang) {
                        true -> AuditLogger.Permit.PERMIT
                        false -> AuditLogger.Permit.DENY
                    },
            ),
        )

        return tilgang
    }

    private fun hasSuperUserAccess(
        fnr: String,
        navEmail: String,
        requestPath: String,
    ): Boolean {
        val tilgang = istilgangskontrollOboClient.sjekkSuperBrukerTilgangVeileder(fnr)
        auditlog.info(
            AuditLogger().createcCefMessage(
                fnr = fnr,
                navEmail = navEmail,
                operation = AuditLogger.Operation.READ,
                requestPath = requestPath,
                permit =
                    when (tilgang) {
                        true -> AuditLogger.Permit.PERMIT
                        false -> AuditLogger.Permit.DENY
                    },
            ),
        )

        return tilgang
    }


}


