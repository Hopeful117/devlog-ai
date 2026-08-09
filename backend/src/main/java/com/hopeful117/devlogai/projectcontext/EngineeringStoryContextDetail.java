package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.shared.exception.InvalidParameterException;

import java.util.Locale;

enum EngineeringStoryContextDetail {
    AGENT,
    FULL;

    static EngineeringStoryContextDetail parse(String value) {
        if (value == null) return AGENT;
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidParameterException("detail", value);
        }
    }
}
