package org.example.model;

public record AbcMetrics(long assignments, long branches, long conditions, double value) {

    public static AbcMetrics of(long assignments, long branches, long conditions) {
        return new AbcMetrics(assignments, branches, conditions,
                Math.hypot(Math.hypot(assignments, branches), conditions));
    }

    public AbcMetrics plus(AbcMetrics other) {
        return of(assignments + other.assignments, branches + other.branches, conditions + other.conditions);
    }
}
