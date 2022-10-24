package no.nav.sykdig.digitalisering.pdl

import no.nav.sykdig.digitalisering.pdl.client.PdlClient
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlPerson
import no.nav.sykdig.logger
import org.springframework.stereotype.Component

@Component
class PersonService(
    private val pdlClient: PdlClient,
) {
    val log = logger()

    fun hentPerson(fnr: String, sykmeldingId: String): Person {
        val person = pdlClient.hentPerson(fnr, sykmeldingId)

        return mapPdlPersonTilPerson(fnr, person)
    }

    fun mapPdlPersonTilPerson(fnr: String, pdlPerson: PdlPerson): Person {
        val navn = pdlPerson.navn.first()
        val bostedsadresse = pdlPerson.bostedsadresse.firstOrNull()
        val oppholdsadresse = pdlPerson.oppholdsadresse.firstOrNull()

        return Person(
            fnr = fnr,
            navn = Navn(
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn
            ),
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
                            landkode = utenlandskAdresse.landkode
                        )
                    },
                    ukjentBosted = it.ukjentBosted?.let { ukjentBosted ->
                        UkjentBosted(ukjentBosted.bostedskommune)
                    }
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
                            landkode = utenlandskAdresse.landkode
                        )
                    },
                    oppholdAnnetSted = it.oppholdAnnetSted
                )
            }
        )
    }
}
