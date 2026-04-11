package com.mcart.auth.utils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * RSA keys for JWT signing. In Kubernetes with multiple auth replicas, every pod must use the
 * same private key; otherwise JWKS on one pod will not match {@code kid} on tokens from another.
 */
public final class RsaKeyPairFactory {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private RsaKeyPairFactory() {}

    public static KeyPair generateEphemeral2048() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Load PKCS#1 ({@code BEGIN RSA PRIVATE KEY}) or PKCS#8 ({@code BEGIN PRIVATE KEY}) PEM.
     */
    public static KeyPair loadFromPrivateKeyPem(Path privatePemPath) {
        if (!Files.isRegularFile(privatePemPath)) {
            throw new IllegalStateException(
                    "JWT signing key file missing: "
                            + privatePemPath
                            + ". Create Secret auth-jwt-rsa (see deploy/k8s/apps/auth/deployment.yaml) "
                            + "or unset AUTH_JWT_RSA_PRIVATE_KEY_PATH for local single-process dev only.");
        }
        try (PEMParser pemParser = new PEMParser(Files.newBufferedReader(privatePemPath))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (object instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair);
            }
            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
                if (!(privateKey instanceof RSAPrivateCrtKey rsaPriv)) {
                    throw new IllegalStateException("Expected RSA private key in " + privatePemPath);
                }
                RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv.getPublicExponent());
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(pubSpec);
                return new KeyPair(publicKey, privateKey);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read RSA private key PEM: " + privatePemPath, e);
        }
        throw new IllegalStateException("Unrecognized PEM content in " + privatePemPath);
    }
}
