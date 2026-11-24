package org.example.analyzer;

import org.example.model.AnalysisReport;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarAnalyzer {
    public AnalysisReport analyze(Path input) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IOException("JAR не найден или не является файлом: " + input);
        }
        Map<String, ClassInfo> classes = new TreeMap<>();
        try (JarFile jar = new JarFile(input.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String path = entry.getName();
                if (entry.isDirectory() || !path.endsWith(".class") || path.startsWith("META-INF/versions/")
                        || isMetadata(path)) continue;
                try (InputStream stream = jar.getInputStream(entry)) {
                    ClassMetricsVisitor visitor = new ClassMetricsVisitor();
                    ClassReader reader = new ClassReader(stream);
                    if (reader.readInt(0) != 0xCAFEBABE) {
                        throw new IOException("Неверный заголовок class-файла: " + path);
                    }
                    if (isMetadata(reader.getClassName())) continue;
                    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    ClassInfo info = visitor.result();
                    if (classes.putIfAbsent(info.name(), info) != null) {
                        throw new IOException("Повторное определение класса " + info.name() + " в " + path);
                    }
                } catch (RuntimeException e) {
                    throw new IOException("Не удалось прочитать класс " + path + ": " + e.getMessage(), e);
                }
            }
        }
        return new MetricsCalculator(classes).calculate(input.getFileName().toString());
    }

    private static boolean isMetadata(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.equals("module-info.class") || name.equals("package-info.class")
                || name.equals("module-info") || name.equals("package-info");
    }
}
