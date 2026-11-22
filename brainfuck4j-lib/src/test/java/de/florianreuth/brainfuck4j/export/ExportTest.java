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

package de.florianreuth.brainfuck4j.export;

import de.florianreuth.brainfuck4j.Brainfuck4J;
import de.florianreuth.brainfuck4j.EndOfInput;
import de.florianreuth.brainfuck4j.Memory;
import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.Program;
import de.florianreuth.brainfuck4j.TapeBoundary;
import de.florianreuth.brainfuck4j.TestPrograms;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compiles and runs exported programs and compares their output with the interpreter, C is only tested where gcc is available.
 */
public final class ExportTest {

    private static Boolean gccAvailable;

    @TempDir
    public Path directory;

    public static List<Case> cases() throws IOException {
        final List<Case> cases = new ArrayList<>();
        for (final MemoryType type : MemoryType.values()) {
            cases.add(new Case("Hello" + type.bits, TestPrograms.source("programs/hello.bf"), type, EndOfInput.MINUS_ONE, new byte[0]));
            cases.add(new Case("Width" + type.bits, TestPrograms.source("programs/width.bf"), type, EndOfInput.MINUS_ONE, new byte[0]));
        }

        for (final EndOfInput endOfInput : EndOfInput.values()) {
            cases.add(new Case("Eof" + endOfInput.ordinal(), "+++++,.,.", MemoryType.BIT_8, endOfInput, new byte[]{'x'}));
        }

        cases.add(new Case("Echo", ",.,.,.,.,.", MemoryType.BIT_32, EndOfInput.MINUS_ONE, "Hi ä".getBytes(StandardCharsets.UTF_8)));
        // Thousands of loops force the Java exporter to split the code into many methods
        cases.add(new Case("Split", "+[" + ">+[.-]<".repeat(3000) + "-]", MemoryType.BIT_8, EndOfInput.MINUS_ONE, new byte[0]));
        cases.add(new Case("Mandelbrot", TestPrograms.source("programs/mandelbrot.bf"), MemoryType.BIT_8, EndOfInput.MINUS_ONE, new byte[0]));
        return cases;
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void java(final Case testCase) throws Exception {
        final Program program = Brainfuck4J.compile(testCase.code());
        final Path source = this.directory.resolve(testCase.name() + ".java");
        Files.writeString(source, ExportLanguage.JAVA.export(program, testCase.type(), 30000, testCase.endOfInput(), testCase.name()));

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK");
        assertEquals(0, compiler.run(null, null, null, "-d", this.directory.toString(), source.toString()), "javac failed");

        final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        assertArrayEquals(expected(program, testCase), run(testCase.input(), java, "-cp", this.directory.toString(), testCase.name()));
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void c(final Case testCase) throws Exception {
        assumeTrue(gccAvailable(), "gcc is not available");
        final Program program = Brainfuck4J.compile(testCase.code());
        final Path source = this.directory.resolve(testCase.name() + ".c");
        final Path executable = this.directory.resolve(testCase.name() + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : ""));
        Files.writeString(source, ExportLanguage.C.export(program, testCase.type(), 30000, testCase.endOfInput(), testCase.name()));

        run(new byte[0], "gcc", "-std=c99", "-O2", "-Wall", "-o", executable.toString(), source.toString());
        assertArrayEquals(expected(program, testCase), run(testCase.input(), executable.toString()));
    }

    @Test
    public void invalidJavaClassName() {
        final Program program = Brainfuck4J.compile("+.");
        assertThrows(IllegalArgumentException.class, () -> ExportLanguage.JAVA.export(program, MemoryType.BIT_8, 10, EndOfInput.ZERO, "bad-name"));
        assertThrows(IllegalArgumentException.class, () -> ExportLanguage.JAVA.export(program, MemoryType.BIT_8, 10, EndOfInput.ZERO, "class"));
    }

    private static byte[] expected(final Program program, final Case testCase) throws IOException {
        return TestPrograms.run(program, new Memory(testCase.type(), 30000, TapeBoundary.ERROR, testCase.endOfInput()), testCase.input());
    }

    private static byte[] run(final byte[] input, final String... command) throws Exception {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (final OutputStream processInput = process.getOutputStream()) {
            processInput.write(input);
        }

        final byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Timed out: " + String.join(" ", command));
        }

        assertEquals(0, process.exitValue(), () -> String.join(" ", command) + " failed: " + new String(output, StandardCharsets.UTF_8));
        return output;
    }

    private static synchronized boolean gccAvailable() {
        if (gccAvailable == null) {
            try {
                final Process process = new ProcessBuilder("gcc", "--version").redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                gccAvailable = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (final IOException | InterruptedException exception) {
                gccAvailable = false;
            }
        }

        return gccAvailable;
    }

    /**
     * @param name The generated class and file name.
     */
    public record Case(String name, String code, MemoryType type, EndOfInput endOfInput, byte[] input) {

        @Override
        public String toString() {
            return name;
        }

    }

}
