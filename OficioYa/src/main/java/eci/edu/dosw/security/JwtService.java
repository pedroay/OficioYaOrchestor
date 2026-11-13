package eci.edu.dosw.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Manejo de JWT en el orquestador. Solo VALIDA los tokens de usuario (los emite Identity)
 * y genera el token de servicio con el que el orquestador llama a Identity y a Users.
 * El secreto HS256 es el mismo que usa Identity (variable JWT_SECRET, Base64).
 */
@Component
public class JwtService {

    public static final String SERVICE_SUBJECT = "00000000-0000-0000-0000-000000000001";
    private static final long SERVICE_TOKEN_MILLIS = 5 * 60 * 1000L;

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /** Token interno de vida corta con rol SERVICE para llamadas entre servicios. */
    public String generateServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SERVICE_SUBJECT)
                .claim("roles", List.of("SERVICE"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(SERVICE_TOKEN_MILLIS)))
                .signWith(key)
                .compact();
    }

    /** Verifica firma y vigencia. Lanza JwtException si el token no es válido. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}