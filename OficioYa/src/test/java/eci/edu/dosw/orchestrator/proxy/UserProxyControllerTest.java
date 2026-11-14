package eci.edu.dosw.orchestrator.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserProxyControllerTest {

    @Test
    void proxyDevuelveBadGatewaySiElServicioEstaCaido() {
        UserProxyController controller = new UserProxyController("http://localhost:59999");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("Host", "localhost:8081");

        ResponseEntity<byte[]> response = controller.proxy(request, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void proxyPreservaQueryYMetodo() {
        UserProxyController controller = new UserProxyController("http://localhost:59999/");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setQueryString("filter=active");
        request.addHeader("Authorization", "Bearer token-123");
        request.setContent("{\"test\": true}".getBytes());

        ResponseEntity<byte[]> response = controller.proxy(request, "{\"test\": true}".getBytes());

        assertEquals(502, response.getStatusCode().value());
    }

    @Test
    void proxyEnrutaImagenesYExtraeBodyDesdeInputStreamCuandoBodyEsNull() {
        UserProxyController controller = new UserProxyController("http://localhost:59998/", "http://localhost:59999/");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/images");
        request.setContentType("multipart/form-data; boundary=---abc");
        request.setContent("binary-image-data".getBytes());

        ResponseEntity<byte[]> response = controller.proxy(request, null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}
