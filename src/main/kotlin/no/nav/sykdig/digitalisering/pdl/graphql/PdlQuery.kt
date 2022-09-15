package no.nav.sykdig.digitalisering.pdl.graphql

data class PdlQuery(val fnr: String) {
    private val query = """
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

    fun getQuery(): String {
        return query
    }
}
