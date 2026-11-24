package org.example;

import org.objectweb.asm.ClassWriter;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.objectweb.asm.Opcodes.*;

public final class FixtureJar {
    private FixtureJar() {
    }

    public static Path compile(Path directory, Map<String, String> sources) throws IOException {
        Path output = Files.createDirectories(directory.resolve("classes"));
        var arguments = new ArrayList<String>();
        arguments.addAll(java.util.List.of("--release", "21", "-d", output.toString()));
        for (var source : sources.entrySet()) {
            Path file = directory.resolve("sources").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests require a JDK");
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                entries.put(output.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return write(directory.resolve("fixture.jar"), entries);
    }

    public static Path write(Path path, Map<String, byte[]> entries) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return path;
    }

    public static ClassWriter type(String name, String parent, int access, String... interfaces) {
        var writer = new ClassWriter(0);
        writer.visit(V21, access, name, null, parent, interfaces);
        return writer;
    }
}
