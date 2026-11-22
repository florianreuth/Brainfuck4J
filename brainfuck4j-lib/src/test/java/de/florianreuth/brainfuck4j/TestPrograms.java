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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Shared fixtures and helpers of the test suite.
 */
public final class TestPrograms {

    /**
     * SHA-256 of the output of programs/mandelbrot.bf, recorded with the original 2.x interpreter.
     */
    public static final String MANDELBROT_SHA256 = "83a0aac65090b3b5e85c22337afac39d8ac17bfd88675f044b33bd55ca0c351b";

    private TestPrograms() {
    }

    /**
     * @param name The resource path without leading slash.
     * @return The resource content decoded as UTF-8.
     */
    public static String source(final String name) throws IOException {
        try (final InputStream stream = TestPrograms.class.getResourceAsStream("/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static byte[] run(final Program program, final Memory memory, final byte[] input) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        program.execute(memory, new ByteArrayInputStream(input), out);
        return out.toByteArray();
    }

    public static byte[] run(final String code, final MemoryType type, final byte[] input) throws IOException {
        return run(Brainfuck4J.compile(code), type.create(30000), input);
    }

    public static String sha256(final byte[] data) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

}
