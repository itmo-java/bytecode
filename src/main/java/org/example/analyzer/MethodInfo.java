package org.example.analyzer;

import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

record MethodInfo(int access, String name, String descriptor, Set<String> bridgeTargets) {
    boolean isCandidate() {
        return !name.startsWith("<") && (access & (ACC_STATIC | ACC_PRIVATE | ACC_SYNTHETIC | ACC_BRIDGE)) == 0;
    }

    String key() {
        return name + descriptor;
    }

    // A covariant return type does not change a Java method's signature.
    String signature() {
        return name + descriptor.substring(0, descriptor.indexOf(')') + 1);
    }
}
