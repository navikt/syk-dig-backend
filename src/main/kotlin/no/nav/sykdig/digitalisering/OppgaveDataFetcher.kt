package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.syfo.model.Periode
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.Bostedsadresse
import no.nav.sykdig.generated.types.DiagnoseValue
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.Matrikkeladresse
import no.nav.sykdig.generated.types.OppgaveValues
import no.nav.sykdig.generated.types.Oppholdsadresse
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.generated.types.PeriodeValue
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.generated.types.SykmeldingsType
import no.nav.sykdig.generated.types.UkjentBosted
import no.nav.sykdig.generated.types.UtenlandskAdresse
import no.nav.sykdig.generated.types.Vegadresse
import no.nav.sykdig.logger
import no.nav.sykdig.model.SykmeldingUnderArbeid

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository,
    private val personService: PersonService,
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")
    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave != null) {
            if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
                log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
                throw IkkeTilgangException("Innlogget bruker har ikke tilgang")
            }
            try {
                val person = personService.hentPerson(fnr = oppgave.fnr, sykmeldingId = oppgave.sykmeldingId.toString())
                return Digitaliseringsoppgave(
                    oppgaveId = oppgave.oppgaveId,
                    person = Person(
                        fnr = person.fnr,
                        navn = person.navn.toFormattedNameString(),
                        bostedsadresse = person.bostedsadresse?.let {
                            Bostedsadresse(
                                coAdressenavn = it.coAdressenavn,
                                vegadresse = it.vegadresse?.let { vegadresse ->
                                    Vegadresse(
                                        husnummer = vegadresse.husnummer,
                                        husbokstav = vegadresse.husbokstav,
                                        bruksenhetsnummer = vegadresse.bruksenhetsnummer,
                                        adressenavn = vegadresse.adressenavn,
                                        tilleggsnavn = vegadresse.tilleggsnavn,
                                        postnummer = vegadresse.postnummer,
                                        poststed = vegadresse.poststed
                                    )
                                },
                                matrikkeladresse = it.matrikkeladresse?.let { matrikkeladresse ->
                                    Matrikkeladresse(
                                        bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                                        tilleggsnavn = matrikkeladresse.tilleggsnavn,
                                        postnummer = matrikkeladresse.postnummer,
                                        poststed = matrikkeladresse.poststed
                                    )
                                },
                                utenlandskAdresse = it.utenlandskAdresse?.let { utenlandskAdresse ->
                                    UtenlandskAdresse(
                                        adressenavnNummer = utenlandskAdresse.adressenavnNummer,
                                        bygningEtasjeLeilighet = utenlandskAdresse.bygningEtasjeLeilighet,
                                        postboksNummerNavn = utenlandskAdresse.postboksNummerNavn,
                                        postkode = utenlandskAdresse.postkode,
                                        bySted = utenlandskAdresse.bySted,
                                        regionDistriktOmraade = utenlandskAdresse.regionDistriktOmraade,
                                        landkode = utenlandskAdresse.landkode
                                    )
                                },
                                ukjentBosted = it.ukjentBosted?.let { ukjentBosted ->
                                    UkjentBosted(ukjentBosted.bostedskommune)
                                }
                            )
                        },
                        oppholdsadresse = person.oppholdsadresse?.let {
                            Oppholdsadresse(
                                coAdressenavn = it.coAdressenavn,
                                vegadresse = it.vegadresse?.let { vegadresse ->
                                    Vegadresse(
                                        husnummer = vegadresse.husnummer,
                                        husbokstav = vegadresse.husbokstav,
                                        bruksenhetsnummer = vegadresse.bruksenhetsnummer,
                                        adressenavn = vegadresse.adressenavn,
                                        tilleggsnavn = vegadresse.tilleggsnavn,
                                        postnummer = vegadresse.postnummer,
                                        poststed = vegadresse.poststed
                                    )
                                },
                                matrikkeladresse = it.matrikkeladresse?.let { matrikkeladresse ->
                                    Matrikkeladresse(
                                        bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                                        tilleggsnavn = matrikkeladresse.tilleggsnavn,
                                        postnummer = matrikkeladresse.postnummer,
                                        poststed = matrikkeladresse.poststed
                                    )
                                },
                                utenlandskAdresse = it.utenlandskAdresse?.let { utenlandskAdresse ->
                                    UtenlandskAdresse(
                                        adressenavnNummer = utenlandskAdresse.adressenavnNummer,
                                        bygningEtasjeLeilighet = utenlandskAdresse.bygningEtasjeLeilighet,
                                        postboksNummerNavn = utenlandskAdresse.postboksNummerNavn,
                                        postkode = utenlandskAdresse.postkode,
                                        bySted = utenlandskAdresse.bySted,
                                        regionDistriktOmraade = utenlandskAdresse.regionDistriktOmraade,
                                        landkode = utenlandskAdresse.landkode
                                    )
                                },
                                oppholdAnnetSted = it.oppholdAnnetSted
                            )
                        },
                    ),
                    type = if (oppgave.type == "UTLAND") {
                        SykmeldingsType.UTENLANDS
                    } else {
                        SykmeldingsType.INNENLANDS
                    },
                    values = oppgave.sykmelding?.mapToOppgaveValues() ?: OppgaveValues()
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av oppgave med id $oppgaveId")
                throw RuntimeException("Noe gikk galt ved henting av oppgave")
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
    }
}

private fun SykmeldingUnderArbeid.mapToOppgaveValues(): OppgaveValues =
    OppgaveValues(
        fnrPasient = this.fnrPasient,
        behandletTidspunkt = this.sykmelding?.behandletTidspunkt,
        // ISO 3166-1 alpha-2, 2-letter country codes
        skrevetLand = this.utenlandskSykmelding?.land,
        hoveddiagnose = this.sykmelding?.medisinskVurdering?.hovedDiagnose?.let {
            DiagnoseValue(
                kode = it.kode,
                tekst = it.tekst,
                system = it.system,
            )
        },
        biDiagnoser = this.sykmelding?.medisinskVurdering?.biDiagnoser?.map {
            DiagnoseValue(
                kode = it.kode,
                tekst = it.tekst,
                system = it.system,
            )
        },
        perioder = this.sykmelding?.perioder?.map(Periode::mapToPeriodeValue),
    )

private fun Periode.mapToPeriodeValue(): PeriodeValue {
    val type: PeriodeType = when {
        this.reisetilskudd -> PeriodeType.REISETILSKUDD
        this.behandlingsdager != null -> PeriodeType.BEHANDLINGSDAGER
        this.avventendeInnspillTilArbeidsgiver != null -> PeriodeType.AVVENTENDE
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
