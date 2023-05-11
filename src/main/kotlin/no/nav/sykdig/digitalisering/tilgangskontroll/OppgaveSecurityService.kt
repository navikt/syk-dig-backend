package no.nav.sykdig.digitalisering.tilgangskontroll

import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.auditlog
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.logger
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(val syfoTilgangskontrollOboClient: SyfoTilgangskontrollOboClient, val sykDigOppgaveService: SykDigOppgaveService) {

    companion object {
        private val log = logger()
        private val auditlog = auditlog()
    }
    fun hasAccessToOppgave(oppgaveId: String, dfe: DataFetchingEnvironment): Boolean {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val navEmail: String = dfe.graphQlContext.get("username")
        if (!syfoTilgangskontrollOboClient.sjekkTilgangVeileder(oppgave.fnr)) {
            log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
            auditlog.info(
                AuditLogger().createcCefMessage(
                    fnr = oppgave.fnr,
                    navEmail = navEmail,
                    operation = AuditLogger.Operation.WRITE,
                    requestPath = "/api/graphql",
                    permit = AuditLogger.Permit.DENY,
                ),
            )
            return false
        }
        log.info("Innlogget bruker har tilgang til oppgave med id $oppgaveId")
        auditlog.info(
            AuditLogger().createcCefMessage(
                fnr = oppgave.fnr,
                navEmail = navEmail,
                operation = AuditLogger.Operation.WRITE,
                requestPath = "/api/graphql",
                permit = AuditLogger.Permit.PERMIT,
            ),
        )
        return true
    }
}
