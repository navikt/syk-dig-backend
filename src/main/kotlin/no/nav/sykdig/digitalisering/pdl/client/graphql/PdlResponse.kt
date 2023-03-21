package no.nav.sykdig.digitalisering.pdl.client.graphql

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.objectMapper

const val PDL_QUERY = """
    query(${"$"}ident: ID!){
        identer: hentIdenter(ident: ${"$"}ident, historikk: false) {
                identer {
                    ident
                    gruppe
                }
            }
        hentPerson(ident: ${"$"}ident) {
            navn(historikk: false) {
                fornavn
                mellomnavn
                etternavn
            }
            foedsel {
                foedselsdato
            }
            bostedsadresse(historikk: false) {
                coAdressenavn
                vegadresse {
                    husnummer
                    husbokstav
                    adressenavn
                    tilleggsnavn
                    postnummer
                }
                matrikkeladresse {
                    matrikkelId
                    bruksenhetsnummer
                    tilleggsnavn
                    postnummer
                }
                utenlandskAdresse {
                    adressenavnNummer
                    bygningEtasjeLeilighet
                    postboksNummerNavn
                    postkode
                    bySted
                    regionDistriktOmraade
                    landkode
                }
                ukjentBosted {
                    bostedskommune
                }
            }
            oppholdsadresse(historikk: false) {
                coAdressenavn
                vegadresse {
                    husnummer
                    husbokstav
                    adressenavn
                    tilleggsnavn
                    postnummer
                    bruksenhetsnummer
                }
                matrikkeladresse {
                    matrikkelId
                    bruksenhetsnummer
                    tilleggsnavn
                    postnummer
                }
                utenlandskAdresse {
                    adressenavnNummer
                    bygningEtasjeLeilighet
                    postboksNummerNavn
                    postkode
                    bySted
                    regionDistriktOmraade
                    landkode
                }
                oppholdAnnetSted
            }
        }
    }
"""

data class Data(
    val data: PdlResponse?,
)

data class PdlResponse(
    val hentPerson: PdlPerson?,
    val identer: PdlIdenter?,
)
data class PdlIdenter(
    val identer: List<PdlIdent>,
)
data class PdlIdent(
    val ident: String,
    val gruppe: String,
)
data class PdlPerson(
    val bostedsadresse: List<PdlBostedsadresse>,
    val navn: List<PdlNavn>,
    val foedsel: List<Foedsel>?,
    val oppholdsadresse: List<PdlOppholdsadresse>,
)

data class Foedsel(
    val foedselsdato: String?,
)

data class PdlBostedsadresse(
    val coAdressenavn: String?,
    val vegadresse: PdlVegadresse?,
    val matrikkeladresse: PdlMatrikkeladresse?,
    val utenlandskAdresse: PdlUtenlandskAdresse?,
    val ukjentBosted: PdlUkjentBosted?,
)

data class PdlOppholdsadresse(
    val coAdressenavn: String?,
    val utenlandskAdresse: PdlUtenlandskAdresse?,
    val vegadresse: PdlVegadresse?,
    val matrikkeladresse: PdlMatrikkeladresse?,
    val oppholdAnnetSted: String?,
)

data class PdlVegadresse(
    val husnummer: String?,
    val husbokstav: String?,
    val bruksenhetsnummer: String?,
    val adressenavn: String?,
    val kommunenummer: String?,
    val bydelsnummer: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
    val poststed: String?,
)

data class PdlMatrikkeladresse(
    val bruksenhetsnummer: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
    val kommunenummer: String?,
    val poststed: String?,
)

data class PdlUkjentBosted(
    val bostedskommune: String,
)

data class PdlUtenlandskAdresse(
    val adressenavnNummer: String?,
    val bygningEtasjeLeilighet: String?,
    val postboksNummerNavn: String?,
    val postkode: String?,
    val bySted: String?,
    val regionDistriktOmraade: String?,
    val landkode: String,
)

data class PdlNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

fun mapToPdlResponse(json: String): PdlResponse =
    objectMapper.readValue<Data>(json).data!!
