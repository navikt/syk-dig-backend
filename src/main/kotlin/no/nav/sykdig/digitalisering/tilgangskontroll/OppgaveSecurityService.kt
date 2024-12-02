package no.nav.sykdig.digitalisering.tilgangskontroll

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Veileder
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(
    private val istilgangskontrollOboClient: IstilgangskontrollOboClient,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
) {
    companion object {
        private val securelog = securelog()
        private val auditlog = auditlog()
    }

    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val navEmail = getNavEmail()
        val tilgang = hasAccess(oppgave.fnr, navEmail)
        securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
        return tilgang
    }

    fun hasAccessToNasjonalOppgave(oppgaveId: String): Boolean {
            securelog.info("sjekker om bruker har tilgang på oppgave $oppgaveId")
            val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId.toInt())
            val navEmail = getNavEmail()
            val fnr = oppgave?.fnr
            if (oppgave != null && fnr != null) {
                val tilgang = hasAccess(fnr, navEmail)
                securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $oppgaveId")
                return tilgang
            }
            return false
        }

        fun hasAccessToSykmelding(sykmeldingId: String): Boolean {
            securelog.info("sjekker om bruker har tilgang på sykmelding $sykmeldingId")
            val oppgave = sykDigOppgaveService.getOppgaveFromSykmeldingId(sykmeldingId)
            val navEmail = getNavEmail()
            val tilgang = hasAccess(oppgave.fnr, navEmail)
            securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} tilgang til oppgave med id $sykmeldingId")
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
            val navEmail = getNavEmail()
            val tilgang = hasAccess(fnr, journalpostId)
            securelog.info("Innlogget bruker: $navEmail har${if (!tilgang) " ikke" else ""} til journalpost med id $journalpostId")
            return tilgang
        }

                private fun hasAccess(
            fnr: String,
            navEmail: String,
        ): Boolean {
            val tilgang = istilgangskontrollOboClient.sjekkTilgangVeileder(fnr)
            auditlog.info(
                AuditLogger().createcCefMessage(
                    fnr = fnr,
                    navEmail = navEmail,
                    operation = AuditLogger.Operation.READ,
                    requestPath = "/api/graphql",
                    permit =
                        when (tilgang) {
                            true -> AuditLogger.Permit.PERMIT
                            false -> AuditLogger.Permit.DENY
                        },
                ),
            )

            return tilgang
        }

        fun getNavIdent(): Veileder {
            val authentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
            return Veileder(authentication.token.claims["NAVident"].toString())
        }

        fun getNavEmail(): String {
            val authentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
            return authentication.token.claims["preferred_username"].toString()
        }
}


