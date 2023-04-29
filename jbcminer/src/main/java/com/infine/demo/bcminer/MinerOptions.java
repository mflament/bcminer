package com.infine.demo.bcminer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class MinerOptions {

    @Nullable
    public static IMiner parseCommandLine(String[] args, List<MinerOptions> minerOptions) {
        if (minerOptions.isEmpty())
            throw new IllegalArgumentException("no minerOptions");
        MinerOptions options;
        int start;
        if (args.length == 0) {
            options = minerOptions.get(0);
            start = 0;
        } else {
            String id = args[0];
            options = minerOptions.stream().filter(f -> f.id().equalsIgnoreCase(id)).findFirst().orElse(null);
            if (options == null) {
                System.err.printf("No miner with id %s, must be one of %s%n", id, minerOptions.stream()
                        .map(MinerOptions::id)
                        .collect(Collectors.joining(", ")));
                return null;
            }
            start = 1;
        }

        ParsedOptions parsedOptions;
        try {
            parsedOptions = options.parse(args, start);
        } catch (IllegalArgumentException e) {
            System.err.printf("Error parsing miner options : %s%n", e.getMessage());
            return null;
        }
        return options.createMiner(parsedOptions);
    }

    protected final String id;
    protected final Option<Boolean> help;
    private final Map<String, Option<?>> options = new LinkedHashMap<>();

    protected MinerOptions(String id) {
        this.id = id;
        help = addFlag("h", "Show help", false);
    }

    public String id() {
        return id;
    }

    public abstract IMiner createMiner(ParsedOptions options);

    public ParsedOptions parse(String[] args, int start) {
        Map<Option<?>, String> values = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                String opt = args[i].substring(1);
                final String value;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[i + 1];
                    i++;
                } else {
                    value = "";
                }
                Option<?> option = options.get(opt);
                if (option != null)
                    values.put(option, value);
            }
        }
        return new ParsedOptions(values);
    }

    protected Option<String> addString(String name, @Nullable String doc, String defaultValue) {
        Option<String> opt = new Option<>(name, doc, Function.identity(), defaultValue);
        options.put(opt.name(), opt);
        return opt;
    }

    protected Option<Integer> addInt(String name, @Nullable String doc, int defaultValue) {
        Option<Integer> opt = new Option<>(name, doc, Integer::parseInt, defaultValue);
        options.put(opt.name(), opt);
        return opt;
    }

    protected Option<Boolean> addFlag(String name, @Nullable String doc, boolean defaultValue) {
        Option<Boolean> opt = new Option<>(name, doc, MinerOptions::isTrue, defaultValue);
        options.put(opt.name(), opt);
        return opt;
    }

    private static boolean isTrue(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equals("1");
    }

    public record Option<T>(String name, String doc, Function<String, T> parser, T defaultValue) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Option<?> option = (Option<?>) o;
            return Objects.equals(name, option.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return String.format("%s : %s (default: %s)", name, doc, defaultValue);
        }
    }

    public static final class ParsedOptions {
        private final Map<Option<?>, String> arguments;

        private ParsedOptions(Map<Option<?>, String> arguments) {
            this.arguments = arguments;
        }

        public <T> T get(Option<T> option) {
            String value = arguments.get(option);
            if (value == null)
                return option.defaultValue();
            try {
                return option.parser().apply(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Error parsing option " + option, e);
            }
        }
    }
}
