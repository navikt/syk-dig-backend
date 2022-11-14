package no.nav.sykdig.utils

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLCS
import no.nav.helse.msgHead.XMLCV
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLMsgInfo
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.helse.msgHead.XMLReceiver
import no.nav.helse.msgHead.XMLRefDoc
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sm2013.URL
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.SporsmalSvar
import no.nav.sykdig.digitalisering.ValidatedOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

fun mapToFellesformat(
    oppgave: DigitaliseringsoppgaveDbModel,
    validatedValues: ValidatedOppgaveValues,
    person: Person,
    sykmeldingId: String,
    datoOpprettet: LocalDateTime?,
    journalpostId: String
): XMLEIFellesformat {
    return XMLEIFellesformat().apply {
        any.add(
            XMLMsgHead().apply {
                msgInfo = XMLMsgInfo().apply {
                    type = XMLCS().apply {
                        dn = "Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding"
                        v = "SYKMELD"
                    }
                    miGversion = "v1.2 2006-05-24"
                    genDate = datoOpprettet ?: LocalDateTime.of(validatedValues.perioder.first().fom, LocalTime.NOON)
                    msgId = sykmeldingId
                    ack = XMLCS().apply {
                        dn = "Ja"
                        v = "J"
                    }
                    sender = XMLSender().apply {
                        comMethod = XMLCS().apply {
                            dn = "EDI"
                            v = "EDI"
                        }
                        organisation = XMLOrganisation().apply {}
                    }
                    receiver = XMLReceiver().apply {
                        comMethod = XMLCS().apply {
                            dn = "EDI"
                            v = "EDI"
                        }
                        organisation = XMLOrganisation().apply {
                            organisationName = "NAV"
                            ident.addAll(
                                listOf(
                                    XMLIdent().apply {
                                        id = "79768"
                                        typeId = XMLCV().apply {
                                            dn = "Identifikator fra Helsetjenesteenhetsregisteret (HER-id)"
                                            s = "2.16.578.1.12.4.1.1.9051"
                                            v = "HER"
                                        }
                                    },
                                    XMLIdent().apply {
                                        id = "889640782"
                                        typeId = XMLCV().apply {
                                            dn = "Organisasjonsnummeret i Enhetsregister (Brønøysund)"
                                            s = "2.16.578.1.12.4.1.1.9051"
                                            v = "ENH"
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                document.add(
                    XMLDocument().apply {
                        refDoc = XMLRefDoc().apply {
                            msgType = XMLCS().apply {
                                dn = "XML-instans"
                                v = "XML"
                            }
                            content = XMLRefDoc.Content().apply {
                                any.add(
                                    HelseOpplysningerArbeidsuforhet().apply {
                                        syketilfelleStartDato = tilSyketilfelleStartDato(oppgave, validatedValues)
                                        pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                                            navn = NavnType().apply {
                                                fornavn = person.navn.fornavn
                                                mellomnavn = person.navn.mellomnavn
                                                etternavn = person.navn.etternavn
                                            }
                                            fodselsnummer = Ident().apply {
                                                id = validatedValues.fnrPasient
                                                typeId = CV().apply {
                                                    dn = "Fødselsnummer"
                                                    s = "2.16.578.1.12.4.1.1.8116"
                                                    v = "FNR"
                                                }
                                            }
                                        }
                                        arbeidsgiver = tilArbeidsgiver(oppgave.sykmelding?.sykmelding?.arbeidsgiver)
                                        medisinskVurdering =
                                            tilMedisinskVurdering(
                                                oppgave.sykmelding?.sykmelding?.medisinskVurdering,
                                                false
                                            )
                                        aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                            periode.addAll(tilPeriodeListe(validatedValues.perioder))
                                        }
                                        prognose = null
                                        utdypendeOpplysninger =
                                            if (oppgave.sykmelding?.sykmelding?.utdypendeOpplysninger?.isNotEmpty() == true) tilUtdypendeOpplysninger(
                                                oppgave.sykmelding.sykmelding.utdypendeOpplysninger
                                            ) else null
                                        tiltak = null
                                        meldingTilNav = null
                                        meldingTilArbeidsgiver = null
                                        kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                                            kontaktDato = oppgave.sykmelding?.sykmelding?.kontaktMedPasient?.kontaktDato
                                            begrunnIkkeKontakt =
                                                oppgave.sykmelding?.sykmelding?.kontaktMedPasient?.begrunnelseIkkeKontakt
                                            behandletDato =
                                                oppgave.sykmelding?.sykmelding?.behandletTidspunkt?.toLocalDateTime()
                                        }
                                        behandler = tilBehandler(oppgave.sykmelding?.sykmelding?.behandler)
                                        avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                                            systemNavn = "syk-dig"
                                            systemVersjon =
                                                journalpostId
                                        }
                                        strekkode = "123456789qwerty"
                                    }
                                )
                            }
                        }
                    }
                )
            }
        )
    }
}

fun tilBehandler(behandler: Behandler?): HelseOpplysningerArbeidsuforhet.Behandler =
    HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = behandler?.fornavn
            mellomnavn = behandler?.mellomnavn
            etternavn = behandler?.etternavn
        }
        id.addAll(
            listOf(
                Ident().apply {
                    id = behandler?.fnr
                    typeId = CV().apply {
                        dn = "Fødselsnummer"
                        s = "2.16.578.1.12.4.1.1.8327"
                        v = "FNR"
                    }
                },
                Ident().apply {
                    id = behandler?.hpr
                    typeId = CV().apply {
                        dn = "HPR-nummer"
                        s = "2.16.578.1.12.4.1.1.8116"
                        v = "HPR"
                    }
                }
            )
        )
        adresse = Address()
        kontaktInfo.add(
            TeleCom().apply {
                typeTelecom = CS().apply {
                    v = "HP"
                    dn = "Hovedtelefon"
                }
                teleAddress = URL().apply {
                    v = "tel:55553336"
                }
            }
        )
    }
fun tilUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>):
    HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger {
    return HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
        spmGruppe.addAll(tilSpmGruppe(utdypendeOpplysninger))
    }
}

