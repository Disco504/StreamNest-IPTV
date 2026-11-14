package com.streamnest.iptv;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

public final class CatalogCrypto {
    private static final String KEY_ALIAS = "StreamNestCatalogKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private CatalogCrypto() {}

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry)
                    keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    public static String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());

        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(
                plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(
                4 + iv.length + encrypted.length);
        buffer.putInt(iv.length);
        buffer.put(iv);
        buffer.put(encrypted);

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    public static String decrypt(String encoded) throws Exception {
        if (encoded == null) return null;

        byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
        if (data.length < 4 + 12 + 16) {
            throw new IllegalArgumentException("Encrypted catalog data is too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int ivLength = buffer.getInt();
        if (ivLength <= 0 || ivLength > 32
                || buffer.remaining() - ivLength < 16) {
            throw new IllegalArgumentException("Invalid encrypted catalog data");
        }

        byte[] iv = new byte[ivLength];
        buffer.get(iv);

        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey(),
                new GCMParameterSpec(128, iv));

        return new String(cipher.doFinal(encrypted),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
