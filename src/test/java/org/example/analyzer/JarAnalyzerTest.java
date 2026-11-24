package org.example.analyzer;

import org.example.FixtureJar;
import org.example.model.AnalysisReport;
import org.example.model.ClassMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class JarAnalyzerTest {
    @TempDir
    Path temp;
    private final JarAnalyzer analyzer = new JarAnalyzer();

    @Test
    void calculatesDepthFieldsAndOverridesAcrossAncestorsAndInterfaces() throws IOException {
        Path jar = FixtureJar.compile(temp, Map.of("p/Types.java", """
                package p;
                interface Root { void run(); }
                interface Left extends Root { }
                interface Right extends Root { }
                abstract class Base { int first; static long second; public abstract void work(); }
                abstract class Middle extends Base implements Left, Right { }
                class Leaf extends Middle {
                    private int own;
                    public void work() { }
                    public void run() { }
                    public void work(int value) { }
                    @Override public String toString() { return "leaf"; }
                }
                """));
        AnalysisReport report = analyzer.analyze(jar);
        assertEquals(3, report.project().classCount());
        assertEquals(3, report.project().interfaceCount());
        assertEquals(2, report.project().maximumInheritanceDepth());
        assertEquals(1.0, report.project().averageInheritanceDepth());
        assertEquals(2.0 / 3, report.project().averageOverriddenMethods());
        assertEquals(1.0, report.project().averageFields());
        assertEquals(0, type(report, "p.Base").inheritanceDepth());
        assertEquals(1, type(report, "p.Middle").inheritanceDepth());
        assertEquals(2, type(report, "p.Leaf").overriddenMethodCount());
        assertEquals(1, type(report, "p.Leaf").fieldCount());
    }

    @Test
    void distinguishesOverloadsStaticPrivateAndPackagePrivateMethods() throws IOException {
        Path jar = FixtureJar.compile(temp, Map.of(
                "p/Base.java", """
                        package p;
                        public class Base {
                            public void visible() { }
                            protected void protectedMethod() { }
                            void packageMethod() { }
                            private void secret() { }
                            public static void shared() { }
                            public void overloaded(int value) { }
                        }
                        """,
                "p/Same.java", """
                        package p;
                        class Same extends Base { void packageMethod() { } }
                        """,
                "q/Other.java", """
                        package q;
                        public class Other extends p.Base {
                            public void visible() { }
                            protected void protectedMethod() { }
                            public void packageMethod() { }
                            public void secret() { }
                            public static void shared() { }
                            public void overloaded(String value) { }
                        }
                        """));
        AnalysisReport report = analyzer.analyze(jar);
        assertEquals(1, type(report, "p.Same").overriddenMethodCount());
        assertEquals(2, type(report, "q.Other").overriddenMethodCount());
    }

    @Test
    void countsCovariantAndGenericImplementationsOnceWithoutCountingBridges() throws IOException {
        Path jar = FixtureJar.compile(temp, Map.of("Types.java", """
                interface Transform<T> { T apply(T value); }
                class Parent { public Object value() { return null; } }
                class Child extends Parent implements Transform<String> {
                    @Override public String value() { return "value"; }
                    @Override public String apply(String value) { return value; }
                    public String apply(Integer value) { return value.toString(); }
                }
                """));
        assertEquals(2, type(analyzer.analyze(jar), "Child").overriddenMethodCount());
    }

    @Test
    void excludesSyntheticAndBridgeMethodsFromOverrideCount() throws IOException {
        var parent = FixtureJar.type("Parent", "java/lang/Object", ACC_PUBLIC | ACC_ABSTRACT);
        var child = FixtureJar.type("Child", "Parent", ACC_PUBLIC | ACC_ABSTRACT);
        for (String name : new String[]{"plain", "synthetic", "bridge"}) {
            parent.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, name, "()V", null, null).visitEnd();
        }
        child.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "plain", "()V", null, null).visitEnd();
        child.visitMethod(ACC_PUBLIC | ACC_ABSTRACT | ACC_SYNTHETIC, "synthetic", "()V", null, null).visitEnd();
        child.visitMethod(ACC_PUBLIC | ACC_ABSTRACT | ACC_BRIDGE, "bridge", "()V", null, null).visitEnd();
        Path jar = FixtureJar.write(temp.resolve("types.jar"), Map.of(
                "Parent.class", parent.toByteArray(), "Child.class", child.toByteArray()));
        assertEquals(1, type(analyzer.analyze(jar), "Child").overriddenMethodCount());
    }

    @Test
    void includesAbstractEnumRecordAndCompilerGeneratedFields() throws IOException {
        Path jar = FixtureJar.compile(temp, Map.of("Types.java", """
                abstract class Abstract { private int value; }
                enum Choice { FIRST, SECOND }
                record Point(int x, int y) { }
                class Outer { class Inner { Outer owner() { return Outer.this; } } }
                interface Constants { int VALUE = 5; }
                """));
        AnalysisReport report = analyzer.analyze(jar);
        assertEquals(5, report.project().classCount());
        assertEquals("abstract", type(report, "Abstract").kind());
        assertEquals("enum", type(report, "Choice").kind());
        assertEquals("record", type(report, "Point").kind());
        assertEquals(3, type(report, "Choice").fieldCount()); // two constants and $VALUES
        assertEquals(2, type(report, "Point").fieldCount());
        assertEquals(1, type(report, "Outer$Inner").fieldCount()); // this$0
        assertEquals(7.0 / 5, report.project().averageFields());
    }

    @Test
    void readsClassesAtArbitraryArchivePathsAndIgnoresMetadataAndVersionedEntries() throws IOException {
        var root = FixtureJar.type("pkg/Root", "external/Missing", ACC_PUBLIC);
        Path jar = FixtureJar.write(temp.resolve("layout.jar"), Map.of(
                "project/module/target/classes/pkg/Root.class", root.toByteArray(),
                "module-info.class", new byte[]{0},
                "pkg/package-info.class", new byte[]{0},
                "META-INF/versions/25/pkg/Root.class", new byte[]{0},
                "nested.jar", new byte[]{0}));
        AnalysisReport report = analyzer.analyze(jar);
        assertEquals(1, report.project().classCount());
        assertEquals(0, type(report, "pkg.Root").inheritanceDepth());
    }

    @Test
    void doesNotLoadClassesOrExecuteInitializers() throws IOException {
        String property = "bytecode.test.executed";
        System.clearProperty(property);
        Path jar = FixtureJar.compile(temp, Map.of("Dangerous.java", """
                class Dangerous {
                    static { System.setProperty("bytecode.test.executed", "yes"); }
                    static Missing reference;
                }
                class Missing { }
                """));
        // The referenced dependency is deliberately unavailable in the analyzed JAR.
        byte[] bytes = Files.readAllBytes(temp.resolve("classes/Dangerous.class"));
        FixtureJar.write(jar, Map.of("Dangerous.class", bytes));
        assertEquals(1, analyzer.analyze(jar).project().classCount());
        assertNull(System.getProperty(property));
    }

    @Test
    void emptyJarHasFiniteZeroMetrics() throws IOException {
        var report = analyzer.analyze(FixtureJar.write(temp.resolve("empty.jar"), Map.of()));
        assertEquals(0, report.project().classCount());
        assertEquals(0.0, report.project().averageInheritanceDepth());
        assertEquals(0.0, report.project().averageFields());
        assertEquals(0.0, report.project().averageOverriddenMethods());
        assertEquals(0.0, report.project().abc().value());
    }

    @Test
    void rejectsMissingFilesDirectoriesAndNonZipFiles() throws IOException {
        assertThrows(IOException.class, () -> analyzer.analyze(temp.resolve("missing.jar")));
        assertThrows(IOException.class, () -> analyzer.analyze(temp));
        Path broken = Files.writeString(temp.resolve("not-a-jar.jar"), "not a zip");
        assertThrows(IOException.class, () -> analyzer.analyze(broken));
    }

    @Test
    void reportsBrokenClassEntry() throws IOException {
        Path jar = FixtureJar.write(temp.resolve("broken.jar"), Map.of("broken/Bad.class", new byte[]{1, 2, 3}));
        IOException error = assertThrows(IOException.class, () -> analyzer.analyze(jar));
        assertTrue(error.getMessage().contains("broken/Bad.class"));
    }

    @Test
    void rejectsWrongMagicNumberAndUnsupportedClassVersion() throws IOException {
        byte[] bytes = FixtureJar.type("Type", "java/lang/Object", ACC_PUBLIC).toByteArray();
        bytes[0] = 0;
        Path jar = FixtureJar.write(temp.resolve("magic.jar"), Map.of("Type.class", bytes));
        assertThrows(IOException.class, () -> analyzer.analyze(jar));
        bytes[0] = (byte) 0xCA;
        bytes[6] = 0x7F;
        FixtureJar.write(jar, Map.of("Type.class", bytes));
        assertThrows(IOException.class, () -> analyzer.analyze(jar));
    }

    @Test
    void rejectsAmbiguousDuplicateClassNames() throws IOException {
        byte[] bytes = FixtureJar.type("Type", "java/lang/Object", ACC_PUBLIC).toByteArray();
        Path jar = FixtureJar.write(temp.resolve("duplicates.jar"), Map.of("a/Type.class", bytes, "b/Type.class", bytes));
        assertTrue(assertThrows(IOException.class, () -> analyzer.analyze(jar)).getMessage().contains("Повторное"));
    }

    @Test
    void rejectsCyclicSuperclassHierarchy() throws IOException {
        Path jar = FixtureJar.write(temp.resolve("cycle.jar"), Map.of(
                "A.class", FixtureJar.type("A", "B", ACC_PUBLIC).toByteArray(),
                "B.class", FixtureJar.type("B", "A", ACC_PUBLIC).toByteArray()));
        assertTrue(assertThrows(IOException.class, () -> analyzer.analyze(jar)).getMessage().contains("Циклическое"));
    }

    @Test
    void handlesDeepHierarchyWithoutRecursiveStackOverflow() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 1_000; i++) {
            String name = String.format("C%04d", i);
            String parent = i == 999 ? "external/Root" : String.format("C%04d", i + 1);
            entries.put(name + ".class", FixtureJar.type(name, parent, ACC_PUBLIC).toByteArray());
        }
        var report = analyzer.analyze(FixtureJar.write(temp.resolve("deep.jar"), entries));
        assertEquals(999, report.project().maximumInheritanceDepth());
        assertEquals(499.5, report.project().averageInheritanceDepth());
    }

    @Test
    void matchesKnownMetricsOfProvidedKotlinSample() throws IOException {
        var report = analyzer.analyze(Path.of("src/main/resources/sample.jar"));
        assertEquals(8, report.project().classCount());
        assertEquals(2, report.project().interfaceCount());
        assertEquals(1, report.project().maximumInheritanceDepth());
        assertEquals(5.0 / 8, report.project().averageInheritanceDepth());
        assertEquals(6.0 / 8, report.project().averageOverriddenMethods());
        assertEquals(7.0 / 8, report.project().averageFields());
    }

    private static ClassMetrics type(AnalysisReport report, String name) {
        return report.classes().stream().filter(type -> type.name().equals(name)).findFirst().orElseThrow();
    }
}
