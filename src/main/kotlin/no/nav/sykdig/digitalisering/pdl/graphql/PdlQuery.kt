package no.nav.sykdig.digitalisering.pdl.graphql

const val PDL_QUERY = """
        query(${"$"}ident: ID!){
              hentIdenter(ident: ${"$"}ident, historikk: false) {
                identer {
                  ident,
                  historisk,
                  gruppe
                }
              }
              hentPerson(ident: ${"$"}ident) {
                navn(historikk: false) {
                  fornavn
                  mellomnavn
                  etternavn
                }
              }
            }
        """
