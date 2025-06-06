package no.nav.sykdig.nasjonal.models

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.nav.sykdig.shared.*
import java.time.LocalDate
import java.time.OffsetDateTime

data class PapirManuellOppgave(
    val fnr: String?,
    val sykmeldingId: String,
    val oppgaveid: Int?,
    var pdfPapirSykmelding: ByteArray,
    val papirSmRegistering: PapirSmRegistering,
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
    @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer::class)
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

fun PapirSmRegistering.toPapirManuellOppgave(oppgaveid: Int?): PapirManuellOppgave {
    return PapirManuellOppgave(
        fnr = fnr,
        sykmeldingId = sykmeldingId,
        oppgaveid = oppgaveid,
        pdfPapirSykmelding = ByteArray(0),
        papirSmRegistering = this,
        documents = emptyList(),
    )
}

data class AvvisSykmeldingRequest(
    val reason: String?,
)

data class Sykmelder(
    val hprNummer: String?,
    val fnr: String?,
    val aktorId: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
    val godkjenninger: List<Godkjenning>?,
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null,
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?,
)

data class SmRegistreringManuell(
    val pasientFnr: String,
    val sykmelderFnr: String,
    val perioder: List<Periode>,
    val medisinskVurdering: MedisinskVurdering,
    val syketilfelleStartDato: LocalDate?,
    val arbeidsgiver: Arbeidsgiver,
    val behandletDato: LocalDate,
    val skjermesForPasient: Boolean,
    val behandler: Behandler,
    val kontaktMedPasient: KontaktMedPasient,
    val meldingTilNAV: MeldingTilNAV?,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val harUtdypendeOpplysninger: Boolean,
)