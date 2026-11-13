package eci.edu.dosw.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "1bsAUISUGkohU5Y15bXvnyYXL3IColWuRszsQTsa/TcHf8lZO/z/l/NQcywT95bm";

    @Test
    void elTokenDeServicioLlevaElSubjectYElRolServicio() {
        JwtService servicio = new JwtService(SECRET);

        Claims claims = servicio.parse(servicio.generateServiceToken());

        assertEquals(JwtService.SERVICE_SUBJECT, claims.getSubject());
        assertEquals(List.of("SERVICE"), claims.get("roles", List.class));
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getIssuedAt());
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String otraClave = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        String ajeno = new JwtService(otraClave).generateServiceToken();

        assertThrows(Exception.class, () -> new JwtService(SECRET).parse(ajeno));
    }

    @Test
    void unTextoQueNoEsJwtSeRechaza() {
        assertThrows(Exception.class, () -> new JwtService(SECRET).parse("no.es.un.jwt"));
    }
}