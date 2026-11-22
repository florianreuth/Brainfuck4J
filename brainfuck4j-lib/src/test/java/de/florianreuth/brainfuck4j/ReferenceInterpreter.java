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

package de.florianreuth.brainfuck4j;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Deliberately naive Brainfuck interpreter without any optimization, used as oracle for the optimized {@link Program}.
 */
public final class ReferenceInterpreter {

    private ReferenceInterpreter() {
    }

    /**
     * @param code         Pure Brainfuck code with balanced loops.
     * @param maximumSteps The maximum number of executed commands.
     * @return The result, or null if the step limit was reached.
     */
    public static Result run(final String code, final MemoryType type, final int size, final TapeBoundary boundary, final EndOfInput endOfInput, final byte[] input, final long maximumSteps) {
        final int shift = 32 - type.bits;
        final int[] tape = new int[size];
        final Map<Long, Integer> outside = new HashMap<>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int[] match = matchLoops(code);
        long pointer = 0;
        long steps = 0;
        int read = 0;
        for (int programCounter = 0; programCounter < code.length(); programCounter++) {
            if (++steps > maximumSteps) {
                return null;
            }

            final char command = code.charAt(programCounter);
            if (command == '>' || command == '<') {
                pointer += command == '>' ? 1 : -1;
                continue;
            }

            final long index = boundary == TapeBoundary.WRAP ? Math.floorMod(pointer, (long) size) : pointer;
            final boolean inside = index >= 0 && index < size;
            if (!inside && boundary == TapeBoundary.ERROR) {
                return new Result(output.toByteArray(), true);
            }

            int value = inside ? tape[(int) index] : outside.getOrDefault(index, 0);
            switch (command) {
                case '+' -> value = (value + 1) << shift >> shift;
                case '-' -> value = (value - 1) << shift >> shift;
                case '.' -> output.write(value);
                case ',' -> {
                    if (read < input.length) {
                        value = input[read++] << shift >> shift;
                    } else {
                        value = switch (endOfInput) {
                            case MINUS_ONE -> -1;
                            case ZERO -> 0;
                            case UNCHANGED -> value;
                        };
                    }
                }
                case '[' -> {
                    if (value == 0) {
                        programCounter = match[programCounter];
                    }
                }
                case ']' -> {
                    if (value != 0) {
                        programCounter = match[programCounter];
                    }
                }
                default -> {
                }
            }

            if (inside) {
                tape[(int) index] = value;
            } else {
                outside.put(index, value);
            }
        }

        return new Result(output.toByteArray(), false);
    }

    private static int[] matchLoops(final String code) {
        final int[] match = new int[code.length()];
        final Deque<Integer> starts = new ArrayDeque<>();
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '[') {
                starts.push(i);
            } else if (code.charAt(i) == ']') {
                final int start = starts.pop();
                match[start] = i;
                match[i] = start;
            }
        }

        return match;
    }

    /**
     * @param output The output written until the program ended or failed.
     * @param error  Whether the program accessed a cell outside an {@link TapeBoundary#ERROR} memory.
     */
    public record Result(byte[] output, boolean error) {
    }

}
