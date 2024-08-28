package no.nav.syfo.nais.isready

import no.nav.syfo.ApplicationState

fun Routing.naisIsReadyRoute(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
) {
    get("/internal/is_ready") {
        if (readynessCheck()) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText(
                "Please wait! I'm not ready :(",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}
