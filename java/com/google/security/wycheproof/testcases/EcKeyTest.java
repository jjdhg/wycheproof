/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO(bleichen): RFC 3279 allows ECKeys with a number of different parameters.
//   E.g. public keys can specify the order, base points etc.
//   We might want to check how well these parameters are verified when parsing
//   a public key.
package com.google.security.wycheproof;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** EC tests */
@RunWith(JUnit4.class)
public class EcKeyTest {
  /**
   * Encodings of public keys with invalid parameters. There are multiple places where a provider
   * can validate a public key: some parameters are typically validated by the KeyFactory, more
   * validation can be done by the cryptographic primitive. Unused parameters are sometimes not
   * validated at all.
   *
   * <p>This following test vectors are public key encodings with invalid parameters where we expect
   * that KeyFactory.generatePublic recognizes the problem. The documentation simply claims that an
   * InvalidKeySpecException is thrown if the given key specification is inappropriate but does not
   * specify what an appropriate key exactly is. Nonetheless we expect that the following minimal
   * validations are performed: order is a positive integer, cofactor is a small positive integer.
   * Some modifications may not be detected and must be caught by the primitives using them. E.g.,
   * it is expensive to verify the order of the group generated by the generator and hence the key
   * factory may not verify the correctness of this parameter. Thus an implementation of ECDH must
   * not trust an order claimed in the public key.
   *
   * <p>TODO(bleichen): The encoding is defined in https://tools.ietf.org/html/rfc3279 Section
   * 2.3.5. This document defines a few additional requirements and options which are not yet
   * checked: - OID for id-public-key_type must be ansi-X9.62 2 - OID for id-ecPublicKey must be
   * id-publicKeyType 1 - The intended application for the key may be indicated in the key usage
   * field (RFC 3280). - EcpkParameters can be implicitlyCA (not sure how we would specify the curve
   * in this case) - the version is always 1 - the points on the curves can be either compressed or
   * uncompressed (so far all points are uncompressed) - the seed value is optional (so far no test
   * vector specifies the seed) - the cofactor is optional but must be included for ECDH keys. (so
   * far all test vectors have a cofactor)
   *
   * <p>RFC 3279 also specifies curves over binary fields. Because of attacks against such curves,
   * i.e. "New algorithm for the discrete logarithm problem on elliptic curves" by I.Semaev
   * https://eprint.iacr.org/2015/310 such curves should no longer be used and hence testing them
   * has low priority.
   */
  public static final String[] EC_INVALID_PUBLIC_KEYS = {
    // order = -115792089210356248762697446949407573529996955224135760342422259061068512044369
    "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f50221ff00000000ffffffff0000000000000000"
        + "4319055258e8617b0c46353d039cdaaf02010103420004cdeb39edd03e2b1a11"
        + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
        + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // order = 0
    "308201123081cb06072a8648ce3d02013081bf020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5020002010103420004cdeb39edd03e2b1a11a5"
        + "e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49"
        + "bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = -1
    "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
        + "bce6faada7179e84f3b9cac2fc6325510201ff03420004cdeb39edd03e2b1a11"
        + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
        + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = 0
    "308201323081eb06072a8648ce3d02013081df020101302c06072a8648ce3d01"
        + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
        + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
        + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
        + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
        + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
        + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
        + "bce6faada7179e84f3b9cac2fc632551020003420004cdeb39edd03e2b1a11a5"
        + "e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49"
        + "bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
    // cofactor = 115792089210356248762697446949407573529996955224135760342422259061068512044369
    "308201553082010d06072a8648ce3d020130820100020101302c06072a8648ce"
        + "3d0101022100ffffffff00000001000000000000000000000000ffffffffffff"
        + "ffffffffffff30440420ffffffff00000001000000000000000000000000ffff"
        + "fffffffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0"
        + "cc53b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277"
        + "037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162b"
        + "ce33576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffff"
        + "ffffbce6faada7179e84f3b9cac2fc632551022100ffffffff00000000ffffff"
        + "ffffffffffbce6faada7179e84f3b9cac2fc63255103420004cdeb39edd03e2b"
        + "1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b842959"
        + "8c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
  };

  @Test
  public void testEncodedPublicKey() {
    KeyFactory kf;
    try {
      kf = KeyFactory.getInstance("EC");
    } catch (NoSuchAlgorithmException ex) {
      TestUtil.skipTest("EC not supported");
      return;
    }
    for (String encodedHex : EC_INVALID_PUBLIC_KEYS) {
      byte[] encoded = TestUtil.hexToBytes(encodedHex);
      X509EncodedKeySpec x509keySpec = new X509EncodedKeySpec(encoded);
      try {
        ECPublicKey unused = (ECPublicKey) kf.generatePublic(x509keySpec);
        fail("Constructed invalid public key from:" + encodedHex);
      } catch (InvalidKeySpecException ex) {
        // OK, since the public keys have been modified.
        System.out.println(ex.toString());
      }
    }
  }

  @Test
  public void testEncodedPrivateKey() {
    KeyPairGenerator keyGen;
    KeyFactory kf;
    KeyPair keyPair;
    try {
      kf = KeyFactory.getInstance("EC");
      keyGen = KeyPairGenerator.getInstance("EC");
      keyGen.initialize(EcUtil.getNistP256Params());
      keyPair = keyGen.generateKeyPair();
    } catch (GeneralSecurityException ex) {
      TestUtil.skipTest("Could not generate EC key.");
      return;
    }
    ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();
    byte[] encoded = priv.getEncoded();
    System.out.println("Encoded ECPrivateKey:" + TestUtil.bytesToHex(encoded));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
    ECPrivateKey decoded;
    try {
      decoded = (ECPrivateKey) kf.generatePrivate(spec);
    } catch (GeneralSecurityException ex) {
      fail("Provider cannot parse its own encoding." + ex);
      return;
    }
    assertEquals(priv.getS(), decoded.getS());
    assertEquals(priv.getParams().getCofactor(), decoded.getParams().getCofactor());
    assertEquals(priv.getParams().getCurve(), decoded.getParams().getCurve());
    assertEquals(priv.getParams().getGenerator(), decoded.getParams().getGenerator());
    assertEquals(priv.getParams().getOrder(), decoded.getParams().getOrder());
  }

