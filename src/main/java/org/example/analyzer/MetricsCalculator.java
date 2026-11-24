package org.example.analyzer;

import org.example.model.AbcMetrics;
import org.example.model.AnalysisReport;
import org.example.model.ClassMetrics;
import org.example.model.InterfaceMetrics;
import org.example.model.ProjectMetrics;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

final class MetricsCalculator {
    private final Map<String, ClassInfo> types;
    private final Map<String, Integer> depths = new HashMap<>();

    MetricsCalculator(Map<String, ClassInfo> types) {
        this.types = types;
    }

    AnalysisReport calculate(String inputJar) throws IOException {
        List<ClassMetrics> classes = new ArrayList<>();
        List<InterfaceMetrics> interfaces = new ArrayList<>();
        AbcMetrics totalAbc = AbcMetrics.of(0, 0, 0);
        long totalDepth = 0;
        long totalOverrides = 0;
        long totalFields = 0;
        int maximumDepth = 0;
        for (ClassInfo type : types.values()) {
            String name = type.name().replace('/', '.');
            totalAbc = totalAbc.plus(type.abc());
            if (type.isInterface()) {
                interfaces.add(new InterfaceMetrics(name, type.fieldCount(), type.abc()));
                continue;
            }
            int depth = depth(type);
            int overriddenMethods = overriddenMethods(type);
            classes.add(new ClassMetrics(name, type.kind(), depth, overriddenMethods, type.fieldCount(), type.abc()));
            maximumDepth = Math.max(maximumDepth, depth);
            totalDepth += depth;
            totalOverrides += overriddenMethods;
            totalFields += type.fieldCount();
        }
        int count = classes.size();
        ProjectMetrics project = new ProjectMetrics(count, interfaces.size(), maximumDepth,
                average(totalDepth, count), average(totalOverrides, count), average(totalFields, count), totalAbc);
        return new AnalysisReport(inputJar, project, classes, interfaces);
    }

    private static double average(long total, int count) {
        return count == 0 ? 0.0 : (double) total / count;
    }

    private int depth(ClassInfo start) throws IOException {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ClassInfo current = start;
        while (current != null && !current.isInterface() && !depths.containsKey(current.name())) {
            if (!visited.add(current.name())) {
                throw new IOException("Циклическое наследование: " + current.name());
            }
            path.add(current.name());
            current = current.superName() == null ? null : types.get(current.superName());
        }
        int depth = current == null || current.isInterface() ? -1 : depths.get(current.name());
        for (int i = path.size() - 1; i >= 0; i--) depths.put(path.get(i), ++depth);
        return depths.get(start.name());
    }

    private int overriddenMethods(ClassInfo type) {
        Set<String> inherited = new HashSet<>();
        for (ClassInfo ancestor : ancestors(type)) {
            for (MethodInfo method : ancestor.methods()) {
                if (!method.isCandidate() || (method.access() & ACC_FINAL) != 0) continue;
                boolean visible = (method.access() & (ACC_PUBLIC | ACC_PROTECTED)) != 0
                        || ancestor.packageName().equals(type.packageName());
                if (visible) inherited.add(method.signature());
            }
        }
        Set<String> bridgeImplementations = new HashSet<>();
        for (MethodInfo method : type.methods()) {
            if ((method.access() & ACC_BRIDGE) != 0 && inherited.contains(method.signature())) {
                // Count the declared implementation once, never the compiler-generated bridge itself.
                bridgeImplementations.addAll(method.bridgeTargets());
            }
        }
        int count = 0;
        for (MethodInfo method : type.methods()) {
            if (method.isCandidate() && (inherited.contains(method.signature())
                    || bridgeImplementations.contains(method.key()))) count++;
        }
        return count;
    }

    private List<ClassInfo> ancestors(ClassInfo type) {
        List<ClassInfo> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(type.name());
        ArrayDeque<String> queue = new ArrayDeque<>();
        addParents(type, queue);
        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            if (!visited.add(name)) continue;
            ClassInfo parent = types.get(name);
            if (parent == null) continue;
            result.add(parent);
            addParents(parent, queue);
        }
        return result;
    }

    private static void addParents(ClassInfo type, ArrayDeque<String> queue) {
        if (!type.isInterface() && type.superName() != null) queue.addLast(type.superName());
        queue.addAll(type.interfaces());
    }
}
