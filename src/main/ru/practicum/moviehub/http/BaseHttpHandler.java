package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {

        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        byte[] responseBody = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, responseBody.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(responseBody);
        }


    }

    protected void sendNoContent(HttpExchange ex) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, - 1);
        ex.close();
    }
}