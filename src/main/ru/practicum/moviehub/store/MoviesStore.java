package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    private final Map<Integer, Movie> movieStore = new HashMap<>();
    private int nextId = 20;


    public List<Movie> getAll() {
        return new ArrayList<>(movieStore.values());
    }


    public Movie add(String title, int year) {
        Movie movie = new Movie(nextId, title, year);
        movieStore.put(nextId, movie);
        nextId++;
        return movie;
    }


    public Movie findById(int id) {
        return movieStore.get(id);
    }

    public List<Movie> getByYear(int year) {
        return movieStore.values().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }


    public boolean deleteById(int id) {
        return movieStore.remove(id) != null; // true, если элемент был удалён
    }

    public void clearStore() {
        movieStore.clear();
    }
}