package org.example.model;

public record ProjectMetrics(int classCount, int interfaceCount, int maximumInheritanceDepth,
                             double averageInheritanceDepth, double averageOverriddenMethods,
                             double averageFields, AbcMetrics abc) {
}
