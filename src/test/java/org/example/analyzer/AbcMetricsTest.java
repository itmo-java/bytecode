package org.example.analyzer;

import org.example.FixtureJar;
import org.example.model.AbcMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class AbcMetricsTest {
    @TempDir
    Path temp;

    @Test
    void countsEveryRequiredOpcodeIncludingInitializersAndExcludesIincGotoAndFieldWrites() throws IOException {
        var writer = FixtureJar.type("Instructions", "java/lang/Object", ACC_PUBLIC);
        writer.visitField(ACC_STATIC, "field", "I", null, null).visitEnd();
        var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        var initializer = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        initializer.visitInsn(POP2);
        initializer.visitInsn(RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();

        var method = writer.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "measure", "()V", null, null);
        method.visitCode();
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, 0);
        method.visitInsn(LCONST_0);
        method.visitVarInsn(LSTORE, 1);
        method.visitInsn(FCONST_0);
        method.visitVarInsn(FSTORE, 3);
        method.visitInsn(DCONST_0);
        method.visitVarInsn(DSTORE, 4);
        method.visitInsn(ACONST_NULL);
        method.visitVarInsn(ASTORE, 6);
        method.visitIincInsn(0, 1);
        method.visitInsn(ICONST_0);
        method.visitFieldInsn(PUTSTATIC, "Instructions", "field", "I");
        method.visitInsn(ICONST_1);
        method.visitIntInsn(NEWARRAY, T_INT);
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitInsn(ICONST_0);
        method.visitInsn(IASTORE);
        method.visitInsn(POP);

        for (int opcode = IFEQ; opcode <= IF_ACMPNE; opcode++) {
            int push = opcode >= IF_ACMPEQ ? ACONST_NULL : ICONST_0;
            method.visitInsn(push);
            if (opcode >= IF_ICMPEQ) method.visitInsn(push);
            Label next = new Label();
            method.visitJumpInsn(opcode, next);
            method.visitLabel(next);
        }
        for (int opcode : new int[]{IFNULL, IFNONNULL}) {
            method.visitInsn(ACONST_NULL);
            Label next = new Label();
            method.visitJumpInsn(opcode, next);
            method.visitLabel(next);
        }
        Label jump = new Label();
        method.visitJumpInsn(GOTO, jump);
        method.visitLabel(jump);
        Label tableEnd = new Label();
        method.visitInsn(ICONST_0);
        method.visitTableSwitchInsn(0, 2, tableEnd, tableEnd, tableEnd, tableEnd);
        method.visitLabel(tableEnd);
        Label lookupEnd = new Label();
        method.visitInsn(ICONST_0);
        method.visitLookupSwitchInsn(lookupEnd, new int[]{1, 50, 500}, new Label[]{lookupEnd, lookupEnd, lookupEnd});
        method.visitLabel(lookupEnd);

        method.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
        method.visitInsn(POP2);
        method.visitInsn(ACONST_NULL);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        method.visitInsn(POP);
        method.visitInsn(ACONST_NULL);
        method.visitMethodInsn(INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
        method.visitTypeInsn(NEW, "java/lang/Object");
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(POP);
        Handle bootstrap = new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        method.visitInvokeDynamicInsn("makeConcatWithConstants", "()Ljava/lang/String;", bootstrap, "hello");
        method.visitInsn(POP);
        method.visitInsn(RETURN);
        method.visitMaxs(4, 7);
        method.visitEnd();

        var jar = FixtureJar.write(temp.resolve("instructions.jar"), Map.of("Instructions.class", writer.toByteArray()));
        var report = new JarAnalyzer().analyze(jar);
        var abc = report.classes().getFirst().abc();
        assertEquals(5, abc.assignments());
        assertEquals(7, abc.branches());
        assertEquals(18, abc.conditions());
        assertEquals(Math.sqrt(398), abc.value(), 1e-12);
        assertEquals(abc, report.project().abc());
    }

    @Test
    void normalizesCompactAndWideStoresButStillExcludesWideIinc() throws IOException {
        var writer = FixtureJar.type("Wide", "java/lang/Object", ACC_PUBLIC);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "()V", null, null);
        method.visitCode();
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, 0);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, 300);
        method.visitIincInsn(300, 500);
        method.visitInsn(RETURN);
        method.visitMaxs(1, 301);
        method.visitEnd();
        var jar = FixtureJar.write(temp.resolve("wide.jar"), Map.of("Wide.class", writer.toByteArray()));
        assertEquals(AbcMetrics.of(2, 0, 0), new JarAnalyzer().analyze(jar).project().abc());
    }

    @Test
    void aggregatesComponentsBeforeComputingMagnitude() throws IOException {
        var jar = FixtureJar.compile(temp, Map.of("Types.java", """
                class First { void store() { int number = 42; } }
                class Second { void call() { System.nanoTime(); } }
                """));
        var report = new JarAnalyzer().analyze(jar);
        assertEquals(AbcMetrics.of(1, 3, 0), report.project().abc());
        double sumOfMagnitudes = report.classes().stream().mapToDouble(type -> type.abc().value()).sum();
        assertNotEquals(sumOfMagnitudes, report.project().abc().value());
    }

    @Test
    void interfaceBytecodeContributesToProjectAbcButNotClassAverages() throws IOException {
        var jar = FixtureJar.compile(temp, Map.of("Only.java", """
                interface Only {
                    int FIELD = 1;
                    default void run() { int value = 1; System.nanoTime(); }
                    static void call() { System.nanoTime(); }
                    void abstractMethod();
                }
                """));
        var report = new JarAnalyzer().analyze(jar);
        assertEquals(0, report.project().classCount());
        assertEquals(1, report.project().interfaceCount());
        assertEquals(0.0, report.project().averageFields());
        assertEquals(0.0, report.project().averageOverriddenMethods());
        assertEquals(AbcMetrics.of(1, 2, 0), report.project().abc());
        assertEquals(report.project().abc(), report.interfaces().getFirst().abc());
    }
}
