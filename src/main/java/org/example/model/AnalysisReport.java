package org.example.model;

import java.util.List;

public record AnalysisReport(String inputJar, ProjectMetrics project,
                             List<ClassMetrics> classes, List<InterfaceMetrics> interfaces) {
    public AnalysisReport {
        classes = List.copyOf(classes);
        interfaces = List.copyOf(interfaces);
    }
}
