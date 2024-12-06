package no.nav.sykdig.utils

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.WhitelistedRuleHit
import no.nav.sykdig.digitalisering.sykmelding.RuleInfo

fun List<RuleInfo>.isWhitelisted(): Boolean {
    return this.all { (ruleName) ->
        val isWhiteListed =
            enumValues<WhitelistedRuleHit>().any { enumValue -> enumValue.name == ruleName }
        isWhiteListed
    }
}
