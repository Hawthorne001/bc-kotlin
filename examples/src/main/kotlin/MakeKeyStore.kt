import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.kcrypto.Digest
import org.bouncycastle.kcrypto.cert.CertificateBuilder
import org.bouncycastle.kcrypto.cert.dsl.authorityKeyIdentifierExtension
import org.bouncycastle.kcrypto.cert.dsl.extension
import org.bouncycastle.kcrypto.cert.dsl.extensions
import org.bouncycastle.kcrypto.cert.dsl.subjectKeyIdentifierExtension
import org.bouncycastle.kcrypto.dsl.certificateEntry
import org.bouncycastle.kcrypto.dsl.file
import org.bouncycastle.kcrypto.dsl.keyEntry
import org.bouncycastle.kcrypto.dsl.pkcs12KeyStore
import org.bouncycastle.kcrypto.spec.asymmetric.PKCS1SigSpec
import org.bouncycastle.kcrypto.spec.asymmetric.RSAGenSpec
import org.bouncycastle.kutil.findBCProvider
import org.bouncycastle.kutil.writePEMObject
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.util.*

fun main() {

    KCryptoServices.setProvider(findBCProvider())

    var kp = KCryptoServices.signingKeyPair(RSAGenSpec(2048))

    val name = with(X500NameBuilder())
    {
        this.addRDN(BCStyle.C, "AU")
                .addRDN(BCStyle.O, "The Legion of the Bouncy Castle")
                .addRDN(BCStyle.L, "Melbourne")
                .addRDN(BCStyle.CN, "Eric H. Echidna")
                .addRDN(BCStyle.EmailAddress, "feedback-crypto@bouncycastle.org")
                .build()
    }

    var exts = extensions {
        extension {
            extOid = Extension.basicConstraints
            extValue = BasicConstraints(false)
        }
        subjectKeyIdentifierExtension {
            subjectKey = kp.verificationKey
        }
        authorityKeyIdentifierExtension {
            authorityKey = kp.verificationKey
        }
    }

    var expDate = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
    var cert = with(CertificateBuilder(kp.signingKey, PKCS1SigSpec(Digest.SHA256), name))
    {
        this.setNotBefore(Date())
                .setNotAfter(expDate)
                .setExtensions(exts)
                .build(BigInteger.valueOf(8), kp.verificationKey)
    }

    var pkcs12 = pkcs12KeyStore()

    pkcs12 set keyEntry {
        alias = "test"
        signingKey = kp.signingKey
        certChain = arrayOf(cert)
    }

    pkcs12 set certificateEntry {
        alias = "test2"
        certificate = cert
    }

    pkcs12 store file {
        fileName = "test.p12"
        password = "Hello, world!".toCharArray()
    }

    OutputStreamWriter(System.out).writePEMObject(pkcs12 withPassword "Hello, world!".toCharArray())

    pkcs12 = pkcs12KeyStore()

    pkcs12 load file {
            fileName = "test.p12"
            password = "Hello, world!".toCharArray()
    }

    for (alias in pkcs12.aliases()) {
        println("alias found: " + alias + " " + (pkcs12 isKeyEntry alias) + " " + (pkcs12 isCertificateEntry alias))
    }

    OutputStreamWriter(System.out).writePEMObject(pkcs12)
}
