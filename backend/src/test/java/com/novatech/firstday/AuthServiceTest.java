package com.novatech.firstday;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final Db db = mock(Db.class);
    private final AuthService auth = new AuthService(db, mock(Templates.class));
    private final UUID employee = UUID.randomUUID();
    private final String token = "synthetic-session-token";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Basic credentials", "Bearer ", "Bearer", "Token value"})
    void rejectsMissingOrUnsupportedCredentialsBeforeDatabaseAccess(String header) {
        assertEquals(401, assertThrows(ApiException.class, () -> auth.current(header)).status);
        verifyNoInteractions(db);
    }

    @Test
    void resolvesOnlyTheDigestOfTheProvidedSession() {
        Map<String, Object> user = Db.map("id", employee, "role", "EMPLOYEE");
        when(db.optional(anyString(), eq(Passwords.digest(token)))).thenReturn(user);
        assertSame(user, auth.current("Bearer " + token));
        verify(db).optional(contains("s.expires_at>NOW()"), eq(Passwords.digest(token)));
    }

    @Test
    void expiredOrRevokedSessionCannotAuthenticate() {
        when(db.optional(anyString(), eq(Passwords.digest(token)))).thenReturn(null);
        assertEquals(401, assertThrows(ApiException.class, () -> auth.current("Bearer " + token)).status);
    }

    @Test
    void employeeCannotUseAdministratorOperations() {
        when(db.optional(anyString(), anyString())).thenReturn(Db.map("id", employee, "role", "EMPLOYEE"));
        assertEquals(403, assertThrows(ApiException.class, () -> auth.admin("Bearer " + token)).status);
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void administratorKeepsTheAuthenticatedIdentity() {
        var admin = Db.map("id", employee, "role", "ADMIN");
        when(db.optional(anyString(), anyString())).thenReturn(admin);
        assertSame(admin, auth.admin("Bearer " + token));
    }

    @Test
    void logoutRevokesOnlyTheAuthenticatedSessionDigest() {
        when(db.optional(anyString(), anyString())).thenReturn(Db.map("id", employee, "role", "EMPLOYEE"));
        auth.logout("Bearer " + token);
        verify(db).update(contains("SET revoked_at=NOW() WHERE token_hash=?"), eq(Passwords.digest(token)));
    }

    @Test
    void unknownLoginDoesNotCreateSession() {
        assertEquals(401, assertThrows(ApiException.class,
            () -> auth.login(new ApiDtos.Login("unknown@example.invalid", "Synthetic-password-123"))).status);
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void wrongPasswordDoesNotCreateSession() {
        when(db.optional(anyString(), eq("employee@example.invalid"))).thenReturn(
            Db.map("id", employee, "password_hash", Passwords.hash("Synthetic-correct-password")));
        assertEquals(401, assertThrows(ApiException.class,
            () -> auth.login(new ApiDtos.Login("employee@example.invalid", "Synthetic-wrong-password"))).status);
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulLoginNormalizesEmailAndNeverReturnsPasswordHash() {
        when(db.optional(anyString(), eq("employee@example.invalid"))).thenReturn(
            Db.map("id", employee, "role", "EMPLOYEE", "password_hash", Passwords.hash("Synthetic-password-123")));
        var result = auth.login(new ApiDtos.Login("  EMPLOYEE@example.invalid  ", "Synthetic-password-123"));
        var returned = (Map<String, Object>) result.get("user");
        assertEquals(employee, returned.get("id"));
        assertFalse(returned.containsKey("password_hash"));
        assertEquals("bearer", result.get("token_type"));
        assertEquals(64, ((String) result.get("access_token")).length());
    }
}