  /** Tests key generation for given parameters. */
  void testKeyGeneration(ECParameterSpec ecParams) throws Exception {
    KeyPair keyPair;
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
      keyGen.initialize(ecParams);
      keyPair = keyGen.generateKeyPair();
    } catch (GeneralSecurityException ex) {
      TestUtil.skipTest("Cannot generate curve");
      return;
    }
    ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
    ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();
    EcUtil.checkPublicKey(pub);
    BigInteger s = priv.getS();
    // Check the length of s. Could fail with probability 2^{-32}.
    int orderSize = ecParams.getOrder().bitLength();
    assertTrue(s.bitLength() >= orderSize - 32);
    // TODO(bleichen): correct curve?
    // TODO(bleichen): use RandomUtil
  }

  /**
   * Checks key generation for secp224r1. This curve has been removed from OpenJDK
   * https://bugs.openjdk.org/browse/JDK-8235710 Some provider (e.g. BouncyCastle and Conscrypt
   * support this curve.
   */
  @Test
  public void testKeyGenerationSecp224r1() throws Exception {
    testKeyGeneration(EcUtil.getNistP224Params());
  }

  @Test
  public void testKeyGenerationSecp256r1() throws Exception {
    testKeyGeneration(EcUtil.getNistP256Params());
  }

  @Test
  public void testKeyGenerationSecp384r1() throws Exception {
    testKeyGeneration(EcUtil.getNistP384Params());
  }

  @Test
  public void testKeyGenerationSecp521r1() throws Exception {
    testKeyGeneration(EcUtil.getNistP521Params());
  }

  @Test
  public void testKeyGenerationPrime239v1() throws Exception {
    testKeyGeneration(EcUtil.getPrime239v1Params());
  }

  /**
   * Checks key generation for the 256-bit brainpool curve. This curve has been removed from OpenJDK
   * https://bugs.openjdk.org/browse/JDK-8235710 Some provider (e.g. BouncyCastle) support this
   * curve. In other cases (e.g. Conscrypt) it is possible to use this curve by specifying the curve
   * parameters explicitly.
   */
  @Test
  public void testKeyGenerationBrainpoolP256r1() throws Exception {
    testKeyGeneration(EcUtil.getBrainpoolP256r1Params());
  }

  /**
   * Checks that the default behavior of an uninitialized EC key pair generator.
   *
   * <p>The requirements for passing this test are rather low. The test only expects that an
   * uninitialized key pair generator uses a at least a 224-bit curve. Generally, it is a good idea
   * to avoid such defaults at all, since they are difficult to change and easily become outdated.
   * Additionally, there is no guarantee that providers use curves that are well supported. For
   * example, jdk20 uses secp384r1 as default (https://bugs.java.com/view_bug.do?bug_id=8283475),
   * BouncyCastle v. 1.71 uses prime239v1 and Conscrypt uses secp256r1.
   *
   * <p>NIST SP 800-57 part1 revision 4, Table 2, page 53
   * http://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r4.pdf for the minimal
   * key size of EC keys. Nist recommends a minimal security strength of 112 bits for the time until
   * 2030. To achieve this security strength EC keys of at least 224 bits are required. After 2030 a
   * minimal security strength of 128 and hence 256-bit curves is required.
   *
   * <p>https://bugs.openjdk.org/browse/JDK-8235710 removes all elliptic curves with security
   * strength significantly smaller than 128 bits. Hence, the default curve used by BouncyCastle is
   * not supported by jdk.
   */
  @Test
  public void testDefaultKeyGeneration() {
    KeyPairGenerator keyGen;
    try {
      keyGen = KeyPairGenerator.getInstance("EC");
    } catch (GeneralSecurityException ex) {
      TestUtil.skipTest("Could not generate EC key");
      return;
    }
    // Generates a key pair without initializing keyGen.
    // Unfortunately, the interface of generateKeyPair does not declare a checked exception.
    // Hence providers are forced to define and use some default parameters.
    KeyPair keyPair = keyGen.generateKeyPair();
    ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
    System.out.println("Default parameters for EC key generation:");
    EcUtil.printParameters(pub.getParams());
    int keySize = pub.getParams().getCurve().getField().getFieldSize();
    if (keySize < 224) {
      fail("Expected a default key size of at least 224 bits. Size of generated key is " + keySize);
    }
  }

  /**
   * Tries to generate a public key with a point at infinity. Public keys with a point at infinity
   * should be rejected to prevent subgroup confinement attacks.
   */
  @Test
  public void testPublicKeyAtInfinity() {
    ECParameterSpec ecSpec = EcUtil.getNistP256Params();
    try {
      ECPublicKeySpec pubSpec = new ECPublicKeySpec(ECPoint.POINT_INFINITY, ecSpec);
      fail(
          "Point at infinity is not a valid public key. "
              + pubSpec.getW().equals(ECPoint.POINT_INFINITY));
    } catch (java.lang.IllegalArgumentException ex) {
      // This is expected
    }
  }
}
