package no.nav.sykdig.nasjonal.mapping

import no.nav.sykdig.generated.types.Godkjenning
import no.nav.sykdig.generated.types.Kode
import no.nav.sykdig.generated.types.Sykmelder

fun mapSykmelder(sykmelder: no.nav.sykdig.nasjonal.models.Sykmelder): Sykmelder {
    return Sykmelder(
        hprNummer = sykmelder.hprNummer,
        fnr = sykmelder.fnr,
        aktorId = sykmelder.aktorId,
        fornavn = sykmelder.fornavn,
        mellomnavn = sykmelder.mellomnavn,
        etternavn = sykmelder.etternavn,
        godkjenninger = sykmelder.godkjenninger?.map { mapGodkjenninger(it) },
    )
}

fun mapGodkjenninger(godkjenninger: no.nav.sykdig.nasjonal.models.Godkjenning): Godkjenning {
    return Godkjenning(
        helsepersonellkategori = if (godkjenninger.helsepersonellkategori != null) mapKode(godkjenninger.helsepersonellkategori) else null,
        autorisasjon = if (godkjenninger.helsepersonellkategori != null) mapKode(godkjenninger.helsepersonellkategori) else null,
    )
}

fun mapKode(kode: no.nav.sykdig.nasjonal.models.Kode): Kode {
    return Kode(
        aktiv = kode.aktiv,
        oid = kode.oid,
        verdi = kode.verdi,
    )
}