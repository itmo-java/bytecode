package org.example.analyzer;

import org.example.model.AbcMetrics;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

record ClassInfo(String name, int access, String superName, List<String> interfaces,
                 int fieldCount, List<MethodInfo> methods, AbcMetrics abc) {
    boolean isInterface() {
        return (access & ACC_INTERFACE) != 0;
    }

    String packageName() {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? "" : name.substring(0, slash);
    }

    String kind() {
        if ((access & ACC_ENUM) != 0) return "enum";
        if ((access & ACC_RECORD) != 0) return "record";
        if ((access & ACC_ABSTRACT) != 0) return "abstract";
        return "class";
    }
}
