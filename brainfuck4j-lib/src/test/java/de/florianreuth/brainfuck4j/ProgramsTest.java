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

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ProgramsTest {

    private static final byte[] NO_INPUT = new byte[0];

    @ParameterizedTest
    @EnumSource(MemoryType.class)
    public void helloWorld(final MemoryType type) throws Exception {
        assertEquals("Hello World!\n", new String(TestPrograms.run(TestPrograms.source("programs/hello.bf"), type, NO_INPUT), StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @EnumSource(MemoryType.class)
    public void cellWidth(final MemoryType type) throws Exception {
        final String output = new String(TestPrograms.run(TestPrograms.source("programs/width.bf"), type, NO_INPUT), StandardCharsets.US_ASCII);
        assertEquals(type.bits + " bit cells", output);
    }

    @Test
    public void mandelbrot() throws Exception {
        final byte[] output = TestPrograms.run(TestPrograms.source("programs/mandelbrot.bf"), MemoryType.BIT_8, NO_INPUT);
        assertEquals(TestPrograms.MANDELBROT_SHA256, TestPrograms.sha256(output));
    }

    @Test
    public void mandelbrotOnGrowingMemory() throws Exception {
        final Memory memory = new Memory(MemoryType.BIT_8, 1, TapeBoundary.GROW, EndOfInput.MINUS_ONE);
        final byte[] output = TestPrograms.run(Brainfuck4J.compile(TestPrograms.source("programs/mandelbrot.bf")), memory, NO_INPUT);
        assertEquals(TestPrograms.MANDELBROT_SHA256, TestPrograms.sha256(output));
    }

    @Test
    public void inputIsReadByteWise() throws Exception {
        final byte[] input = "Hi ä".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, TestPrograms.run(",.,.,.,.,.", MemoryType.BIT_32, input));
    }

    @ParameterizedTest
    @CsvSource({"MINUS_ONE, -1", "ZERO, 0", "UNCHANGED, 5"})
    public void endOfInput(final EndOfInput endOfInput, final byte expected) throws Exception {
        final Memory memory = new Memory(MemoryType.BIT_8, 10, TapeBoundary.ERROR, endOfInput);
        assertArrayEquals(new byte[]{expected}, TestPrograms.run(Brainfuck4J.compile("+++++,."), memory, NO_INPUT));
    }

    @Test
    public void memoryKeepsStateBetweenExecutions() throws Exception {
        final Program program = Brainfuck4J.compile("+.>");
        final Memory memory = MemoryType.BIT_8.create(10);
        assertArrayEquals(new byte[]{1}, TestPrograms.run(program, memory, NO_INPUT));
        assertArrayEquals(new byte[]{1}, TestPrograms.run(program, memory, NO_INPUT));
        assertEquals(2, memory.pointer());
    }

    @Test
    public void wrapsAround() throws Exception {
        final Memory memory = new Memory(MemoryType.BIT_8, 3, TapeBoundary.WRAP, EndOfInput.MINUS_ONE);
        assertArrayEquals(new byte[]{3, 3}, TestPrograms.run(Brainfuck4J.compile("<+++.>>>[.[-]]"), memory, NO_INPUT));
        assertEquals(2, memory.pointer());
    }

    @Test
    public void growsToTheLeft() throws Exception {
        final Memory memory = new Memory(MemoryType.BIT_8, 2, TapeBoundary.GROW, EndOfInput.MINUS_ONE);
        assertArrayEquals(new byte[]{3}, TestPrograms.run(Brainfuck4J.compile("<<+++."), memory, NO_INPUT));
        assertEquals(memory.origin() - 2, memory.pointer());
        assertEquals(3, memory.cells()[memory.origin() - 2]);
    }

    @Test
    public void optimizesKnownPatterns() {
        assertEquals(List.of(new Instruction(InstructionType.SET, 0, 0)), Brainfuck4J.compile("[-]").instructions());
        assertEquals(List.of(new Instruction(InstructionType.SET, 3, 0)), Brainfuck4J.compile("[-]+++").instructions());
        assertEquals(List.of(
            new Instruction(InstructionType.ADD, 1, 1),
            new Instruction(InstructionType.ADD, 2, 2),
            new Instruction(InstructionType.MOVE, 1, 0)
        ), Brainfuck4J.compile(">+>++<").instructions());
        assertEquals(List.of(
            new Instruction(InstructionType.LOAD, 2, 0),
            new Instruction(InstructionType.MULTIPLY, 1, 1),
            new Instruction(InstructionType.MULTIPLY, 3, 2)
        ), Brainfuck4J.compile("[->+>+++<<]").instructions());
        assertEquals(List.of(new Instruction(InstructionType.SCAN, -2, 1)), Brainfuck4J.compile(">[<<]").instructions());
        assertEquals(List.of(
            new Instruction(InstructionType.LOOP_START, 2, 1),
            new Instruction(InstructionType.OUTPUT, 0, 0),
            new Instruction(InstructionType.LOOP_END, 0, 1)
        ), Brainfuck4J.compile(">[.>]").instructions());
    }

    @Test
    public void keepsPendingChangeBeforeInput() throws Exception {
        final Memory memory = new Memory(MemoryType.BIT_8, 10, TapeBoundary.ERROR, EndOfInput.UNCHANGED);
        assertArrayEquals(new byte[]{-1}, TestPrograms.run(Brainfuck4J.compile("-,."), memory, NO_INPUT));
    }

}
