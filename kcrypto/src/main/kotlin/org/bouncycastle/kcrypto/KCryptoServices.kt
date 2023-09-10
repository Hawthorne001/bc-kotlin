import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.util.DefaultJcaJceHelper
import org.bouncycastle.jcajce.util.JcaJceHelper
import org.bouncycastle.jcajce.util.ProviderJcaJceHelper
import org.bouncycastle.kcrypto.*
import org.bouncycastle.kcrypto.internal.PBKDF2PbKdf
import org.bouncycastle.kcrypto.internal.ScryptPbKdf
import org.bouncycastle.kcrypto.spec.*
import org.bouncycastle.kcrypto.spec.asymmetric.*
import org.bouncycastle.kcrypto.spec.kdf.PBKDF2Spec
import org.bouncycastle.kcrypto.spec.kdf.ScryptSpec
import org.bouncycastle.kcrypto.spec.symmetric.*
import org.bouncycastle.pqc.jcajce.spec.*
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.spec.*
import javax.crypto.spec.SecretKeySpec

/**
 * Central place to start building cryptographic services from.
 * <p>
 * Different key types are the source of implementations for various services.
 * For example:
 * <pre>
 *   val kp = KCryptoServices.signingKeyPair(RSAGenSpec(2048))
 *
 *   val contentSigner = kp.signingKey.signatureCalculator(PKCS1SigSpec(Digest.SHA256))
 * </pre>
 * or an example of GCM using AES-256 with a random IV:
 * <pre>
 *   var bOut = ByteArrayOutputStream()
 *   var key = KCryptoServices.symmetricKey(AESGenSpec(256))
 *   var aeadEncryptor = key.encryptor(GCMSpec()).outputEncryptor(bOut)
 *
 *   aeadEncryptor.use {
 *       it.aadStream.write(Strings.toByteArray("Hello, world!"))
 *       it.encStream.write(Strings.toByteArray("Data to be encrypted!"))
 *   }
 *
 *   var encData = bOut.toByteArray()
 * </pre>
 */
class KCryptoServices {

