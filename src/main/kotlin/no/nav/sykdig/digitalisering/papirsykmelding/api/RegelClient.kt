package no.nav.sykdig.digitalisering.papirsykmelding.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate


@Component
class RegelClient(
    @Value("\$regel.url") private val regelUrl: String,
    private val regelRestTemplate: RestTemplate
){

}
