package org.example;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

class MainTest {
    @TempDir
    Path temp;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Test
    void writesParseableJsonAndConsoleReportWithExplicitPath() throws IOException {
        Path input = FixtureJar.write(temp.resolve("тест \"quotes\".jar"), Map.of(
                "Root.class", FixtureJar.type("Root", "java/lang/Object", ACC_PUBLIC).toByteArray()));
        Path output = temp.resolve("new/reports/output.json");
        assertEquals(0, run(input.toString(), output.toString()));
        var report = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        assertEquals(input.getFileName().toString(), report.get("inputJar").getAsString());
        assertEquals(1, report.getAsJsonObject("project").get("classCount").getAsInt());
        assertEquals("Root", report.getAsJsonArray("classes").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("ABC проекта"));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void usesDefaultOutputAndProducesReproducibleLocaleIndependentJson() throws IOException {
        Path input = FixtureJar.write(temp.resolve("empty.jar"), Map.of());
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals(0, run(input.toString()));
            String first = Files.readString(temp.resolve("empty.report.json"));
            Locale.setDefault(Locale.US);
            assertEquals(0, run(input.toString()));
            assertEquals(first, Files.readString(temp.resolve("empty.report.json")));
            assertEquals(0.0, JsonParser.parseString(first).getAsJsonObject().getAsJsonObject("project")
                    .get("averageFields").getAsDouble());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void reportsHelpAndInvalidArguments() {
        assertEquals(0, run("--help"));
        assertEquals(2, run());
        assertEquals(2, run("a", "b", "c"));
        assertEquals(2, run("--unknown"));
        assertEquals(2, run(""));
        assertEquals(2, run("invalid\0path"));
        assertEquals(2, run("/"));
    }

    @Test
    void returnsFailureWithoutCreatingReportOnReadError() {
        Path output = temp.resolve("output.json");
        assertEquals(1, run(temp.resolve("missing.jar").toString(), output.toString()));
        assertFalse(Files.exists(output));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void protectsInputFromOverwriteIncludingHardLinks() throws IOException {
        Path input = FixtureJar.write(temp.resolve("input.jar"), Map.of());
        byte[] original = Files.readAllBytes(input);
        assertEquals(2, run(input.toString(), input.toString()));
        Path link = Files.createLink(temp.resolve("same.json"), input);
        assertEquals(2, run(input.toString(), link.toString()));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test
    void reportsWriteErrorsAndCleansTemporaryFiles() throws IOException {
        Path input = FixtureJar.write(temp.resolve("input.jar"), Map.of());
        Path directory = Files.createDirectory(temp.resolve("output.json"));
        Files.writeString(directory.resolve("keep.txt"), "keep");
        assertEquals(1, run(input.toString(), directory.toString()));
        assertEquals("keep", Files.readString(directory.resolve("keep.txt")));
        try (var files = Files.list(temp)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".bytecode-")));
        }
        Path blockedParent = Files.writeString(temp.resolve("file"), "not a directory");
        assertEquals(1, run(input.toString(), blockedParent.resolve("report.json").toString()));
    }

    @Test
    void preservesExistingReportWhenAnalysisFails() throws IOException {
        Path output = Files.writeString(temp.resolve("report.json"), "previous report");
        Path input = Files.writeString(temp.resolve("broken.jar"), "broken");
        assertEquals(1, run(input.toString(), output.toString()));
        assertEquals("previous report", Files.readString(output));
    }

    private int run(String... args) {
        return Main.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }
}
