package com.novatech.firstday;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private static final SecureRandom RANDOM=new SecureRandom();
    private Passwords() {}
    public static String hash(String password) {
        byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2_sha256$600000$"+HexFormat.of().formatHex(salt)+"$"+HexFormat.of().formatHex(derive(password,salt,600000));
    }
    public static boolean verify(String password,String encoded) {
        if(encoded==null) return false;
        try {
            String[] parts=encoded.split("\\$");
            if(parts.length!=4 || !parts[0].equals("pbkdf2_sha256")) return false;
            int iterations=Integer.parseInt(parts[1]);
            if(iterations<300000 || iterations>1000000) return false;
            byte[] salt=HexFormat.of().parseHex(parts[2]),expected=HexFormat.of().parseHex(parts[3]);
            return MessageDigest.isEqual(expected,derive(password,salt,iterations));
        } catch(IllegalArgumentException e) { return false; }
    }
    static byte[] derive(String password,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch(Exception e) { throw new IllegalStateException("Password hashing unavailable",e); }
        finally { spec.clearPassword(); }
    }
    public static String token() { byte[] bytes=new byte[48];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
