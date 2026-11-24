package org.example.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.model.AnalysisReport;
import org.example.model.AbcMetrics;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class ReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ReportWriter() {
    }

    public static void writeJson(AnalysisReport report, Path output) throws IOException {
        Path destination = output.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".bytecode-", ".json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(report));
                writer.write('\n');
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void print(AnalysisReport report, PrintStream out) {
        var project = report.project();
        out.println("JAR: " + report.inputJar());
        out.printf("Классы: %d; интерфейсы: %d%n", project.classCount(), project.interfaceCount());
        out.printf("Максимальная глубина наследования: %d%n", project.maximumInheritanceDepth());
        out.printf(Locale.ROOT, "Средняя глубина наследования: %.6f%n", project.averageInheritanceDepth());
        out.printf(Locale.ROOT, "Среднее количество переопределённых методов: %.6f%n", project.averageOverriddenMethods());
        out.printf(Locale.ROOT, "Среднее количество полей: %.6f%n", project.averageFields());
        out.println("ABC проекта: " + format(project.abc()));
        for (var type : report.classes()) {
            out.printf("%s [%s]: depth=%d; overrides=%d; fields=%d; %s%n", type.name(), type.kind(),
                    type.inheritanceDepth(), type.overriddenMethodCount(), type.fieldCount(), format(type.abc()));
        }
        for (var type : report.interfaces()) {
            out.printf("%s [interface]: fields=%d; %s%n", type.name(), type.fieldCount(), format(type.abc()));
        }
    }

    private static String format(AbcMetrics abc) {
        return String.format(Locale.ROOT, "A=%d; B=%d; C=%d; ABC=%.6f",
                abc.assignments(), abc.branches(), abc.conditions(), abc.value());
    }
}
