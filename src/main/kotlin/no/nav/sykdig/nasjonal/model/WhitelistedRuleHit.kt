package no.nav.sykdig.nasjonal.model

enum class WhitelistedRuleHit {
    INNTIL_8_DAGER,
    INNTIL_30_DAGER, //fjerne etter prodsetting av regler.
    INNTIL_1_MAANED,
    INNTIL_1_MAANED_MED_BEGRUNNELSE,
    OVER_1_MAANED,
    INNTIL_30_DAGER_MED_BEGRUNNELSE, //fjerne
    OVER_30_DAGER, //fjerne
    FREMDATERT,
    PASIENTEN_HAR_KODE_6,
}

enum class RuleHitCustomError {
    BEHANDLER_MANGLER_AUTORISASJON_I_HPR,
    BEHANDLER_SUSPENDERT,
}
