package no.nav.sykdig.utenlandsk.db

import java.sql.ResultSet
import java.util.UUID
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.poststed.PostInformasjon
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
class PoststedRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    val log = applog()

    fun getPoststed(postnummer: String): String? {
        return namedParameterJdbcTemplate
            .query(
                """
            SELECT poststed
                    FROM postinformasjon
                    where postnummer = :postnummer;
            """,
                mapOf("postnummer" to postnummer),
            ) { resultSet, _ ->
                resultSet.getString("poststed")
            }
            .firstOrNull()
    }

    fun oppdaterPoststed(oppdatertPostinformasjon: List<PostInformasjon>, sporingsId: UUID) {
        val postinfoFraDb = getAllePoststeder()
        if (
            postinfoFraDb.size == oppdatertPostinformasjon.size &&
                postinfoFraDb.toHashSet() == oppdatertPostinformasjon.toHashSet()
        ) {
            log.info("Ingen endringer for $sporingsId, avslutter...")
            return
        }

        val oppdatertPostinformasjonMap: HashMap<String, PostInformasjon> =
            HashMap(oppdatertPostinformasjon.associateBy { it.postnummer })
        val postinfoFraDbMap: HashMap<String, PostInformasjon> =
            HashMap(postinfoFraDb.associateBy { it.postnummer })

        val slettesfraDb =
            postinfoFraDbMap.filter { oppdatertPostinformasjonMap[it.key] == null }.keys
        val oppdateresIDb =
            oppdatertPostinformasjonMap.filter { postinfoFraDbMap[it.key] != it.value }

        slettesfraDb.forEach {
            namedParameterJdbcTemplate.update(
                """
            DELETE FROM postinformasjon
            where postnummer = :postnummer;
        """,
                mapOf("postnummer" to it),
            )
        }
        oppdateresIDb.forEach {
            namedParameterJdbcTemplate.update(
                """
            INSERT INTO postinformasjon(postnummer, poststed)
            VALUES (:postnummer, :poststed)
            ON CONFLICT (postnummer) DO UPDATE SET poststed = :poststed;
        """,
                mapOf("postnummer" to it.key, "poststed" to it.value.poststed),
            )
        }
    }

    fun getAllePoststeder(): List<PostInformasjon> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT postnummer,
                    poststed
                    FROM postinformasjon;
            """
        ) { resultSet, _ ->
            resultSet.toPostInformasjon()
        }
    }

    fun lagrePostinformasjonForTest(postinformasjon: List<PostInformasjon>) {
        postinformasjon.forEach {
            namedParameterJdbcTemplate.update(
                """
            INSERT INTO postinformasjon(postnummer, poststed)
            VALUES (:postnummer, :poststed);
        """,
                mapOf("postnummer" to it.postnummer, "poststed" to it.poststed),
            )
        }
    }
}

private fun ResultSet.toPostInformasjon(): PostInformasjon =
    PostInformasjon(postnummer = getString("postnummer"), poststed = getString("poststed"))
