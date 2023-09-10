import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.kcrypto.cert.dsl.*
import org.bouncycastle.kcrypto.dsl.dilithium
import org.bouncycastle.kcrypto.dsl.edDsa
import org.bouncycastle.kcrypto.dsl.signingKeyPair
import org.bouncycastle.kcrypto.dsl.using
import org.bouncycastle.kcrypto.pkcs.dsl.encryptedPrivateKey
import org.bouncycastle.kutil.findBCProvider
import org.bouncycastle.kutil.writePEMObject
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.util.*

fun main() {

    using(findBCProvider())

    var kp = signingKeyPair {
        edDsa {
            curveName = "Ed25519"
        }
    }

    var altKp = signingKeyPair {
        dilithium {
            parameterSet = "Dilithium2"
        }
    }

    var expDate = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

    var name = x500Name {
        rdn(BCStyle.C, "AU")
        rdn(BCStyle.O, "The Legion of the Bouncy Castle")
        rdn(BCStyle.L, "Melbourne")
        rdn(BCStyle.CN, "Eric H. Echidna")
        rdn(BCStyle.EmailAddress, "feedback-crypto@bouncycastle.org")
    }

    var cert = certificate {
        serialNumber = BigInteger.valueOf(1)
        issuer = name
        notAfter = expDate
        subject = name
        subjectPublicKey = kp.verificationKey

        extensions = extensions {
            subjectAltPublicKeyInfoExtension {
                publicKey = altKp.verificationKey
            }
        }

        signature {
            EdDSA using kp.signingKey
        }
        altSignature {
            Dilithium using altKp.signingKey
        }
    }

    var exts = extensions {
        authorityKeyIdentifierExtension {
            authorityKey = cert
        }
    }

    // empty CRL.
    var crl = crl {
        issuer = cert

        revocation {
            userCert = BigInteger.ONE
            reason = certificateHold
        }
        revocation {
            userCert = cert
            reason = keyCompromise
        }

        extensions = exts
        
        signature {
            EdDSA using kp.signingKey
        }
        altSignature {
            Dilithium using altKp.signingKey
        }
    }

    // Example of updating
    var crl2 = crl updateWith {

        revocation {
            userCert = BigInteger.valueOf(2)
            reason = keyCompromise
        }

        signature {
            EdDSA using kp.signingKey
        }
        altSignature {
            Dilithium using altKp.signingKey
        }
    }

    println("cert verifies " + cert.signatureVerifiedBy(cert))
    println("cert alt verifies " + cert.alternativeSignatureVerifiedBy(altKp.verificationKey))
    println("crl verifies " + crl.signatureVerifiedBy(cert))

    var encKey = encryptedPrivateKey {
        privateKey = kp.signingKey
        encryption {
            AESKWP using PBKDF2 {
                saltLength = 20
                keySize = 256
            } with "Test".toCharArray()
        }
    }

    OutputStreamWriter(System.out).writePEMObject(encKey)

    OutputStreamWriter(System.out).writePEMObject(cert)

    OutputStreamWriter(System.out).writePEMObject(crl)
}