    companion object {
        private var _helper: JcaJceHelper = DefaultJcaJceHelper()
        private var _pqcHelper: JcaJceHelper = if (Security.getProvider("BCPQC") != null) ProviderJcaJceHelper(Security.getProvider("BCPQC")) else _helper

        private var _secureRandom: SecureRandom = SecureRandom()

        internal var _provider: Provider? = null;

        fun setProvider(provider: Provider) {
            _helper = ProviderJcaJceHelper(provider)
            _provider = provider
            _secureRandom = helper.createSecureRandom("DEFAULT")
        }

        internal fun isPQC(name: String): Boolean
        {
             return name.equals("FALCON", true) || name.startsWith("1.3.9999.3")
                     || name.equals("SPHINCSPLUS", true) || name.equals("SPHINCS+", true) || name.startsWith(BCObjectIdentifiers.sphincsPlus.id)
                     || name.equals("DILITHIUM", true) || name.startsWith("1.3.6.1.4.1.2.267.12")
        }

        internal fun helperFor(algorithm: String): JcaJceHelper
        {
            return if (isPQC(algorithm)) _pqcHelper else _helper
        }

        internal val helper: JcaJceHelper get() = _helper

        internal val pqcHelper: JcaJceHelper get() = _pqcHelper

        val secureRandom: SecureRandom get() = _secureRandom

        /**
         * Generate a new key pair for signature creation and verification.
         *
         * @param keyGenSpec specification for the key pair to generate.
         * @return a new key pair for creating and verifying signatures.
         */
        fun signingKeyPair(keyGenSpec: SignGenSpec): SigningKeyPair {
            when (keyGenSpec) {
                is RSAGenSpec -> {
                    val kpGen = helper.createKeyPairGenerator("RSA");
                    kpGen.initialize(RSAKeyGenParameterSpec(keyGenSpec.keySize, keyGenSpec.publicExponent), keyGenSpec.random)
                    return SigningKeyPair(KeyPair(kpGen.generateKeyPair()));
                }
                is ECGenSpec -> {
                    val ecGen = helper.createKeyPairGenerator("EC")
                    ecGen.initialize(ECGenParameterSpec(keyGenSpec.curveName), keyGenSpec.random)
                    return SigningKeyPair(KeyPair(ecGen.genKeyPair()))
                }
                is EdDSAGenSpec -> {
                    val edGen = helper.createKeyPairGenerator("EDDSA")
                    edGen.initialize(ECGenParameterSpec(keyGenSpec.curveName), keyGenSpec.random)
                    return SigningKeyPair(KeyPair(edGen.genKeyPair()))
                }
                is DSAGenSpec -> {
                    val dsaGen = helper.createKeyPairGenerator("DSA")
                    val dsaSpec = DSAParameterSpec(keyGenSpec.domainParameters.p, keyGenSpec.domainParameters.q, keyGenSpec.domainParameters.g)
                    dsaGen.initialize(dsaSpec, keyGenSpec.random)
                    return SigningKeyPair(KeyPair(dsaGen.genKeyPair()))
                }
                is FalconGenSpec -> {
                    val falconGen = _pqcHelper.createKeyPairGenerator("Falcon")
                    val falconSpec = FalconParameterSpec.fromName(keyGenSpec.parameterSet)
                    falconGen.initialize(falconSpec, keyGenSpec.random)
                    return SigningKeyPair(KeyPair(falconGen.genKeyPair()))
                }
                is DilithiumGenSpec -> {
                    val dilithiumGen = _pqcHelper.createKeyPairGenerator("Dilithium")
                    val dilithiumSpec = DilithiumParameterSpec.fromName(keyGenSpec.parameterSet)
                    dilithiumGen.initialize(dilithiumSpec, keyGenSpec.random)
                    return SigningKeyPair(KeyPair(dilithiumGen.genKeyPair()))
                }
                is SPHINCSPlusGenSpec -> {
                    val sphincsPlusGen = _pqcHelper.createKeyPairGenerator("SPHINCSPlus")
                    val sphincsPlusSpec = SPHINCSPlusParameterSpec.fromName(keyGenSpec.parameterSet)
                    sphincsPlusGen.initialize(sphincsPlusSpec, keyGenSpec.random)
                    return SigningKeyPair(KeyPair(sphincsPlusGen.genKeyPair()))
                }
                is LMSGenSpec -> {
                    val lmsGen = _pqcHelper.createKeyPairGenerator("LMS")
                    val lmsSpec = LMSKeyGenParameterSpec.fromNames(keyGenSpec.sigParameterSet, keyGenSpec.otsParameterSet)
                    lmsGen.initialize(lmsSpec, keyGenSpec.random)
                    return SigningKeyPair(KeyPair(lmsGen.genKeyPair()))
                }
                else ->
                    throw IllegalArgumentException("unknown KeyGenSpec")
            }
        }


        /**
         * Generate a new key pair for encryption and decryption.
         *
         * @param keyGenSpec specification for the key pair to generate.
         * @return a new key pair for encrypting and decrypting messages.
         */
        fun encryptingKeyPair(keySpec: EncPairGenSpec): EncryptingKeyPair {
            if (keySpec is RSAGenSpec) {
                val kpGen = helper.createKeyPairGenerator("RSA");
                kpGen.initialize(RSAKeyGenParameterSpec(keySpec.keySize, keySpec.publicExponent), keySpec.random)
                return EncryptingKeyPair(KeyPair(kpGen.generateKeyPair()));
            }
            if (keySpec is KyberGenSpec) {
                val kpGen = pqcHelper.createKeyPairGenerator("Kyber");
                kpGen.initialize(KyberParameterSpec.fromName(keySpec.parameterSet), keySpec.random)
                return EncryptingKeyPair(KeyPair(kpGen.generateKeyPair()));
            }
            if (keySpec is NTRUGenSpec) {
                val kpGen = pqcHelper.createKeyPairGenerator("NTRU");
                kpGen.initialize(NTRUParameterSpec.fromName(keySpec.parameterSet), keySpec.random)
                return EncryptingKeyPair(KeyPair(kpGen.generateKeyPair()));
            }
            throw IllegalArgumentException("unknown KeyGenSpec")
        }

        /**
         * Create a public key for signature verification from a DER byte representation.
         *
         * @param encodedKey the DER encoding of the key.
         * @param keyType the type of the key to return.
         * @return a VerificationKey
         */
        fun verificationKey(encodedKey: ByteArray, keyType: KeyType<VerificationKey>): VerificationKey {
            var algorithm = if (keyType.algorithm.equals("Verification"))
                                SubjectPublicKeyInfo.getInstance(encodedKey).algorithm.algorithm.id else keyType.algorithm
            var kf = helperFor(algorithm).createKeyFactory(algorithm)

            return BaseVerificationKey(kf.generatePublic(X509EncodedKeySpec(encodedKey)))
        }

        /**
         * Create a public key for message encryption from a DER byte representation.
         *
         * @param encodedKey the DER encoding of the key.
         * @param keyType the type of the key to return.
         * @return an EncryptionKey
         */
        fun encryptionKey(encodedKey: ByteArray, keyType: KeyType<EncryptionKey>): EncryptionKey {
            var algorithm = if (keyType.algorithm.equals("Encryption"))
                                SubjectPublicKeyInfo.getInstance(encodedKey).algorithm.algorithm.id else keyType.algorithm
            var kf = helperFor(algorithm).createKeyFactory(algorithm)

            return BaseEncryptionKey(kf.generatePublic(X509EncodedKeySpec(encodedKey)))
        }

        /**
         * Create a private key for signature creation from a DER byte representation.
         *
         * @param encodedKey the DER encoding of the key.
         * @param keyType the type of the key to return.
         * @return an SigningKey
         */
        fun signingKey(encodedKey: ByteArray, keyType: KeyType<SigningKey>): SigningKey {
            var algorithm = if (keyType.algorithm.equals("Signing"))
                PrivateKeyInfo.getInstance(encodedKey).privateKeyAlgorithm.algorithm.id else keyType.algorithm
            var kf = helperFor(algorithm).createKeyFactory(algorithm)

            return BaseSigningKey(kf.generatePrivate(PKCS8EncodedKeySpec(encodedKey)))
        }

        /**
         * Create a private key for message decryption from a DER byte representation.
         *
         * @param encodedKey the DER encoding of the key.
         * @param keyType the type of the key to return.
         * @return a DecryptionKey
         */
        fun decryptionKey(encodedKey: ByteArray, keyType: KeyType<DecryptionKey>): DecryptionKey {
            var algorithm = if (keyType.algorithm.equals("Decryption"))
                            PrivateKeyInfo.getInstance(encodedKey).privateKeyAlgorithm.algorithm.id else keyType.algorithm
            var kf = helperFor(algorithm).createKeyFactory(algorithm)

            return BaseDecryptionKey(kf.generatePrivate(PKCS8EncodedKeySpec(encodedKey)))
        }

        /**
         * Generate a general purpose symmetric key.
         *
         * @param keyGenSpec specification for symmetric key to generate.
         * @return a new symmetric key.
         */
        fun symmetricKey(keyGenSpec: KeyGenSpec): SymmetricKey {
            if (keyGenSpec is AESGenSpec) {
                val kpGen = helper.createKeyGenerator("AES");

                kpGen.init(keyGenSpec.keySize, keyGenSpec.random)

                return BaseSymmetricKey(keyGenSpec.keySize, kpGen.generateKey());
            }
            throw IllegalArgumentException("unknown KeyGenSpec")
        }

        /**
         * Create a general purpose symmetric key from a raw byte representation.
         *
         * @param rawKey the key bytes.
         * @param keyType the type of the key to return.
         * @return a SymmetricKey
         */
        fun symmetricKey(rawKey: ByteArray, keyType: KeyType<SymmetricKey>): SymmetricKey {
            return BaseSymmetricKey(rawKey.size * 8, SecretKeySpec(rawKey, keyType.algorithm))
        }

        /**
         * Return a password based KDF operator based on the passed in specifications.
         *
         * @param kdfConfig details of the PBKDF to use for key generation.
         * @param keySpec specification for the key to be generated.
         * @return a PBKDF operator.
         */
        fun pbkdf(kdfConfig: PBKDFAlgSpec<AlgorithmIdentifier>, keySpec: KeyGenSpec): PBKDF {
            if (kdfConfig is ScryptSpec) {
                val skdf = kdfConfig

                return ScryptPbKdf(skdf, keySpec)
            }
            if (kdfConfig is PBKDF2Spec) {
                val skdf = kdfConfig

                return PBKDF2PbKdf(skdf, keySpec)
            }
            throw IllegalArgumentException("unknown PBKDF requested")
        }

        /**
         * Generate a symmetric key for use with MAC generation only.
         *
         * @param keyGenSpec specification for symmetric key to generate.
         * @return a new authentication key.
         */
        fun authenticationKey(keyGenSpec: AuthGenSpec): AuthenticationKey {
            val keyType: KeyType<out Any> = keyGenSpec.authType

            val keySize = when (keyGenSpec) {
                is AESGenSpec -> keyGenSpec.keySize
                is HMacSHA1GenSpec -> keyGenSpec.keySize
                is HMacSHA224GenSpec -> keyGenSpec.keySize
                is HMacSHA256GenSpec -> keyGenSpec.keySize
                is HMacSHA384GenSpec -> keyGenSpec.keySize
                is HMacSHA512GenSpec -> keyGenSpec.keySize
                else -> throw IllegalArgumentException("unknown key requested for MAC")
            }

            val kpGen = helper.createKeyGenerator(keyType.algorithm)

            kpGen.init(keySize, (keyGenSpec as KeyGenSpec).random)

            if (keyGenSpec is AESGenSpec) {
                return BaseMacKey(keySize, kpGen.generateKey(), keyType)
            } else {
                return BaseHMacKey(keySize, kpGen.generateKey(), keyType)
            }
        }

        /**
         * Create a symmetric key from a raw byte representation for use with MAC generation only.
         *
         * @param rawKey the key bytes.
         * @param keyType the type of the key to return.
         * @return an AuthenticationKey
         */
        fun authenticationKey(rawKey: ByteArray, keyType: KeyType<AuthenticationKey>): AuthenticationKey {
            if (keyType.algorithm.startsWith("HMac", true)) {
                return BaseHMacKey(rawKey.size * 8,
                        SecretKeySpec(rawKey, keyType.algorithm), keyType)
            } else {
                return BaseMacKey(rawKey.size * 8, SecretKeySpec(rawKey, keyType.algorithm), keyType)
            }
        }
    }
}