fun tilSpmGruppe(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): List<HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe> {

    val listeSpmGruppe = ArrayList<HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe>()

    utdypendeOpplysninger.map {
        listeSpmGruppe.add(
            HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe().apply {
                spmGruppeId = it.key
                spmGruppeTekst = it.key
                spmSvar.addAll(
                    it.value.map {
                        DynaSvarType().apply {
                            spmId = it.key
                            spmTekst = it.value.sporsmal
                            restriksjon = DynaSvarType.Restriksjon().apply {
                                it.value.restriksjoner.map {
                                    restriksjonskode.add(
                                        CS().apply {
                                            v = it.codeValue
                                            dn = it.text
                                        }
                                    )
                                }
                            }
                            svarTekst = it.value.svar
                        }
                    }
                )
            }
        )
    }

    return listeSpmGruppe
}

fun tilSyketilfelleStartDato(
    oppgave: DigitaliseringsoppgaveDbModel,
    validatedValues: ValidatedOppgaveValues
): LocalDate {
    return oppgave.sykmelding?.sykmelding?.syketilfelleStartDato
        ?: validatedValues.perioder.stream().map(PeriodeInput::fom).min(LocalDate::compareTo).get()
}

fun tilPeriodeListe(perioder: List<PeriodeInput>): List<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode> {
    return ArrayList<HelseOpplysningerArbeidsuforhet.Aktivitet.Periode>().apply {
        addAll(
            perioder.map {
                tilHelseOpplysningerArbeidsuforhetPeriode(it)
            }
        )
    }
}

fun tilHelseOpplysningerArbeidsuforhetPeriode(periode: PeriodeInput): HelseOpplysningerArbeidsuforhet.Aktivitet.Periode =
    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
        periodeFOMDato = periode.fom
        periodeTOMDato = periode.tom
        aktivitetIkkeMulig = null
        avventendeSykmelding = null

        gradertSykmelding = if (periode.type == PeriodeType.GRADERT) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                sykmeldingsgrad = periode.grad!!
                isReisetilskudd = false
            }
        } else {
            null
        }

        behandlingsdager = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
            antallBehandlingsdagerUke = antallBehanldingsDager(periode)
        }

        isReisetilskudd = periode.type == PeriodeType.REISETILSKUDD
    }

fun antallBehanldingsDager(periode: PeriodeInput): Int =
    (periode.fom..periode.tom).daysBetween().toInt()

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun tilArbeidsgiver(arbeidsgiver: Arbeidsgiver?): HelseOpplysningerArbeidsuforhet.Arbeidsgiver =
    HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
        harArbeidsgiver =
            when (arbeidsgiver?.harArbeidsgiver) {
                HarArbeidsgiver.EN_ARBEIDSGIVER -> CS().apply {
                    dn = "Én arbeidsgiver"
                    v = "1"
                }

                HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> CS().apply {
                    dn = "Flere arbeidsgivere"
                    v = "2"
                }

                HarArbeidsgiver.INGEN_ARBEIDSGIVER -> CS().apply {
                    dn = "Ingen arbeidsgiver"
                    v = "3"
                }

                else -> {
                    CS().apply {
                        dn = "Ingen arbeidsgiver"
                        v = "3"
                    }
                }
            }

        navnArbeidsgiver = arbeidsgiver?.navn
        yrkesbetegnelse = arbeidsgiver?.yrkesbetegnelse
        stillingsprosent = arbeidsgiver?.stillingsprosent
    }

fun tilMedisinskVurdering(
    medisinskVurdering: MedisinskVurdering?,
    skjermesForPasient: Boolean
): HelseOpplysningerArbeidsuforhet.MedisinskVurdering {

    val biDiagnoseListe: List<CV>? = medisinskVurdering?.biDiagnoser?.map {
        toMedisinskVurderingDiagnode(it)
    }

    return HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
        if (medisinskVurdering?.hovedDiagnose != null) {
            hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                diagnosekode = toMedisinskVurderingDiagnode(medisinskVurdering.hovedDiagnose!!)
            }
        }
        if (!biDiagnoseListe.isNullOrEmpty()) {
            biDiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser().apply {
                diagnosekode.addAll(biDiagnoseListe)
            }
        }
        isSkjermesForPasient = skjermesForPasient
        annenFraversArsak = medisinskVurdering?.annenFraversArsak?.let {
            ArsakType().apply {
                arsakskode.add(CS())
                beskriv = medisinskVurdering.annenFraversArsak!!.beskrivelse
            }
        }
        isSvangerskap = medisinskVurdering?.svangerskap
        isYrkesskade = medisinskVurdering?.yrkesskade
        yrkesskadeDato = medisinskVurdering?.yrkesskadeDato
    }
}

fun toMedisinskVurderingDiagnode(diagnose: Diagnose): CV =
    CV().apply {
        s = diagnose.system
        v = diagnose.kode
        dn = diagnose.tekst
    }
