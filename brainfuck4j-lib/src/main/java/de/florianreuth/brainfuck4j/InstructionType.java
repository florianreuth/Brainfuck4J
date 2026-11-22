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

/**
 * Operations of the optimized instruction stream. {@code offset} is relative to the current memory pointer, except for
 * {@link #LOOP_START}, {@link #LOOP_END} and {@link #SCAN} where it is a pointer movement applied before the operation.
 */
public enum InstructionType {

    /**
     * {@code cell[offset] += value}
     */
    ADD,
    /**
     * {@code cell[offset] = value}
     */
    SET,
    /**
     * {@code pointer += value}
     */
    MOVE,
    /**
     * Jumps to the matching {@link #LOOP_END} at index {@code value} if the current cell is zero.
     */
    LOOP_START,
    /**
     * Jumps back to the matching {@link #LOOP_START} at index {@code value} if the current cell is not zero.
     */
    LOOP_END,
    /**
     * Loads and clears {@code cell[offset]} as factor for the following {@link #MULTIPLY} instructions, skips to index {@code value} if zero.
     */
    LOAD,
    /**
     * {@code cell[offset] += value * factor} with the factor from the preceding {@link #LOAD}.
     */
    MULTIPLY,
    /**
     * Moves the pointer by {@code value} until the current cell is zero.
     */
    SCAN,
    /**
     * Writes {@code cell[offset]} to the output.
     */
    OUTPUT,
    /**
     * Reads one byte from the input into {@code cell[offset]}, see {@link EndOfInput} for the end of input.
     */
    INPUT

}
