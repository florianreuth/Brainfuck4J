/*
 * This file is part of Brainfuck4J - https://github.com/florianreuth/Brainfuck4J
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.florianreuth.brainfuck4j.cli;

import de.florianreuth.brainfuck4j.Brainfuck4J;
import de.florianreuth.brainfuck4j.EndOfInput;
import de.florianreuth.brainfuck4j.Memory;
import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.TapeBoundary;
import de.florianreuth.brainfuck4j.dialect.DialectType;
import de.florianreuth.brainfuck4j.export.ExportLanguage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BrainfuckCli {

    public static void main(final String[] arguments) {
        if (arguments.length == 0) {
            printUsage();
            return;
        }

        final String[] subArguments = Arrays.copyOfRange(arguments, 1, arguments.length);
        try {
            final boolean success = switch (arguments[0]) {
                case "run" -> run(subArguments);
                case "convert" -> convert(subArguments);
                case "export" -> export(subArguments);
                case "list" -> list(subArguments);
                case "help", "-h", "--help" -> {
                    printUsage();
                    yield true;
                }
                default -> {
                    System.err.println("Unknown command: " + arguments[0]);
                    printUsage();
                    yield false;
                }
            };
            if (!success) {
                System.exit(1);
            }
        } catch (final Exception exception) {
            System.out.flush();
            System.err.println("Error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Brainfuck4J CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  run <file> [--dialect <name>] [--memory <bits>] [--size <n>] [--boundary error|wrap|grow] [--eof minus-one|zero|unchanged]");
        System.out.println("      Execute a Brainfuck program from file, using stdin/stdout.");
        System.out.println();
        System.out.println("  convert <input> <output> --from <dialect> --to <dialect>");
        System.out.println("      Convert a program between dialects.");
        System.out.println();
        System.out.println("  export <input> <output> [--language <name>] [--dialect <name>] [--memory <bits>] [--size <n>] [--eof minus-one|zero|unchanged]");
        System.out.println("      Export a program as standalone source file, the language defaults to the output extension.");
        System.out.println();
        System.out.println("  list dialects");
        System.out.println("      List available dialects.");
        System.out.println("  list memories");
        System.out.println("      List available memory types.");
        System.out.println("  list languages");
        System.out.println("      List available export languages.");
    }

    private static boolean run(final String[] arguments) throws Exception {
        if (arguments.length < 1) {
            System.err.println("Usage: run <file> [--dialect <name>] [--memory <bits>] [--size <n>] [--boundary error|wrap|grow] [--eof minus-one|zero|unchanged]");
            return false;
        }

        final Path file = Path.of(arguments[0]);
        if (!Files.exists(file)) {
            System.err.println("File does not exist: " + file);
            return false;
        }

        final Map<String, String> options = options(arguments, 1, "--dialect", "--memory", "--size", "--boundary", "--eof");
        final DialectType dialect = parseEnum(DialectType.class, options.getOrDefault("--dialect", "brainfuck"));
        final MemoryType memoryType = parseMemoryType(options.getOrDefault("--memory", "32"));
        final int memorySize = parseSize(options.getOrDefault("--size", "30000"));
        final TapeBoundary boundary = parseEnum(TapeBoundary.class, options.getOrDefault("--boundary", "error"));
        final EndOfInput endOfInput = parseEnum(EndOfInput.class, options.getOrDefault("--eof", "minus-one"));

        final Memory memory = new Memory(memoryType, memorySize, boundary, endOfInput);
        Brainfuck4J.compile(read(file, dialect)).execute(memory, System.in, System.out);
        return true;
    }

    private static boolean export(final String[] arguments) throws Exception {
        if (arguments.length < 2) {
            System.err.println("Usage: export <input> <output> [--language <name>] [--dialect <name>] [--memory <bits>] [--size <n>] [--eof minus-one|zero|unchanged]");
            return false;
        }

        final Path input = Path.of(arguments[0]);
        final Path output = Path.of(arguments[1]);
        if (!Files.exists(input)) {
            System.err.println("Input file does not exist: " + input);
            return false;
        }

        final Map<String, String> options = options(arguments, 2, "--language", "--dialect", "--memory", "--size", "--eof");
        final String fileName = output.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        final String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
        final ExportLanguage language = options.containsKey("--language")
            ? parseEnum(ExportLanguage.class, options.get("--language"))
            : dot == -1 ? null : ExportLanguage.byExtension(fileName.substring(dot + 1));
        final DialectType dialect = parseEnum(DialectType.class, options.getOrDefault("--dialect", "brainfuck"));
        final MemoryType memoryType = parseMemoryType(options.getOrDefault("--memory", "32"));
        final int memorySize = parseSize(options.getOrDefault("--size", "30000"));
        final EndOfInput endOfInput = parseEnum(EndOfInput.class, options.getOrDefault("--eof", "minus-one"));

        if (language == null) {
            System.err.println("Unknown output extension, specify --language.");
            return false;
        }

        Files.writeString(output, language.export(Brainfuck4J.compile(read(input, dialect)), memoryType, memorySize, endOfInput, baseName));
        System.out.println("Exported " + input + " -> " + output + " (" + language.name + ")");
        return true;
    }

    private static String read(final Path file, final DialectType dialect) throws Exception {
        final String source = readSource(file);
        return dialect == DialectType.BRAINFUCK ? source : dialect.convert(source, DialectType.BRAINFUCK);
    }

    private static String readSource(final Path file) throws Exception {
        // Comments may contain arbitrary bytes, the String constructor replaces malformed UTF-8 instead of throwing like Files#readString
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static Map<String, String> options(final String[] arguments, final int start, final String... allowed) {
        final List<String> names = List.of(allowed);
        final Map<String, String> options = new HashMap<>();
        for (int i = start; i < arguments.length; i++) {
            final String name = arguments[i];
            if (!names.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + name + ", expected one of " + String.join(", ", names));
            }

            if (i + 1 >= arguments.length) {
                throw new IllegalArgumentException("Missing value for option " + name);
            }

            if (options.put(name, arguments[++i]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + name);
            }
        }

        return options;
    }

    private static int parseSize(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid memory size: " + value);
        }
    }

    private static MemoryType parseMemoryType(final String value) {
        final String bits = value.toLowerCase().replace("bit_", "").replace("-bit", "");
        try {
            final MemoryType type = MemoryType.byBits(Integer.parseInt(bits));
            if (type != null) {
                return type;
            }
        } catch (final NumberFormatException ignored) {
        }

        throw new IllegalArgumentException("Unknown memory type: " + value + ", expected 8, 16 or 32");
    }

    private static <T extends Enum<T>> T parseEnum(final Class<T> type, final String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase().replace('-', '_'));
        } catch (final IllegalArgumentException exception) {
            final String expected = Arrays.stream(type.getEnumConstants()).map(constant -> constant.name().toLowerCase().replace('_', '-')).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Unknown value: " + value + ", expected one of " + expected);
        }
    }

    private static boolean convert(final String[] arguments) throws Exception {
        if (arguments.length < 4) {
            System.err.println("Usage: convert <input> <output> --from <dialect> --to <dialect>");
            return false;
        }

        final Path input = Path.of(arguments[0]);
        final Path output = Path.of(arguments[1]);

        final Map<String, String> options = options(arguments, 2, "--from", "--to");
        if (!options.containsKey("--from") || !options.containsKey("--to")) {
            System.err.println("Both --from and --to must be specified.");
            return false;
        }

        if (!Files.exists(input)) {
            System.err.println("Input file does not exist: " + input);
            return false;
        }

        final DialectType from = parseEnum(DialectType.class, options.get("--from"));
        final DialectType to = parseEnum(DialectType.class, options.get("--to"));
        Files.writeString(output, from.convert(readSource(input), to));
        System.out.println("Converted " + input + " -> " + output);
        return true;
    }

    private static boolean list(final String[] arguments) {
        if (arguments.length == 0) {
            System.err.println("Usage: list dialects|memories|languages");
            return false;
        }

        switch (arguments[0].toLowerCase()) {
            case "dialects" -> {
                for (final DialectType type : DialectType.values()) {
                    System.out.println(type.name() + " - " + type.name);
                }
            }
            case "memories" -> {
                for (final MemoryType type : MemoryType.values()) {
                    System.out.println(type.name() + " - " + type.name);
                }
            }
            case "languages" -> {
                for (final ExportLanguage language : ExportLanguage.values()) {
                    System.out.println(language.name() + " - " + language.name + " (." + language.extension + ")");
                }
            }
            default -> {
                System.err.println("Unknown list target: " + arguments[0]);
                return false;
            }
        }
        return true;
    }

}
