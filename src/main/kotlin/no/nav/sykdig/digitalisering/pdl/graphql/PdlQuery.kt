package no.nav.sykdig.digitalisering.pdl.graphql

const val PDL_QUERY = """
    query(${"$"}ident: ID!){
        hentPerson(ident: ${"$"}ident) {
            navn(historikk: false) {
                fornavn
                mellomnavn
                etternavn
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
