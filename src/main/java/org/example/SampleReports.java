package org.example;

import org.example.analyzer.JarAnalyzer;
import org.example.report.ReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Reproducible reports for the archives supplied with the assignment. */
public final class SampleReports {
    private SampleReports() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Ожидаются каталог примеров и каталог отчётов");
        for (String name : List.of("sample", "fescar", "guava")) {
            var report = new JarAnalyzer().analyze(Path.of(args[0], name + ".jar"));
            Path output = Path.of(args[1], name + ".json");
            ReportWriter.writeJson(report, output);
            System.out.printf("%s: %d классов, %d интерфейсов -> %s%n", name,
                    report.project().classCount(), report.project().interfaceCount(), output);
        }
    }
}
