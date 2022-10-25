package no.nav.sykdig.db

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.poststed.PostInformasjon
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class PoststedRepositoryTest : FellesTestOppsett() {
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
                PostInformasjon("9609", "NORDRE SEILAND")
            )
        )
    }

    @AfterEach
    fun after() {
        namedParameterJdbcTemplate.update("DELETE FROM postinformasjon", MapSqlParameterSource())
    }

    @Test
    fun getPoststedHenterPoststed() {
        val poststed = poststedRepository.getPoststed("5341")

        poststed shouldBeEqualTo "STRAUME"
    }

    @Test
    fun getPoststedGirNullHvisPostnummerIkkeFinnes() {
        val poststed = poststedRepository.getPoststed("0101")

        poststed shouldBeEqualTo null
    }

    @Test
    fun getAllePoststederHenterAllePoststeder() {
        val allePoststeder = poststedRepository.getAllePoststeder()

        allePoststeder.size shouldBeEqualTo 5
    }

    @Test
    fun oppdaterPoststedSletterPoststed() {
        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("5449", "BØMLO"),
                PostInformasjon("9609", "NORDRE SEILAND")
            ),
            UUID.randomUUID()
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        allePoststeder.size shouldBeEqualTo 4
        allePoststeder.find { it.postnummer == "5341" } shouldBeEqualTo null
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
                PostInformasjon("9609", "NORDRE SEILAND")
            ),
            UUID.randomUUID()
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        allePoststeder.size shouldBeEqualTo 6
        allePoststeder.find { it.postnummer == "0502" }?.poststed shouldBeEqualTo "OSLO"
    }

    @Test
    fun oppdaterPoststedSletterLeggerTilOgOppdatererPoststed() {
        poststedRepository.oppdaterPoststed(
            listOf(
                PostInformasjon("0484", "OSLO"),
                PostInformasjon("0502", "OSLO"),
                PostInformasjon("5341", "STRAUME"),
                PostInformasjon("5365", "TURØY"),
                PostInformasjon("9609", "SENJA")
            ),
            UUID.randomUUID()
        )

        val allePoststeder = poststedRepository.getAllePoststeder()
        allePoststeder.size shouldBeEqualTo 5
        allePoststeder.find { it.postnummer == "0502" }?.poststed shouldBeEqualTo "OSLO"
        allePoststeder.find { it.postnummer == "5449" } shouldBeEqualTo null
        allePoststeder.find { it.postnummer == "9609" }?.poststed shouldBeEqualTo "SENJA"
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
                PostInformasjon("9609", "NORDRE SEILAND")
            ),
            UUID.randomUUID()
        )

        val allePoststederOppdatert = poststedRepository.getAllePoststeder()
        allePoststederOppdatert shouldBeEqualTo allePoststeder
    }
}
