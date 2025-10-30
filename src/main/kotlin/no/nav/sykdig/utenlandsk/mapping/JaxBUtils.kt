package no.nav.sykdig.utenlandsk.mapping

import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Marshaller.JAXB_ENCODING
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

private val fellesformatMarshallerContext: JAXBContext =
    JAXBContext.newInstance(
        XMLEIFellesformat::class.java,
        XMLMsgHead::class.java,
        HelseOpplysningerArbeidsuforhet::class.java,
    )

fun createMarshaller(): Marshaller =
    fellesformatMarshallerContext.createMarshaller().apply { setProperty(JAXB_ENCODING, "UTF-8") }

fun Marshaller.toString(input: Any): String =
    StringWriter().use {
        marshal(input, it)
        it.toString()
    }
