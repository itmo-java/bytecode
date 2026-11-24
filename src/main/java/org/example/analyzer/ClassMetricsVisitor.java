package org.example.analyzer;

import org.example.model.AbcMetrics;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

final class ClassMetricsVisitor extends ClassVisitor {
    private String name;
    private int access;
    private String superName;
    private List<String> interfaces;
    private int fields;
    private final List<MethodInfo> methods = new ArrayList<>();
    private long assignments;
    private long branches;
    private long conditions;

    ClassMetricsVisitor() {
        super(ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = name;
        this.access = access;
        this.superName = superName;
        this.interfaces = List.of(interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        fields++;
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        Set<String> bridgeTargets = new HashSet<>();
        methods.add(new MethodInfo(access, name, descriptor, bridgeTargets));
        return new MethodVisitor(ASM9) {
            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                if (opcode >= ISTORE && opcode <= ASTORE) assignments++;
            }

            // IINC is deliberately excluded, as explicitly required by the assignment.
            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName, String desc, boolean isInterface) {
                branches++;
                if ((access & ACC_BRIDGE) != 0 && owner.equals(ClassMetricsVisitor.this.name)
                        && methodName.equals(name)) {
                    bridgeTargets.add(methodName + desc);
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                branches++;
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                if ((opcode >= IFEQ && opcode <= IF_ACMPNE) || opcode == IFNULL || opcode == IFNONNULL) {
                    conditions++;
                }
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label defaultLabel, Label... labels) {
                conditions++;
            }

            @Override
            public void visitLookupSwitchInsn(Label defaultLabel, int[] keys, Label[] labels) {
                conditions++;
            }
        };
    }

    ClassInfo result() {
        return new ClassInfo(name, access, superName, interfaces, fields, List.copyOf(methods),
                AbcMetrics.of(assignments, branches, conditions));
    }
}
