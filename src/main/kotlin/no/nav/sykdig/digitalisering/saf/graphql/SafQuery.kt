package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
        }
    }
"""
