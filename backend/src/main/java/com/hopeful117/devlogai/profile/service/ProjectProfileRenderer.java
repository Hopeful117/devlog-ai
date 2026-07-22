package com.hopeful117.devlogai.profile.service;

import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.profile.model.ProfileSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProjectProfileRenderer {
    public static final String VERSION = "project-profile-renderer-fr-v1";
    private static final Map<String, String> PHRASES = phrases();

    public String render(List<ProfileSection> sections, ProfileCompletenessStatus completeness) {
        List<String> phrases = sections.stream()
                .flatMap(section -> section.characteristics().stream())
                .map(characteristic -> PHRASES.get(characteristic.code()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        String body = phrases.isEmpty()
                ? "Aucune caractéristique déterministe prise en charge n’a été confirmée."
                : String.join(" ", phrases);
        if (completeness == ProfileCompletenessStatus.PARTIAL) {
            return "L’analyse partielle a produit le profil suivant. " + body
                    + " Certaines caractéristiques peuvent ne pas avoir été détectées.";
        }
        return body;
    }

    private static Map<String, String> phrases() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("SPRING_BOOT_REST_APPLICATION", "Le projet est une application REST développée avec Spring Boot.");
        result.put("CONTAINERIZED_PROJECT", "Le projet est conteneurisé avec Docker et Docker Compose.");
        result.put("ADR_DOCUMENTATION", "Le projet documente ses décisions d’architecture avec des ADR.");
        result.put("AUTOMATED_TESTS", "Le projet comporte des tests automatisés.");
        result.put("INTEGRATION_TESTS", "Des tests d’intégration sont présents.");
        result.put("MULTI_MODULE_BUILD", "Le projet utilise un build multi-module.");
        return Map.copyOf(result);
    }
}
