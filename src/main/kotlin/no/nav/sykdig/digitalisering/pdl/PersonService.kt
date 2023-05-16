package no.nav.sykdig.digitalisering.pdl

import no.nav.sykdig.digitalisering.pdl.client.PdlClient
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlResponse
import no.nav.sykdig.logger
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonService(
    private val pdlClient: PdlClient,
) {
    val log = logger()

    fun hentPerson(fnr: String, sykmeldingId: String): Person {
        val pdlResponse = pdlClient.hentPerson(fnr, sykmeldingId)

        log.info("Hentet person for sykmeldingId $sykmeldingId")

        return mapPdlResponseTilPerson(fnr, pdlResponse)
    }

    fun mapPdlResponseTilPerson(fnr: String, pdlResponse: PdlResponse): Person {
        val navn = pdlResponse.hentPerson!!.navn.first()
        val bostedsadresse = pdlResponse.hentPerson.bostedsadresse.firstOrNull()
        val oppholdsadresse = pdlResponse.hentPerson.oppholdsadresse.firstOrNull()

        return Person(
            fnr = fnr,
            navn = Navn(
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn,
            ),
            fodselsdato = pdlResponse.hentPerson.foedsel?.first()?.foedselsdato?.let { LocalDate.parse(it) },
            aktorId = pdlResponse.identer!!.identer.first { it.gruppe == "AKTORID" }.ident,
            bostedsadresse = bostedsadresse?.let {
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
                        )
                    },
                    matrikkeladresse = it.matrikkeladresse?.let { matrikkeladresse ->
                        Matrikkeladresse(
                            bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                            tilleggsnavn = matrikkeladresse.tilleggsnavn,
                            postnummer = matrikkeladresse.postnummer,
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
                            landkode = utenlandskAdresse.landkode,
                        )
                    },
                    ukjentBosted = it.ukjentBosted?.let { ukjentBosted ->
                        UkjentBosted(ukjentBosted.bostedskommune)
                    },
                )
            },
            oppholdsadresse = oppholdsadresse?.let {
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
                        )
                    },
                    matrikkeladresse = it.matrikkeladresse?.let { matrikkeladresse ->
                        Matrikkeladresse(
                            bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                            tilleggsnavn = matrikkeladresse.tilleggsnavn,
                            postnummer = matrikkeladresse.postnummer,
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
                            landkode = utenlandskAdresse.landkode,
                        )
                    },
                    oppholdAnnetSted = it.oppholdAnnetSted,
                )
            },
        )
    }
}
