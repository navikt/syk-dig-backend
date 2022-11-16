package no.nav.sykdig.digitalisering.ferdigstilling.mapping

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
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.sykdig.digitalisering.ValidatedOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.generated.types.PeriodeInput
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.TeleCom
import no.nav.helse.sm2013.URL
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeType

fun mapToFellesformat(
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
                                        syketilfelleStartDato = tilSyketilfelleStartDato(validatedValues)
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
                                        arbeidsgiver = tilArbeidsgiver()
                                        medisinskVurdering = tilMedisinskVurdering(validatedValues.hovedDiagnose, validatedValues.biDiagnoser)
                                        aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                                            periode.addAll(tilPeriodeListe(validatedValues.perioder))
                                        }
                                        prognose = null
                                        utdypendeOpplysninger = null
                                        tiltak = null
                                        meldingTilNav = null
                                        meldingTilArbeidsgiver = null
                                        kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                                            kontaktDato = validatedValues.behandletTidspunkt.toLocalDate()
                                            begrunnIkkeKontakt = null
                                            behandletDato = validatedValues.behandletTidspunkt.toLocalDateTime()
                                        }
                                        behandler = tilBehandler()
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

fun tilBehandler(): HelseOpplysningerArbeidsuforhet.Behandler =
    HelseOpplysningerArbeidsuforhet.Behandler().apply {
        navn = NavnType().apply {
            fornavn = ""
            mellomnavn = ""
            etternavn = ""
        }
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

fun tilMedisinskVurdering(hovedDiagnoseInput: DiagnoseInput, biDiagnoserInput: List<DiagnoseInput>):
        HelseOpplysningerArbeidsuforhet.MedisinskVurdering {

    val biDiagnoseListe: List<CV> = biDiagnoserInput.map {
        toMedisinskVurderingDiagnode(it)
    }

    return HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
        hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
            diagnosekode = toMedisinskVurderingDiagnode(hovedDiagnoseInput)
        }
        if (biDiagnoseListe.isNotEmpty()){
            biDiagnoser = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.BiDiagnoser().apply {
                diagnosekode.addAll(biDiagnoseListe)
            }
        }

        isSkjermesForPasient = false
        annenFraversArsak = null
        isSvangerskap = false
        isYrkesskade = false
        yrkesskadeDato = null
    }
}

fun toMedisinskVurderingDiagnode(diagnose: DiagnoseInput): CV =
    CV().apply {
        s = diagnose.system
        v = diagnose.kode
        dn = ""
    }

fun tilSyketilfelleStartDato(
    validatedValues: ValidatedOppgaveValues
): LocalDate {
    return validatedValues.perioder.stream().map(PeriodeInput::fom).min(LocalDate::compareTo).get()
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
        aktivitetIkkeMulig = if (periode.type == PeriodeType.AKTIVITET_IKKE_MULIG) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                medisinskeArsaker = null
                arbeidsplassen = null
            }
        } else {
            null
        }
        avventendeSykmelding = if (periode.type == PeriodeType.AVVENTENDE) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AvventendeSykmelding().apply {
                innspillTilArbeidsgiver = null
            }
        } else {
            null
        }

        gradertSykmelding = if (periode.type == PeriodeType.GRADERT && periode.grad != null && periode.grad > 100) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding().apply {
                sykmeldingsgrad = periode.grad
                isReisetilskudd = false
            }
        } else {
            null
        }

        behandlingsdager = if (periode.type == PeriodeType.BEHANDLINGSDAGER) {
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.Behandlingsdager().apply {
            antallBehandlingsdagerUke = antallBehanldingsDager(periode)
            }
        } else {
            null
        }

        isReisetilskudd = periode.type == PeriodeType.REISETILSKUDD
    }

fun antallBehanldingsDager(periode: PeriodeInput): Int =
    (periode.fom..periode.tom).daysBetween().toInt()

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun tilArbeidsgiver(): HelseOpplysningerArbeidsuforhet.Arbeidsgiver =
    HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
        harArbeidsgiver = CS().apply {
            dn = "Én arbeidsgiver"
            v = "1"
        }

        navnArbeidsgiver = ""
        yrkesbetegnelse = ""
        stillingsprosent = null
    }
