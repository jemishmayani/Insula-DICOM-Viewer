/*
 * Insula DICOM Viewer
 * Copyright (C) 2026 Jemish Mayani
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Licensed under the GNU GPL v3 or later, with an additional permission for JJ2000. See LICENSE and NOTICE.
 */
package com.insula.dicomviewer;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.Charset;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts short secrets (PACS passwords, tokens) with an AES key that never leaves the Android Keystore. */
final class Vault {
    static final String ALIAS = "insula_vault";
    static final Charset UTF8 = Charset.forName("UTF-8");

    static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    /** @return "v1:" + base64(iv || ciphertext), "" for an empty secret, or null if the device can't encrypt. */
    static String enc(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV(), ct = c.doFinal(s.getBytes(UTF8));
            byte[] all = new byte[1 + iv.length + ct.length];
            all[0] = (byte) iv.length;
            System.arraycopy(iv, 0, all, 1, iv.length);
            System.arraycopy(ct, 0, all, 1 + iv.length, ct.length);
            return "v1:" + Base64.encodeToString(all, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    static String dec(String s) {
        if (s == null || s.isEmpty()) return "";
        if (!s.startsWith("v1:")) return "";
        try {
            byte[] all = Base64.decode(s.substring(3), Base64.NO_WRAP);
            int n = all[0];
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 1, n));
            return new String(c.doFinal(all, 1 + n, all.length - 1 - n), UTF8);
        } catch (Exception e) {
            return "";
        }
    }
}
