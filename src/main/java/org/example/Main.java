package org.example;

import org.example.analyzer.JarAnalyzer;
import org.example.report.ReportWriter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            usage(out);
            return 0;
        }
        if (args.length < 1 || args.length > 2) {
            err.println("Ошибка: укажите входной JAR и, при необходимости, путь JSON-отчёта.");
            usage(err);
            return 2;
        }
        try {
            if (args[0].isBlank() || args[0].startsWith("--") || (args.length == 2 && args[1].isBlank())) {
                throw new IllegalArgumentException("Неверные аргументы командной строки");
            }
            Path input = Path.of(args[0]);
            Path output = args.length == 2 ? Path.of(args[1]) : defaultOutput(input);
            if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                    || (Files.exists(input) && Files.exists(output) && Files.isSameFile(input, output))) {
                throw new IllegalArgumentException("Путь отчёта не должен совпадать с входным JAR");
            }
            var report = new JarAnalyzer().analyze(input);
            ReportWriter.writeJson(report, output);
            ReportWriter.print(report, out);
            out.println("JSON-отчёт: " + output);
            return 0;
        } catch (InvalidPathException e) {
            err.println("Ошибка пути: " + e.getInput());
            return 2;
        } catch (IllegalArgumentException e) {
            err.println("Ошибка: " + e.getMessage());
            return 2;
        } catch (IOException | SecurityException e) {
            err.println("Ошибка анализа или записи отчёта: " + e.getMessage());
            return 1;
        }
    }

    private static Path defaultOutput(Path input) {
        Path name = input.getFileName();
        if (name == null) throw new IllegalArgumentException("Укажите путь к файлу JAR");
        String fileName = name.toString();
        if (fileName.endsWith(".jar")) fileName = fileName.substring(0, fileName.length() - 4);
        return input.resolveSibling(fileName + ".report.json");
    }

    private static void usage(PrintStream out) {
        out.println("Использование: bytecode <input.jar> [output.json]");
        out.println("Без output.json отчёт сохраняется рядом с JAR как <имя>.report.json.");
    }
}
