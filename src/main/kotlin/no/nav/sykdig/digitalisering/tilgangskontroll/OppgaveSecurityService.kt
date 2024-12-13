package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalCommonService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    private val istilgangskontrollOboClient: IstilgangskontrollOboClient,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val nasjonalCommonService: NasjonalCommonService,
) {
    companion object {
        private val securelog = securelog()
        private val auditlog = auditlog()
    }

    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val navEmail = nasjonalCommonService.getNavEmail()
        val tilgang = hasAccess(oppgave.fnr, navEmail)
        securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
        return tilgang
    }

    fun hasAccessToNasjonalOppgave(oppgaveId: String, authorization: String, requestPath: String): Boolean {
            securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")

            val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId, authorization)
            val navEmail = nasjonalCommonService.getNavEmail()
            val fnr = oppgave?.fnr
            if (oppgave != null && fnr != null) {
                val tilgang = hasAccess(fnr, navEmail, requestPath)
                securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
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

        fun hasAccessToNasjonalSykmelding(sykmeldingId: String, authorization: String, requestPath: String): Boolean {
            securelog.info("sjekker om bruker har tilgang på sykmelding $sykmeldingId")
            val oppgave = nasjonalOppgaveService.findBySykmeldingId(sykmeldingId)
            val navEmail = nasjonalCommonService.getNavEmail()
            val fnr = oppgave?.fnr
            if (oppgave != null && fnr != null) {
                val tilgang = hasAccess(fnr, navEmail, requestPath)
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

    fun hasSuperUserAccessToNasjonalSykmelding(sykmeldingId: String, authorization: String, requestPath: String): Boolean {
        securelog.info("sjekker om bruker har super bruker tilgang på sykmelding $sykmeldingId")
        val oppgave = nasjonalOppgaveService.findBySykmeldingId(sykmeldingId)
        val navEmail = nasjonalCommonService.getNavEmail()
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
            val navEmail = nasjonalCommonService.getNavEmail()
            val tilgang = hasAccess(oppgave.fnr, navEmail)
            securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $sykmeldingId")
            return tilgang
        }

        fun hasAccessToJournalpost(journalpostResult: JournalpostResult): Boolean {
            return when (journalpostResult) {
                is Journalpost -> return hasAccess(journalpostResult.fnr, nasjonalCommonService.getNavEmail())
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
            val navEmail = nasjonalCommonService.getNavEmail()
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


