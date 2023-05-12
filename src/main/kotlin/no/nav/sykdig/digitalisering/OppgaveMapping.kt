package no.nav.sykdig.digitalisering

import no.nav.syfo.model.Periode
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.generated.types.Bostedsadresse
import no.nav.sykdig.generated.types.DiagnoseValue
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Matrikkeladresse
import no.nav.sykdig.generated.types.OppgaveValues
import no.nav.sykdig.generated.types.OppholdAnnetSted
import no.nav.sykdig.generated.types.Oppholdsadresse
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.generated.types.PeriodeValue
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.generated.types.SykmeldingsType
import no.nav.sykdig.generated.types.UkjentBosted
import no.nav.sykdig.generated.types.UtenlandskAdresse
import no.nav.sykdig.generated.types.Vegadresse
import no.nav.sykdig.model.SykmeldingUnderArbeid

fun mapToDigitaliseringsoppgave(
    oppgave: SykDigOppgave,
) = Digitaliseringsoppgave(
    oppgaveId = oppgave.oppgaveDbModel.oppgaveId,
    person = Person(
        navn = oppgave.person.navn.toFormattedNameString(),
        bostedsadresse = mapToBostedsadresse(oppgave.person),
        oppholdsadresse = mapToOppholdsadresse(oppgave.person),
    ),
    type = if (oppgave.oppgaveDbModel.type == "UTLAND") SykmeldingsType.UTENLANDS else SykmeldingsType.INNENLANDS,
    values = oppgave.oppgaveDbModel.sykmelding?.mapToOppgaveValues()
        ?: OppgaveValues(fnrPasient = oppgave.oppgaveDbModel.fnr),
    documents = oppgave.oppgaveDbModel.dokumenter.map { Document(it.tittel, it.dokumentInfoId) },
)

private fun SykmeldingUnderArbeid.mapToOppgaveValues(): OppgaveValues = OppgaveValues(
    fnrPasient = this.fnrPasient,
    behandletTidspunkt = this.sykmelding.behandletTidspunkt,
    // ISO 3166-1 alpha-3, 3-letter country codes
    skrevetLand = this.utenlandskSykmelding?.land,
    hoveddiagnose = this.sykmelding.medisinskVurdering?.hovedDiagnose?.let {
        DiagnoseValue(
            kode = it.kode,
            tekst = it.tekst,
            system = it.system,
        )
    },
    biDiagnoser = this.sykmelding.medisinskVurdering?.biDiagnoser?.map {
        DiagnoseValue(
            kode = it.kode,
            tekst = it.tekst,
            system = it.system,
        )
    },
    perioder = this.sykmelding.perioder?.map(Periode::mapToPeriodeValue),
)

private fun Periode.mapToPeriodeValue(): PeriodeValue {
    val type: PeriodeType = when {
        this.gradert != null -> PeriodeType.GRADERT
        else -> PeriodeType.AKTIVITET_IKKE_MULIG
    }

    return PeriodeValue(
        type = type,
        fom = this.fom,
        tom = this.tom,
        grad = this.gradert?.grad,
    )
}

private fun mapToOppholdsadresse(person: no.nav.sykdig.digitalisering.pdl.Person): Oppholdsadresse? =
    person.oppholdsadresse?.let {
        when {
            it.vegadresse != null -> Vegadresse(
                husnummer = it.vegadresse.husnummer,
                husbokstav = it.vegadresse.husbokstav,
                adressenavn = it.vegadresse.adressenavn,
                postnummer = it.vegadresse.postnummer,
            )

            it.matrikkeladresse != null -> Matrikkeladresse(
                bruksenhetsnummer = it.matrikkeladresse.bruksenhetsnummer,
                tilleggsnavn = it.matrikkeladresse.tilleggsnavn,
                postnummer = it.matrikkeladresse.postnummer,
            )

            it.utenlandskAdresse != null -> UtenlandskAdresse(
                adressenavnNummer = it.utenlandskAdresse.adressenavnNummer,
                postboksNummerNavn = it.utenlandskAdresse.postboksNummerNavn,
                postkode = it.utenlandskAdresse.postkode,
                bySted = it.utenlandskAdresse.bySted,
                landkode = it.utenlandskAdresse.landkode,
            )

            it.oppholdAnnetSted != null -> OppholdAnnetSted(
                type = it.oppholdAnnetSted,
            )

            else -> null
        }
    }

private fun mapToBostedsadresse(person: no.nav.sykdig.digitalisering.pdl.Person): Bostedsadresse? =
    person.bostedsadresse?.let {
        when {
            it.vegadresse != null -> Vegadresse(
                husnummer = it.vegadresse.husnummer,
                husbokstav = it.vegadresse.husbokstav,
                adressenavn = it.vegadresse.adressenavn,
                postnummer = it.vegadresse.postnummer,
            )

            it.matrikkeladresse != null -> Matrikkeladresse(
                bruksenhetsnummer = it.matrikkeladresse.bruksenhetsnummer,
                tilleggsnavn = it.matrikkeladresse.tilleggsnavn,
                postnummer = it.matrikkeladresse.postnummer,
            )

            it.utenlandskAdresse != null -> UtenlandskAdresse(
                adressenavnNummer = it.utenlandskAdresse.adressenavnNummer,
                postboksNummerNavn = it.utenlandskAdresse.postboksNummerNavn,
                postkode = it.utenlandskAdresse.postkode,
                bySted = it.utenlandskAdresse.bySted,
                landkode = it.utenlandskAdresse.landkode,
            )

            it.ukjentBosted != null -> UkjentBosted(
                bostedskommune = it.ukjentBosted.bostedskommune,
            )

            else -> null
        }
    }
