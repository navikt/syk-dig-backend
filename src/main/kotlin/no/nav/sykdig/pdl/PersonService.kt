package no.nav.sykdig.pdl

import java.time.LocalDate
import no.nav.sykdig.pdl.client.PdlClient
import no.nav.sykdig.pdl.client.graphql.PdlResponse
import no.nav.sykdig.shared.applog
import org.springframework.stereotype.Component

@Component
class PersonService(private val pdlClient: PdlClient) {
    val log = applog()

    fun getPerson(id: String, callId: String): Person {
        val pdlResponse = pdlClient.getPerson(id, callId)
        log.info("Hentet person for callId: $callId")
        return mapPdlResponseTilPerson(id, pdlResponse)
    }

    fun mapPdlResponseTilPerson(ident: String, pdlResponse: PdlResponse): Person {
        val navn = pdlResponse.hentPerson!!.navn.first()
        val bostedsadresse = pdlResponse.hentPerson.bostedsadresse.firstOrNull()
        val oppholdsadresse = pdlResponse.hentPerson.oppholdsadresse.firstOrNull()
        val fnr =
            pdlResponse.identer?.identer?.first { it.gruppe == "FOLKEREGISTERIDENT" }?.ident
                ?: throw RuntimeException("Fant ikke fnr for person i PDL")
        return Person(
            fnr = fnr,
            navn =
                Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn,
                ),
            fodselsdato =
                pdlResponse.hentPerson.foedselsdato?.first()?.foedselsdato?.let {
                    LocalDate.parse(it)
                },
            aktorId = pdlResponse.identer.identer.first { it.gruppe == "AKTORID" }.ident,
            bostedsadresse =
                bostedsadresse?.let {
                    Bostedsadresse(
                        coAdressenavn = it.coAdressenavn,
                        vegadresse =
                            it.vegadresse?.let { vegadresse ->
                                Vegadresse(
                                    husnummer = vegadresse.husnummer,
                                    husbokstav = vegadresse.husbokstav,
                                    bruksenhetsnummer = vegadresse.bruksenhetsnummer,
                                    adressenavn = vegadresse.adressenavn,
                                    tilleggsnavn = vegadresse.tilleggsnavn,
                                    postnummer = vegadresse.postnummer,
                                )
                            },
                        matrikkeladresse =
                            it.matrikkeladresse?.let { matrikkeladresse ->
                                Matrikkeladresse(
                                    bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                                    tilleggsnavn = matrikkeladresse.tilleggsnavn,
                                    postnummer = matrikkeladresse.postnummer,
                                )
                            },
                        utenlandskAdresse =
                            it.utenlandskAdresse?.let { utenlandskAdresse ->
                                UtenlandskAdresse(
                                    adressenavnNummer = utenlandskAdresse.adressenavnNummer,
                                    bygningEtasjeLeilighet =
                                        utenlandskAdresse.bygningEtasjeLeilighet,
                                    postboksNummerNavn = utenlandskAdresse.postboksNummerNavn,
                                    postkode = utenlandskAdresse.postkode,
                                    bySted = utenlandskAdresse.bySted,
                                    regionDistriktOmraade = utenlandskAdresse.regionDistriktOmraade,
                                    landkode = utenlandskAdresse.landkode,
                                )
                            },
                        ukjentBosted =
                            it.ukjentBosted?.let { ukjentBosted ->
                                UkjentBosted(ukjentBosted.bostedskommune)
                            },
                    )
                },
            oppholdsadresse =
                oppholdsadresse?.let {
                    Oppholdsadresse(
                        coAdressenavn = it.coAdressenavn,
                        vegadresse =
                            it.vegadresse?.let { vegadresse ->
                                Vegadresse(
                                    husnummer = vegadresse.husnummer,
                                    husbokstav = vegadresse.husbokstav,
                                    bruksenhetsnummer = vegadresse.bruksenhetsnummer,
                                    adressenavn = vegadresse.adressenavn,
                                    tilleggsnavn = vegadresse.tilleggsnavn,
                                    postnummer = vegadresse.postnummer,
                                )
                            },
                        matrikkeladresse =
                            it.matrikkeladresse?.let { matrikkeladresse ->
                                Matrikkeladresse(
                                    bruksenhetsnummer = matrikkeladresse.bruksenhetsnummer,
                                    tilleggsnavn = matrikkeladresse.tilleggsnavn,
                                    postnummer = matrikkeladresse.postnummer,
                                )
                            },
                        utenlandskAdresse =
                            it.utenlandskAdresse?.let { utenlandskAdresse ->
                                UtenlandskAdresse(
                                    adressenavnNummer = utenlandskAdresse.adressenavnNummer,
                                    bygningEtasjeLeilighet =
                                        utenlandskAdresse.bygningEtasjeLeilighet,
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
