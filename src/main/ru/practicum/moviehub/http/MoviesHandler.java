package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private static final Gson gson = new Gson();
    private static final int MIN_YEAR = 1888;
    private static final int MAX_YEAR = java.time.LocalDate.now().getYear() + 1;
    private static final int MAX_TITLE_LENGTH = 100;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        switch (method) {
            case "GET":
                handleGet(ex);
                break;
            case "POST":
                handlePost(ex);
                break;
            case "DELETE":
                handleDelete(ex);
                break;
            default:
                sendJson(ex, 405, createErrorJson(405, "Метод не поддерживается"));
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String uriString = ex.getRequestURI().toString();
        int queryIndex = uriString.indexOf('?');
        String queryPart = queryIndex != - 1 ? uriString.substring(queryIndex + 1) : "";

        if (path.equals("/movies") && queryPart.isEmpty()) {
            List<Movie> movies = store.getAll();
            sendJson(ex, 200, gson.toJson(movies));
            return;
        }

        if (path.equals("/movies") && ! queryPart.isEmpty()) {
            if (queryPart.startsWith("year=")) {
                String yearStr = queryPart.substring(5);

                if (yearStr.length() == 4 && yearStr.matches("\\d{4}")) {
                    try {
                        int year = Integer.parseInt(yearStr);
                        List<Movie> filteredMovies = store.getByYear(year);
                        sendJson(ex, 200, gson.toJson(filteredMovies));
                    } catch (NumberFormatException e) {
                        sendJson(ex, 400, createErrorJson(400, "Некорректный параметр 'year'"));
                    }
                } else {
                    sendJson(ex, 400, createErrorJson(400, "Некорректный параметр " +
                            "запроса — 'year'"));
                }
            } else {
                sendJson(ex, 400, createErrorJson(400, "Некорректный запрос"));
            }
            return;
        }

        String[] splitStrings = path.split("/");
        if (splitStrings.length == 3 &&
                splitStrings[1].equals("movies") &&
                splitStrings[2].matches("\\d+")) {

            try {
                int id = Integer.parseInt(splitStrings[2]);
                Movie movie = store.findById(id);


                if (movie != null) {
                    sendJson(ex, 200, gson.toJson(movie));
                } else {
                    sendJson(ex, 404, createErrorJson(404, "Фильм не найден"));
                }
            } catch (NumberFormatException e) {
                sendJson(ex, 400, createErrorJson(400, "Некорректный ID"));
            }
            return;
        }

        sendJson(ex, 400, createErrorJson(400, "Некорректный запрос"));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        // Проверка Content-Type
        Map<String, List<String>> headers = ex.getRequestHeaders();
        List<String> contentTypeValues = headers.get("Content-Type");

        if (contentTypeValues == null || contentTypeValues.isEmpty() ||
                ! contentTypeValues.getFirst().contains("application/json")) {
            sendJson(ex, 415, createErrorJson(415, "Неподдерживаемый формат. " +
                    "Используйте application/json"));
            return;
        }

        try {
            // Читаем тело запроса
            InputStream inputStream = ex.getRequestBody();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // Парсим JSON
            Movie incomingMovie = gson.fromJson(requestBody, Movie.class);

            // Валидация данных
            List<String> validationErrors = validateMovie(incomingMovie);
            if (! validationErrors.isEmpty()) {
                sendJson(ex, 422, createErrorJson(422, String.join("; ", validationErrors)));
                return;
            }

            // Создаём фильм в хранилище
            Movie createdMovie = store.add(incomingMovie.getTitle(), incomingMovie.getYear());

            // Отправляем ответ с созданным фильмом
            sendJson(ex, 201, gson.toJson(createdMovie));
        } catch (JsonSyntaxException e) {
            sendJson(ex, 400, createErrorJson(400, "Invalid JSON format"));
        } catch (Exception e) {
            sendJson(ex, 500, createErrorJson(500, "Internal Server Error"));
        }
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String[] splitStrings = path.split("/");


        if (splitStrings.length == 3 &&
                splitStrings[1].equals("movies") &&
                splitStrings[2].matches("\\d+")) {

            try {
                int id = Integer.parseInt(splitStrings[2]);
                boolean isDeleted = store.deleteById(id);

                if (isDeleted) {
                    ex.sendResponseHeaders(204, - 1); // 204 No Content
                    ex.close();
                } else {
                    sendJson(ex, 404, createErrorJson(404, "Фильм не найден"));
                }
            } catch (NumberFormatException e) {
                sendJson(ex, 400, createErrorJson(400, "Некорректный ID"));
            }
            return;
        }

        sendJson(ex, 400, createErrorJson(400, "Некорректный ID"));
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("Название не может быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("Длина названия должна быть максимум 100 символов");
        }

        int year = movie.getYear();
        if (year < MIN_YEAR) {
            errors.add(String.format("Year must be at least %d", MIN_YEAR));
        } else if (year > MAX_YEAR) {
            errors.add(String.format("Year cannot be greater than %d", MAX_YEAR));
        }

        return errors;
    }

    private String createErrorJson(int code, String message) {
        return gson.toJson(new ErrorResponse(code, message));
    }
}
