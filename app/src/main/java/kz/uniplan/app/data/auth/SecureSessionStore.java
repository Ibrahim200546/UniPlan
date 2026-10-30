package kz.uniplan.app.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Token snapshots only. The AES key remains in Android Keystore; no plaintext fallback. */
public final class SecureSessionStore implements SessionStorage {
    private static final String KEY_ALIAS="uniplan_supabase_session_v1";
    private final SharedPreferences preferences;
    public SecureSessionStore(Context context){this(context,"session");}
    public SecureSessionStore(Context context,String scope){preferences=context.getApplicationContext().getSharedPreferences("secure_supabase_"+scope,Context.MODE_PRIVATE);}
    private SecretKey key() throws GeneralSecurityException,IOException {
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(keys.containsAlias(KEY_ALIAS))return (SecretKey)keys.getKey(KEY_ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    public synchronized void save(String tokenSnapshot) throws GeneralSecurityException,IOException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(tokenSnapshot.getBytes(StandardCharsets.UTF_8));
        boolean saved=preferences.edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("payload",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit();
        if(!saved)throw new IOException("Cannot persist encrypted session");
    }
    public synchronized String load() throws GeneralSecurityException,IOException {
        String payload=preferences.getString("payload",null),iv=preferences.getString("iv",null);if(payload==null||iv==null)return null;
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(payload,Base64.NO_WRAP)),StandardCharsets.UTF_8);
        }catch(IllegalArgumentException invalid){throw new GeneralSecurityException("Invalid encrypted session",invalid);}
    }
    public synchronized void clear() throws IOException {
        if(!preferences.edit().clear().commit()) {
            // If preferences cannot be written, invalidate ciphertext so a restart cannot revive it.
            try { KeyStore keys=KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(KEY_ALIAS); }
            catch(GeneralSecurityException failure) { throw new IOException("Cannot invalidate encrypted session",failure); }
            throw new IOException("Cannot clear encrypted session");
        }
    }
}
