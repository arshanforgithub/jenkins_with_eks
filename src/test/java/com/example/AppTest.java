package com.example;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTest {

    @Test
    void testGreeting() {
        assertEquals("Hello from Jenkins on EKS with Karpenter!", App.getGreeting());
    }

    @Test
    void testAdd() {
        assertEquals(5, App.add(2, 3));
    }

    @Test
    void testAddNegative() {
        assertEquals(-1, App.add(2, -3));
    }

    // --- HTTP server tests ---
    // Started on an ephemeral port (0 = OS picks a free one), so this is
    // safe to run in CI without clashing with anything on 8080.

    static HttpServer server;
    static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new App.RootHandler());
        server.createContext("/health", new App.HealthHandler());
        server.setExecutor(null);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void rootEndpointReturnsGreeting() throws IOException {
        HttpURLConnection conn = openConnection("/");
        assertEquals(200, conn.getResponseCode());
        assertEquals(App.getGreeting(), readBody(conn));
    }

    @Test
    void healthEndpointReturns200() throws IOException {
        HttpURLConnection conn = openConnection("/health");
        assertEquals(200, conn.getResponseCode());
        assertEquals("OK", readBody(conn));
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
