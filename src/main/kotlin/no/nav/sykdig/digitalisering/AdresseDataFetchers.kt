package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.db.PoststedRepository
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Matrikkeladresse
import no.nav.sykdig.generated.types.Vegadresse

@DgsComponent
class AdresseDataFetchers(
    private val poststedRepository: PoststedRepository,
) {
    @DgsData(parentType = DgsConstants.VEGADRESSE.TYPE_NAME, field = DgsConstants.VEGADRESSE.Poststed)
    fun vegadressePoststed(dfe: DataFetchingEnvironment): String? {
        val vegadresse: Vegadresse = dfe.getSource();
        if (vegadresse.postnummer == null) {
            return null;
        }

        return poststedRepository.getPoststed(vegadresse.postnummer)
    }

    @DgsData(parentType = DgsConstants.MATRIKKELADRESSE.TYPE_NAME, field = DgsConstants.MATRIKKELADRESSE.Poststed)
    fun matrikkeladressePoststed(dfe: DataFetchingEnvironment): String? {
        val vegadresse: Matrikkeladresse = dfe.getSource();
        if (vegadresse.postnummer == null) {
            return null;
        }

        return poststedRepository.getPoststed(vegadresse.postnummer)
    }
}
