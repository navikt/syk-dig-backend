package no.nav.sykdig.digitalisering.sykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_IM
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_NETS
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.digitalisering.sykmelding.service.SykmeldingService
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import no.nav.sykdig.logger
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.toOppgaveDbModel
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@DgsComponent
class JournalpostDataFetcher(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
    private val sykmeldingService: SykmeldingService,
    private val oppgaveClient: OppgaveClient,
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveSecurityService: OppgaveSecurityService,
) {
    companion object {
        private val securelog = securelog()
        private val log = logger()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(returnObject)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument id: String,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(id)
        securelog.info("journalpost from saf: ${objectMapper.writeValueAsString(journalpost)}")

        if (sykmeldingService.isSykmeldingCreated(id)) {
            log.info("Sykmelding already created for journalpost id $id")
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }
        val fnrEllerAktorId = getFnrEllerAktorId(journalpost)

        if (fnrEllerAktorId == null) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.MANGLER_FNR,
            )
        }

        if (journalpost.journalpost?.tema != TEMA_SYKMELDING) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        if (!listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.journalpost.kanal)) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.FEIL_KANAL,
            )
        }

        val fnr = personService.hentPerson(fnrEllerAktorId, id).fnr

        return Journalpost(
            id,
            journalpost.journalpost.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter = journalpost.journalpost.dokumenter?.map {
                Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
            } ?: emptyList(),
            fnr = fnr,
        )
    }


    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpostId(#journalpostId)")
    @DgsMutation(field = DgsConstants.MUTATION.SykmeldingFraJournalpost)
    fun createSykmelding(
        @InputArgument journalpostId: String,
        norsk: Boolean,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)

        if (journalpost.journalpost?.tema != TEMA_SYKMELDING) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        if (norsk) {
            if (!listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.journalpost.kanal)) {
                return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.FEIL_KANAL,
                )
            }
        } else {
            val response = oppgaveClient.opprettOppgave(
                journalpostId = journalpostId,
                tema = journalpost.journalpost.tema,
                oppgavetype = "BEH_SED",
                prioritet = "NORM",
                aktivDato = OffsetDateTime.now(),
                behandlesAvApplikasjon = "FS22",
            )

            val fnrEllerAktorId = getFnrEllerAktorId(journalpost)
                ?: return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.MANGLER_FNR,
                )

            val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr

            val dokumenter = journalpost.journalpost.dokumenter.map {
                DokumentDbModel(it.dokumentInfoId, it.tittel ?: "Mangler Tittel")
            }
            val oppgave = OppgaveDbModel(
                oppgaveId = response.id.toString(),
                fnr = fnr,
                journalpostId = journalpostId,
                dokumentInfoId = journalpost.journalpost.dokumenter.first().dokumentInfoId,
                dokumenter = dokumenter,
                opprettet = response.aktivDato,
                ferdigstilt = null,
                tilbakeTilGosys = true,
                avvisingsgrunn = null,
                sykmeldingId = UUID.randomUUID() , // Må opprette sykmelding først for å ha denne.
                type = "UTLAND",
                sykmelding = null,
                endretAv = oppgaveSecurityService.getSaksbehandlerId(),
                timestamp = OffsetDateTime.now(),
                source = "syk-dig"
            )
            oppgaveRepository.lagreOppgave(oppgave)

        }
        sykmeldingService.createSykmelding(journalpostId, journalpost.journalpost.tema)


        return JournalpostStatus(
            journalpostId = journalpostId,
            status = JournalpostStatusEnum.OPPRETTET,
        )
    }

    private fun getFnrEllerAktorId(journalpost: SafQueryJournalpost): String? {
        return when (journalpost.journalpost?.bruker?.type) {
            Type.ORGNR -> null
            else -> journalpost.journalpost?.bruker?.id
        }
    }

}
