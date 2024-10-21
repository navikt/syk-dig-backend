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
sealed class PdfDocument {
    data class Good(val value: ByteArray) : PdfDocument()

    data class Error(val value: PdfErrors) : PdfDocument()
}

enum class PdfErrors {
    DOCUMENT_NOT_FOUND,
    SAF_CLIENT_ERROR,
    EMPTY_RESPONSE,
    SAKSBEHANDLER_IKKE_TILGANG,
}