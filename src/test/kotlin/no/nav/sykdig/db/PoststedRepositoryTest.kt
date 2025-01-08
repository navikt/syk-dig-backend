package no.nav.sykdig.db

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.utenlandsk.poststed.PostInformasjon
import no.nav.sykdig.utenlandsk.db.PoststedRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class PoststedRepositoryTest : IntegrationTest() {
    @Autowired
    lateinit var poststedRepository: PoststedRepository

    @BeforeEach
    fun before() {
        poststedRepository.lagrePostinformasjonForTest(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("5341", "STRAUME"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("5449", "BØMLO"),
                PostInformasjon("9609", "NORDRE SEILAND"),
            ),
        )
    }

    @AfterEach
    fun after() {
        namedParameterJdbcTemplate.update("DELETE FROM postinformasjon", MapSqlParameterSource())
    }

    @Test
    fun getPoststedHenterPoststed() {
        val poststed = poststedRepository.getPoststed("5341")

        assertEquals("STRAUME", poststed)
    }

    @Test
    fun getPoststedGirNullHvisPostnummerIkkeFinnes() {
        val poststed = poststedRepository.getPoststed("0101")

        assertEquals(null, poststed)
    }

    @Test
    fun getAllePoststederHenterAllePoststeder() {
        val allePoststeder = poststedRepository.getAllePoststeder()

        assertEquals(5, allePoststeder.size)
    }

    @Test
    fun oppdaterPoststedSletterPoststed() {
        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("5449", "BØMLO"),
                PostInformasjon("9609", "NORDRE SEILAND"),
            ),
            UUID.randomUUID(),
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        assertEquals(4, allePoststeder.size)
        assertEquals(null, allePoststeder.find { it.postnummer == "5341" })
    }

    @Test
    fun oppdaterPoststedLeggerTilPoststed() {
        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("0502", "OSLO"),
                PostInformasjon("5341", "STRAUME"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("5449", "BØMLO"),
                PostInformasjon("9609", "NORDRE SEILAND"),
            ),
            UUID.randomUUID(),
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        assertEquals(6, allePoststeder.size)
        assertEquals("OSLO", allePoststeder.find { it.postnummer == "0502" }?.poststed)
    }

    @Test
    fun oppdaterPoststedSletterLeggerTilOgOppdatererPoststed() {
        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("0502", "OSLO"),
                PostInformasjon("5341", "STRAUME"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("9609", "SENJA"),
            ),
            UUID.randomUUID(),
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        assertEquals(5, allePoststeder.size)
        assertEquals("OSLO", allePoststeder.find { it.postnummer == "0502" }?.poststed)
        assertEquals(null, allePoststeder.find { it.postnummer == "5449" })
        assertEquals("SENJA", allePoststeder.find { it.postnummer == "9609" }?.poststed)
    }

    @Test
    fun oppdaterPoststedEndrerIngentingHvisIngenEndringer() {
        val allePoststeder = poststedRepository.getAllePoststeder()

        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("5341", "STRAUME"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("5449", "BØMLO"),
                PostInformasjon("9609", "NORDRE SEILAND"),
            ),
            UUID.randomUUID(),
        )

        val allePoststederOppdatert = poststedRepository.getAllePoststeder()
        assertEquals(allePoststeder, allePoststederOppdatert)
    }
}
