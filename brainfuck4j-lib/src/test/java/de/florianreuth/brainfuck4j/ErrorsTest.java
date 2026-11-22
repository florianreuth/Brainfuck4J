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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ErrorsTest {

    private static final byte[] NO_INPUT = new byte[0];

    @Test
    public void unbalancedLoops() {
        assertEquals("Unclosed '[' at index 2", assertThrows(IllegalArgumentException.class, () -> Brainfuck4J.compile("+ [")).getMessage());
        assertEquals("Unmatched ']' at index 1", assertThrows(IllegalArgumentException.class, () -> Brainfuck4J.compile("+]]")).getMessage());
    }

    @Test
    public void memoryBounds() {
        final Memory memory = MemoryType.BIT_8.create(10);
        assertEquals("Memory underflow at cell -1", assertThrows(IndexOutOfBoundsException.class, () -> TestPrograms.run(Brainfuck4J.compile("<+"), memory, NO_INPUT)).getMessage());
        assertEquals("Memory overflow at cell 10", assertThrows(IndexOutOfBoundsException.class, () -> TestPrograms.run(Brainfuck4J.compile(">>>>>>>>>>+"), MemoryType.BIT_8.create(10), NO_INPUT)).getMessage());
        assertEquals("Memory underflow at cell -1", assertThrows(IndexOutOfBoundsException.class, () -> TestPrograms.run(Brainfuck4J.compile("+[<]"), MemoryType.BIT_8.create(10), NO_INPUT)).getMessage());
    }

    @Test
    public void invalidMemory() {
        assertThrows(IllegalArgumentException.class, () -> MemoryType.BIT_8.create(0));
    }

    @Test
    public void wrappingMemoryTooSmall() {
        final Program program = Brainfuck4J.compile("+>>>+");
        assertEquals(3, program.span());
        final Memory memory = new Memory(MemoryType.BIT_8, 3, TapeBoundary.WRAP, EndOfInput.MINUS_ONE);
        assertThrows(IllegalArgumentException.class, () -> TestPrograms.run(program, memory, NO_INPUT));
    }

    @Test
    public void invalidJumpTargets() {
        assertThrows(IllegalArgumentException.class, () -> new Program(List.of(new Instruction(InstructionType.LOOP_START, 5, 0))));
        assertThrows(IllegalArgumentException.class, () -> new Program(List.of(new Instruction(InstructionType.LOOP_END, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new Program(List.of(new Instruction(InstructionType.LOAD, 1, 0), new Instruction(InstructionType.ADD, 1, 0))));
    }

    @Test
    public void programRoundTripsThroughConstructor() {
        final Program program = Brainfuck4J.compile("++[>+++[>++<-]<-]>>.[-]");
        assertEquals(program.instructions(), new Program(program.instructions()).instructions());
    }

}
