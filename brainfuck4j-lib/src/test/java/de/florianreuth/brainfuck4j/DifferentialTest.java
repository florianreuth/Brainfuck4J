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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the optimized {@link Program} with the {@link ReferenceInterpreter} on random programs for all memory settings.
 */
public final class DifferentialTest {

    private static final int PROGRAMS_PER_SEED = 3000;
    private static final long REFERENCE_STEPS = 200_000;
    private static final String[] LOOP_SHAPES = {"[-]", "[+]", "[->+<]", "[->>+++<<]", "[-<<<+>>>]", "[>]", "[<<]"};

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5})
    public void matchesReference(final long seed) throws IOException {
        final Random random = new Random(seed);
        final List<String> failures = new ArrayList<>();
        int compared = 0;
        for (int i = 0; i < PROGRAMS_PER_SEED; i++) {
            final String code = randomProgram(random);
            final MemoryType type = MemoryType.values()[random.nextInt(MemoryType.values().length)];
            final TapeBoundary boundary = TapeBoundary.values()[random.nextInt(TapeBoundary.values().length)];
            final EndOfInput endOfInput = EndOfInput.values()[random.nextInt(EndOfInput.values().length)];
            final int size = 1 + random.nextInt(12);
            final byte[] input = new byte[random.nextInt(4)];
            random.nextBytes(input);

            final ReferenceInterpreter.Result expected = ReferenceInterpreter.run(code, type, size, boundary, endOfInput, input, REFERENCE_STEPS);
            if (expected == null) {
                continue;
            }

            final Program program = Brainfuck4J.compile(code);
            if (boundary == TapeBoundary.WRAP && size <= program.span()) {
                continue;
            }

            final String mismatch = compare(program, expected, new Memory(type, size, boundary, endOfInput), input);
            compared++;
            if (mismatch != null) {
                failures.add(type + " " + boundary + " " + endOfInput + " size=" + size + " input=" + HexFormat.of().formatHex(input) + " code=" + code + ": " + mismatch);
            }
        }

        assertTrue(compared > PROGRAMS_PER_SEED / 2, "Too few comparable programs: " + compared);
        assertTrue(failures.isEmpty(), failures.size() + " mismatches, first:\n" + String.join("\n", failures.subList(0, Math.min(5, failures.size()))));
    }

    private static String compare(final Program program, final ReferenceInterpreter.Result expected, final Memory memory, final byte[] input) throws IOException {
        // Output beyond what the reference produced means the optimized run diverged, so it is cut off early
        final int outputLimit = expected.output().length + 64;
        final ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override
            public void write(final byte[] bytes, final int offset, final int length) {
                final int allowed = Math.min(length, outputLimit - this.size());
                super.write(bytes, offset, Math.max(0, allowed));
                if (allowed < length) {
                    throw new IllegalStateException("Output limit exceeded");
                }
            }
        };

        boolean error = false;
        try {
            program.execute(memory, new ByteArrayInputStream(input), output, new ExecutionLimits(10_000_000, 1 << 20));
        } catch (final IndexOutOfBoundsException exception) {
            error = true;
        } catch (final IllegalStateException exception) {
            if (!expected.error()) {
                return exception.getMessage();
            }
        }

        final byte[] actual = output.toByteArray();
        if (expected.error()) {
            // ERROR mode only fails on performed accesses, optimized away no-op accesses may let the program continue
            final boolean prefix = actual.length >= expected.output().length && Arrays.equals(actual, 0, expected.output().length, expected.output(), 0, expected.output().length);
            return error || prefix ? null : "expected error after " + HexFormat.of().formatHex(expected.output()) + ", got " + HexFormat.of().formatHex(actual);
        }

        if (error) {
            return "unexpected error, expected " + HexFormat.of().formatHex(expected.output());
        }

        return Arrays.equals(actual, expected.output()) ? null : "expected " + HexFormat.of().formatHex(expected.output()) + ", got " + HexFormat.of().formatHex(actual);
    }

    private static String randomProgram(final Random random) {
        final String commands = "+++---<<<>>>..,[[]]";
        final StringBuilder code = new StringBuilder();
        final int length = 5 + random.nextInt(60);
        int depth = 0;
        for (int i = 0; i < length; i++) {
            char command = commands.charAt(random.nextInt(commands.length()));
            if (command == ']' && depth == 0) {
                command = '[';
            }

            if (command == '[') {
                depth++;
            } else if (command == ']') {
                depth--;
            }

            code.append(command);
            if (random.nextInt(8) == 0) {
                code.append(LOOP_SHAPES[random.nextInt(LOOP_SHAPES.length)]);
            }
        }

        return code + "]".repeat(depth);
    }

}
