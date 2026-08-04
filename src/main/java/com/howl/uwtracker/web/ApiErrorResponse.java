package com.howl.uwtracker.web;

public record ApiErrorResponse(String error, Object details) {

    public ApiErrorResponse(String error) {
        this(error, null);
    }
}
