package org.bouncycastle.kcrypto.cert.dsl

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.kcrypto.PublicKey
import org.bouncycastle.kcrypto.VerificationKey
import org.bouncycastle.kcrypto.cert.Certificate
import org.bouncycastle.kcrypto.cert.ExtensionsBuilder

class ExtensionsBody
{
    internal val extensions: MutableList<Ext> = ArrayList<Ext>()
    internal val extUtils = JcaX509ExtensionUtils()

    fun addExtension(e: Ext)
    {
        extensions.add(e)
    }

    fun markLastCritical(ve: Ext): Ext
    {
        var e: Ext = extensions.last()

        extensions.removeAt(extensions.size - 1)

        if (e != ve) {
            throw IllegalStateException("extension confusion!")
        }
        
        var ce = Ext(true);
        ce.extOid = e.extOid
        ce.extValue = e.extValue;

        extensions.add(ce)

        return ce
    }

    fun build(): Extensions
    {
        var bldr = ExtensionsBuilder()

        for (e in extensions)
        {
            bldr.addExtension(e.extOid, e.isCritical, e.extValue)
        }

        return bldr.build()
    }
}

fun extensions(block: ExtensionsBody.() -> Unit): Extensions = ExtensionsBody().apply(block).build()

fun ExtensionsBody.extension(block: Ext.() -> Unit): Ext
{
    var e = Ext(false).apply(block)
    addExtension(e)
    return e
}

infix fun ExtensionsBody.critical(e: Ext): Ext {
    return markLastCritical(e)
}

fun ExtensionsBody.subjectKeyIdentifierExtension(block: ExtSubjectKeyId.() -> Unit): Ext
{
    var es = ExtSubjectKeyId().apply(block)

    var e = Ext(false)
    e.extOid = Extension.subjectKeyIdentifier
    e.extValue = extUtils.createSubjectKeyIdentifier(SubjectPublicKeyInfo.getInstance(es.subjectKey.encoding))

    addExtension(e)

    return e
}

fun ExtensionsBody.basicConstraintsExtension(block: ExtBasicConstraints.() -> Unit): Ext
{
    var es = ExtBasicConstraints().apply(block)

    var e = Ext(false)
    e.extOid = Extension.basicConstraints
    if (es.pathLen >= 0) {
        e.extValue = BasicConstraints(es.pathLen)
    } else {
        e.extValue = BasicConstraints(es.isCA)
    }
    addExtension(e)

    return e
}

fun ExtensionsBody.authorityKeyIdentifierExtension(block: ExtAuthorityKeyId.() -> Unit): Ext
{
    var ea = ExtAuthorityKeyId().apply(block)

    var e = Ext(false)
    e.extOid = Extension.authorityKeyIdentifier

    if (ea.authorityKey is Certificate) {
        e.extValue = extUtils.createAuthorityKeyIdentifier((ea.authorityKey as Certificate)._cert)
    }
    else if (ea.authorityKey is VerificationKey)
    {
        e.extValue = extUtils.createAuthorityKeyIdentifier(SubjectPublicKeyInfo.getInstance((ea.authorityKey as VerificationKey).encoding))
    }
    else if (ea.authorityKey is SubjectPublicKeyInfo)
    {
        e.extValue = extUtils.createAuthorityKeyIdentifier(ea.authorityKey as SubjectPublicKeyInfo)
    }
    else
    {
        throw IllegalArgumentException("unknown authorityKey type")
    }

    addExtension(e)

    return e
}

fun ExtensionsBody.emailAltNameExtension(block: ExtAltName.() -> Unit): Ext
{
    var es = ExtAltName().apply(block)

    var e = Ext(false)
    e.extOid = Extension.subjectAlternativeName
    e.extValue = GeneralNames(GeneralName(1, es.email))
    addExtension(e)

    return e
}

data class Ext(internal val isCritical: Boolean = false)
{
    lateinit var extOid: ASN1ObjectIdentifier
    lateinit var extValue: ASN1Encodable
}

data class ExtSubjectKeyId(var isCritical: Boolean = false)
{
    lateinit var subjectKey: PublicKey
}

data class ExtBasicConstraints(var isCritical: Boolean = false)
{
    var pathLen: Int = -1
    var isCA: Boolean = false
}

data class ExtAuthorityKeyId(var isCritical: Boolean = false)
{
    lateinit var authorityKey: Any
}

data class ExtAltName(var isCritical: Boolean = false)
{
    lateinit var email: String
}

