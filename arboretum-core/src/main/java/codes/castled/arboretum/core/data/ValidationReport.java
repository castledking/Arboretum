package codes.castled.arboretum.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects everything wrong with a set of data definitions.
 *
 * <p>Validation never stops at the first problem. An author fixing a species file wants the whole
 * list in one pass, and a server operator wants to know every broken species before the world tries
 * to generate with them. Severity separates "this file is unusable" from "this file loaded but
 * something was ignored".
 */
public final class ValidationReport {

    /** How serious a finding is. */
    public enum Severity {
        /** The definition cannot be used. Loading must fail or the entry must be skipped. */
        ERROR,
        /** The definition loaded, but something was ignored or defaulted. */
        WARNING
    }

    /** One finding, tied to the definition it came from so the message is actionable. */
    public record Problem(Severity severity, String source, String message) {
        public Problem {
            source = source == null || source.isBlank() ? "<unknown>" : source;
            message = message == null ? "" : message;
        }

        @Override
        public String toString() {
            return severity + " in " + source + ": " + message;
        }
    }

    private final String context;
    private final List<Problem> problems = new ArrayList<>();

    public ValidationReport(String context) {
        this.context = context;
    }

    public ValidationReport error(String message) {
        add(Severity.ERROR, message);
        return this;
    }

    public ValidationReport warn(String message) {
        add(Severity.WARNING, message);
        return this;
    }

    /** Adds a finding belonging to a nested element, e.g. {@code tree.trunk_template}. */
    public ValidationReport error(String field, String message) {
        add(Severity.ERROR, field + ": " + message);
        return this;
    }

    public ValidationReport warn(String field, String message) {
        add(Severity.WARNING, field + ": " + message);
        return this;
    }

    private void add(Severity severity, String message) {
        problems.add(new Problem(severity, context, message));
    }

    public List<Problem> problems() {
        return List.copyOf(problems);
    }

    public List<Problem> errors() {
        return problems.stream().filter(problem -> problem.severity() == Severity.ERROR).toList();
    }

    public List<Problem> warnings() {
        return problems.stream().filter(problem -> problem.severity() == Severity.WARNING).toList();
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(problem -> problem.severity() == Severity.ERROR);
    }

    public boolean isClean() {
        return problems.isEmpty();
    }

    /** Merges another report's problems in, keeping their original sources. */
    public ValidationReport merge(ValidationReport other) {
        problems.addAll(other.problems);
        return this;
    }

    /** A single-line summary suitable for a log line. */
    public String summary() {
        if (isClean()) {
            return context + ": no problems";
        }
        return context + ": " + errors().size() + " error(s), " + warnings().size() + " warning(s)";
    }

    /** A multi-line report for a console dump. */
    public String describe() {
        if (isClean()) {
            return summary();
        }
        StringBuilder builder = new StringBuilder(summary());
        for (Problem problem : problems) {
            builder.append(System.lineSeparator()).append("  ").append(problem);
        }
        return builder.toString();
    }

    /** Merges reports and describes them all, the usual shape of a load-time log entry. */
    public static String describeAll(String title, List<ValidationReport> reports) {
        int errors = reports.stream().mapToInt(report -> report.errors().size()).sum();
        int warnings = reports.stream().mapToInt(report -> report.warnings().size()).sum();
        StringBuilder builder = new StringBuilder(title)
                .append(": ").append(errors).append(" error(s), ")
                .append(warnings).append(" warning(s)");
        for (ValidationReport report : reports) {
            if (!report.isClean()) {
                builder.append(System.lineSeparator()).append(report.describe());
            }
        }
        return builder.toString();
    }
}