package com.systeam.project.config;

public class RubroConfig {

    private RubroConfig() {}

    public static int getDividendBps(int rubroId) {
        return switch (rubroId) {
            case 1 -> 2000;  // Tech          -> 20% anual
            case 2 -> 4000;  // Gastro        -> 40% anual
            case 3 -> 6000;  // Inmobiliario  -> 60% anual
            case 4 -> 5000;  // Agro          -> 50% anual
            default -> 3000; // Otros         -> 30% anual
        };
    }

    public static int getRubroIdDefault() {
        return 4; // Agro por defecto
    }
}
