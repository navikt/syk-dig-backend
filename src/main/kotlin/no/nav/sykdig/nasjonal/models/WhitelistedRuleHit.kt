package no.nav.sykdig.nasjonal.models

enum class WhitelistedRuleHit {
    INNTIL_8_DAGER,
    INNTIL_1_MAANED, //fjernes
    MINDRE_ENN_1_MAANED,
    INNTIL_1_MAANED_MED_BEGRUNNELSE, //fjernes
    MINDRE_ENN_1_MAANED_MED_BEGRUNNELSE,
    OVER_1_MAANED,
    FREMDATERT,
    PASIENTEN_HAR_KODE_6,
}

enum class RuleHitCustomError {
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR,
    BEHANDLER_SUSPENDERT,
}
