package com.hopeful117.devlogai.project.exception;

public class ProjectSlugAlreadyExistsException extends RuntimeException {
    public ProjectSlugAlreadyExistsException(String slug) {
        super("A project already exists with slug: " + slug);
    }
}
