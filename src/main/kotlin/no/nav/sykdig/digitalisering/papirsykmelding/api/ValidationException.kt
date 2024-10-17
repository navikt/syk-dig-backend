package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.digitalisering.sykmelding.ValidationResult

class ValidationException(val validationResult: ValidationResult) : Exception()
