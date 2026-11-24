package org.example.model;

public record ClassMetrics(String name, String kind, int inheritanceDepth,
                           int overriddenMethodCount, int fieldCount, AbcMetrics abc) {
}
