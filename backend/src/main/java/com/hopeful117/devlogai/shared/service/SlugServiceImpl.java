package com.hopeful117.devlogai.shared.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SlugServiceImpl implements SlugService {
    @Override
    public String generate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be empty");
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
    }

