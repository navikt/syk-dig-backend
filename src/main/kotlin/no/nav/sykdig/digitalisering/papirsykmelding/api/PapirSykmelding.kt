package no.nav.sykdig.digitalisering.papirsykmelding.api

import java.time.LocalDate
import java.time.OffsetDateTime

data class PapirManuellOppgave(
    val fnr: String?,
    val sykmeldingId: String,
    val oppgaveid: Int,
    var pdfPapirSykmelding: ByteArray,
    val papirSmRegistering: PapirSmRegistering?,
    val documents: List<Document>,
)

data class Document(
    val dokumentInfoId: String,
    val tittel: String,
)

data class PapirSmRegistering(
    val journalpostId: String,
    val oppgaveId: String?,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: OffsetDateTime?,
    val sykmeldingId: String,
    val syketilfelleStartDato: LocalDate?,
    val arbeidsgiver: Arbeidsgiver?,
    val medisinskVurdering: MedisinskVurdering?,
    val skjermesForPasient: Boolean?,
    val perioder: List<Periode>?,
    val prognose: Prognose?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>?,
    val tiltakNAV: String?,
    val tiltakArbeidsplassen: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNAV?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasient?,
    val behandletTidspunkt: LocalDate?,
    val behandler: Behandler?,
)

data class PasientNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class AvvisSykmeldingRequest(
    val reason: String?,
)
