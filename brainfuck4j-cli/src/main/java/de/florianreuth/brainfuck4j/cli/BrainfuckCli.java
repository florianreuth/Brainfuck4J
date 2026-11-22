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
import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.dialect.DialectType;
import de.florianreuth.brainfuck4j.export.ExportLanguage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BrainfuckCli {

    public static void main(final String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        try {
            final boolean success = switch (args[0]) {
                case "run" -> run(subArgs);
                case "convert" -> convert(subArgs);
                case "export" -> export(subArgs);
                case "list" -> list(subArgs);
                case "help", "-h", "--help" -> {
                    printUsage();
                    yield true;
                }
                default -> {
                    System.err.println("Unknown command: " + args[0]);
                    printUsage();
                    yield false;
                }
            };
            if (!success) {
                System.exit(1);
            }
        } catch (final Exception e) {
            System.out.flush();
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Brainfuck4J CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  run <file> [--dialect <name>] [--memory <bits>] [--size <n>]");
        System.out.println("      Execute a Brainfuck program from file, using stdin/stdout.");
        System.out.println();
        System.out.println("  convert <input> <output> --from <dialect> --to <dialect>");
        System.out.println("      Convert a program between dialects.");
        System.out.println();
        System.out.println("  export <input> <output> [--language <name>] [--dialect <name>] [--memory <bits>] [--size <n>]");
        System.out.println("      Export a program as standalone source file, the language defaults to the output extension.");
        System.out.println();
        System.out.println("  list dialects");
        System.out.println("      List available dialects.");
        System.out.println("  list memories");
        System.out.println("      List available memory types.");
        System.out.println("  list languages");
        System.out.println("      List available export languages.");
    }

    private static boolean run(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: run <file> [--dialect <name>] [--memory <bits>] [--size <n>]");
            return false;
        }

        final Path file = Path.of(args[0]);
        if (!Files.exists(file)) {
            System.err.println("File does not exist: " + file);
            return false;
        }

        DialectType dialect = DialectType.BRAINFUCK;
        MemoryType memoryType = MemoryType.BIT_32;
        int memorySize = 30000;

        for (int i = 1; i < args.length; i++) {
            final String opt = args[i];
            if ("--dialect".equals(opt) && i + 1 < args.length) {
                dialect = DialectType.valueOf(args[++i].toUpperCase());
            } else if ("--memory".equals(opt) && i + 1 < args.length) {
                memoryType = parseMemoryType(args[++i]);
            } else if ("--size".equals(opt) && i + 1 < args.length) {
                memorySize = Integer.parseInt(args[++i]);
            }
        }

        Brainfuck4J.compile(read(file, dialect)).execute(memoryType.create(memorySize), System.in, System.out);
        return true;
    }

    private static boolean export(final String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: export <input> <output> [--language <name>] [--dialect <name>] [--memory <bits>] [--size <n>]");
            return false;
        }

        final Path input = Path.of(args[0]);
        final Path output = Path.of(args[1]);
        if (!Files.exists(input)) {
            System.err.println("Input file does not exist: " + input);
            return false;
        }

        final String fileName = output.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        final String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
        ExportLanguage language = dot == -1 ? null : ExportLanguage.byExtension(fileName.substring(dot + 1));
        DialectType dialect = DialectType.BRAINFUCK;
        MemoryType memoryType = MemoryType.BIT_32;
        int memorySize = 30000;

        for (int i = 2; i < args.length; i++) {
            final String opt = args[i];
            if ("--language".equals(opt) && i + 1 < args.length) {
                language = ExportLanguage.valueOf(args[++i].toUpperCase());
            } else if ("--dialect".equals(opt) && i + 1 < args.length) {
                dialect = DialectType.valueOf(args[++i].toUpperCase());
            } else if ("--memory".equals(opt) && i + 1 < args.length) {
                memoryType = parseMemoryType(args[++i]);
            } else if ("--size".equals(opt) && i + 1 < args.length) {
                memorySize = Integer.parseInt(args[++i]);
            }
        }

        if (language == null) {
            System.err.println("Unknown output extension, specify --language.");
            return false;
        }

        Files.writeString(output, language.export(Brainfuck4J.compile(read(input, dialect)), memoryType, memorySize, baseName));
        System.out.println("Exported " + input + " -> " + output + " (" + language.name + ")");
        return true;
    }

    private static String read(final Path file, final DialectType dialect) throws Exception {
        final String source = Files.readString(file);
        return dialect == DialectType.BRAINFUCK ? source : dialect.convert(source, DialectType.BRAINFUCK);
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

    private static boolean convert(final String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: convert <input> <output> --from <dialect> --to <dialect>");
            return false;
        }

        final Path input = Path.of(args[0]);
        final Path output = Path.of(args[1]);

        DialectType from = null;
        DialectType to = null;

        for (int i = 2; i < args.length; i++) {
            final String opt = args[i];
            if ("--from".equals(opt) && i + 1 < args.length) {
                from = DialectType.valueOf(args[++i].toUpperCase());
            } else if ("--to".equals(opt) && i + 1 < args.length) {
                to = DialectType.valueOf(args[++i].toUpperCase());
            }
        }

        if (from == null || to == null) {
            System.err.println("Both --from and --to must be specified.");
            return false;
        }

        if (!Files.exists(input)) {
            System.err.println("Input file does not exist: " + input);
            return false;
        }

        Files.writeString(output, from.convert(Files.readString(input), to));
        System.out.println("Converted " + input + " -> " + output);
        return true;
    }

    private static boolean list(final String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: list dialects|memories");
            return false;
        }

        switch (args[0].toLowerCase()) {
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
                System.err.println("Unknown list target: " + args[0]);
                return false;
            }
        }
        return true;
    }

}
