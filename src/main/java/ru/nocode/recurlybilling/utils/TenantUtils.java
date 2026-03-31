package ru.nocode.recurlybilling.utils;

import java.util.UUID;

public class TenantUtils {

    /**
     * Генератор id тенантов
     * @param organizationName название организации
     * @return сгенерированный id тенанта
     */
    public static String generateTenantId(String organizationName) {
        String cleanName = organizationName.toLowerCase()
                .replaceAll("[^a-zа-я0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (cleanName.isEmpty()) {
            return "tenant_" + UUID.randomUUID().toString().substring(0, 8);
        }

        return cleanName;
    }
}
