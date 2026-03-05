package ru.practicum.moviehub.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static final com.google.gson.Gson gson = new com.google.gson.Gson();
    private static final int PORT = 8080;
    private static final MoviesStore moviesStore = new MoviesStore();

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(moviesStore, PORT);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clearStore();

    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenHasMovies_returnsListWithAddedMovies() throws Exception {

        MoviesStore store = server.getStore();

        Movie movie1 = new Movie(0, "Title 1", 2020);
        Movie movie2 = new Movie(0, "Title 2", 2021);

        store.add(movie1.getTitle(), movie1.getYear());
        store.add(movie2.getTitle(), movie2.getYear());


        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(
                getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));


        assertEquals(200, getResp.statusCode(), "GET /movies должен вернуть 200");


        Optional<String> contentTypeHeader = getResp.headers().firstValue("Content-Type");
        assertTrue(contentTypeHeader.isPresent(), "Заголовок Content-Type должен присутствовать");
        assertEquals("application/json; charset=UTF-8", contentTypeHeader.get(),
                "Content-Type должен содержать формат данных и кодировку");

        String body = getResp.body().trim();
        List<Movie> movies = gson.fromJson(body, new ListOfMoviesTypeToken().getType());


        assertEquals(2, movies.size(), "Массив должен содержать 2 фильма");


        Movie firstMovie = movies.get(0);
        assertTrue(firstMovie.getId() > 0, "ID первого фильма должен быть положительным числом " +
                "(назначен сервером)");
        assertEquals("Title 1", firstMovie.getTitle(), "Название первого фильма должно " +
                "быть 'Title 1'");
        assertEquals(2020, firstMovie.getYear(), "Год первого фильма должен быть 2020");


        Movie secondMovie = movies.get(1);
        assertTrue(secondMovie.getId() > 0, "ID второго фильма должен быть положительным " +
                "числом (назначен сервером)");
        assertEquals("Title 2", secondMovie.getTitle(), "Название второго фильма должно " +
                "быть 'Title 2'");
        assertEquals(2021, secondMovie.getYear(), "Год второго фильма должен быть 2021");
    }

    @Test
    void postMovie_withValidData_createsMovieAndReturns201() throws Exception {
        // ARRANGE
        Movie movieToCreate = new Movie(0, "Valid Movie", 2023);
        String movieJson = gson.toJson(movieToCreate);

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .header("Content-Type", "application/json")
                .build();


        HttpResponse<String> postResp = client.send(
                postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));


        assertEquals(201, postResp.statusCode(), "POST /movies должен вернуть 201 Created");

        Optional<String> contentTypeHeader = postResp.headers().firstValue("Content-Type");
        assertTrue(contentTypeHeader.isPresent(), "Заголовок Content-Type должен присутствовать");
        assertEquals("application/json; charset=UTF-8", contentTypeHeader.get(),
                "Content-Type должен быть application/json");

        JsonObject createdMovie = JsonParser.parseString(postResp.body()).getAsJsonObject();
        assertTrue(createdMovie.has("id"), "Ответ должен содержать поле id");
        assertEquals("Valid Movie", createdMovie.get("title").getAsString());
        assertEquals(2023, createdMovie.get("year").getAsInt());
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        String invalidJson = "{\"title\":\"\",\"year\":2023}";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> postResp = client.send(
                postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));


        assertEquals(422, postResp.statusCode(), "Должен вернуть 422 при пустом title");
        JsonObject error = JsonParser.parseString(postResp.body()).getAsJsonObject();
        assertEquals("Название не может быть пустым", error.get("message").getAsString());
    }

    @Test
    void postMovie_withLongTitle_returns422() throws Exception {
        String longTitle = "A".repeat(101);
        String invalidJson = String.format("{\"title\":\"%s\",\"year\":2023}", longTitle);

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> postResp = client.send(
                postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));


        assertEquals(422, postResp.statusCode(), "Должен вернуть 422 при title > 100 символов");
        JsonObject error = JsonParser.parseString(postResp.body()).getAsJsonObject();
        assertEquals("Длина названия должна быть максимум 100 символов", error.get("message").getAsString());
    }

    @Test
    void postMovie_withInvalidYear_returns422() throws Exception {
        int currentYear = java.time.LocalDate.now().getYear();


        // Год < 1888
        String invalidJson1 = String.format("{\"title\":\"Movie\",\"year\":%d}", 1887);
        HttpRequest postReq1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson1))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> postResp1 = client.send(postReq1,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, postResp1.statusCode());


        // Год > currentYear + 1
        String invalidJson2 = String.format("{\"title\":\"Movie\",\"year\":%d}", currentYear + 2);
        HttpRequest postReq2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson2))
                .header("Content-Type", "application/json")
                .build();
        HttpResponse<String> postResp2 = client.send(postReq2,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, postResp2.statusCode());
    }

    @Test
    void postMovie_withWrongContentType_returns415() throws Exception {
        String movieJson = "{\"title\":\"Movie\",\"year\":2023}";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> postResp = client.send(
                postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, postResp.statusCode(), "Должен вернуть 415 при неверном Content-Type");
    }

    @Test
    void postMovie_withMalformedJson_returns400() throws Exception {
        String malformedJson = "{\"title\":\"Movie\",\"year\":2023";

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(malformedJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> postResp = client.send(
                postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, postResp.statusCode(), "Должен вернуть 400 при некорректном JSON");
    }

    @Test
    void getMovie_byExistingId_returns200() throws Exception {

        MoviesStore store = server.getStore();
        Movie expectedMovie = store.add("The Matrix", 1999);
        int movieId = expectedMovie.getId();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .GET()
                .build();


        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );


        assertEquals(200, response.statusCode(), "Должен вернуть 200 OK для существующего ID");


        Movie actualMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(expectedMovie.getId(), actualMovie.getId(), "ID фильма должен совпадать");
        assertEquals(expectedMovie.getTitle(), actualMovie.getTitle(), "Название должно совпадать");
        assertEquals(expectedMovie.getYear(), actualMovie.getYear(), "Год должен совпадать");
    }

    @Test
    void getMovie_byNonExistingId_returns404() throws Exception {
        int nonExistingId = 999;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + nonExistingId))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(404, response.statusCode(), "Должен вернуть 404 Not Found для " +
                "несуществующего ID");

        JsonObject errorResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(404, errorResponse.get("code").getAsInt(), "Код ошибки должен быть 404");
        assertEquals("Фильм не найден", errorResponse.get("message").getAsString(), "Сообщение " +
                "должно быть 'Фильм не найден'");
    }

    @Test
    void getMovie_byInvalidIdFormat_returns400() throws Exception {
        String invalidId = "abc";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + invalidId))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, response.statusCode(), "Должен вернуть 400 Bad Request для нецифрового ID");

        JsonObject errorResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(400, errorResponse.get("code").getAsInt(), "Код ошибки должен быть 400");
        assertEquals("Некорректный запрос", errorResponse.get("message").getAsString(), "Сообщение " +
                "должно быть 'Некорректный ID'");
    }

    @Test
    void deleteMovie_existingId_returns204() throws Exception {
        Movie movie = moviesStore.add("Test Movie", 2020);
        int id = movie.getId();
        URI uri = URI.create(BASE + "/movies/" + id);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode(), "Должен вернуть 204 No Content " +
                "при успешном удалении");
        assertTrue(response.body().isEmpty(), "Тело ответа должно быть пустым при 204");

        assertNull(moviesStore.findById(id), "Фильм должен быть удалён из хранилища");
    }

    @Test
    void deleteMovie_nonExistingId_returns404() throws Exception {
        int nonExistingId = 999;
        URI uri = URI.create(BASE + "/movies/" + nonExistingId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode(), "Должен вернуть 404 Not Found для " +
                "несуществующего ID");

        JsonObject errorResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(404, errorResponse.get("code").getAsInt());
        assertEquals("Фильм не найден", errorResponse.get("message").getAsString());
    }

    @Test
    void deleteMovie_invalidIdFormat_returns400() throws Exception {
        String invalidId = "abc";
        URI uri = URI.create(BASE + "/movies/" + invalidId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), "Должен вернуть 400 Bad Request для нецифрового ID");

        JsonObject errorResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(400, errorResponse.get("code").getAsInt());


    }

    @Test
    void getMoviesByYear_returnsFilmsForSpecifiedYear() throws Exception {
        moviesStore.add("Inception", 2010);
        moviesStore.add("Tenet", 2020);
        moviesStore.add("Dune", 2021);
        moviesStore.add("No Time to Die", 2020);

        URI uri = URI.create(BASE + "/movies?year=2020");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Должен вернуть 200 OK для корректного года");

        JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
        assertEquals(2, jsonArray.size(), "Должно вернуться 2 фильма за 2020 год");

        for (JsonElement element : jsonArray) {
            JsonObject movie = element.getAsJsonObject();
            assertEquals(2020, movie.get("year").getAsInt());
        }
    }

    @Test
    void getMoviesByYear_returnsEmptyListWhenNoFilmsForYear() throws Exception {
        moviesStore.add("Inception", 2010);
        moviesStore.add("Tenet", 2020);

        URI uri = URI.create(BASE + "/movies?year=1999");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Должен вернуть 200 OK даже если фильмов нет");

        JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
        assertTrue(jsonArray.isEmpty(), "Тело ответа должно быть пустым массивом []");
    }

    @Test
    void getMoviesByYear_returns400WhenYearIsNotANumber() throws Exception {
        String[] invalidYears = { "20a0", "202", "20202", "abc", "20.5" };

        for (String yearValue : invalidYears) {
            URI uri = URI.create(BASE + "/movies?year=" + yearValue);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode(),
                    "Должен вернуть 400 Bad Request для некорректного year=" + yearValue);

            JsonObject errorResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            assertEquals(400, errorResponse.get("code").getAsInt());
            assertTrue(
                    errorResponse.get("message").getAsString().contains("year") ||
                            errorResponse.get("message").getAsString().contains("запрос"),
                    "Сообщение должно указывать на ошибку параметра 'year'"
            );
        }
    }

}