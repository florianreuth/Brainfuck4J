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
 * The memory tape a {@link Program} executes on, keeps its state between executions.
 * <p>
 * Cells are stored as sign-extended ints and wrap around according to {@link MemoryType#bits}.
 */
public final class Memory {

    private static final int MAX_SIZE = Integer.MAX_VALUE - 8;

    private final MemoryType type;
    private final TapeBoundary boundary;
    private final EndOfInput endOfInput;
    private int[] cells;
    private int origin;
    private int pointer;

    /**
     * Creates a memory which throws on out of bounds access and reads end of input as -1.
     *
     * @param type The cell width.
     * @param size The initial number of cells.
     * @throws IllegalArgumentException If size is not positive.
     */
    public Memory(final MemoryType type, final int size) {
        this(type, size, TapeBoundary.ERROR, EndOfInput.MINUS_ONE);
    }

    /**
     * @param type       The cell width.
     * @param size       The initial number of cells.
     * @param boundary   The behavior on out of bounds access.
     * @param endOfInput The value stored by input instructions once the input has ended.
     * @throws IllegalArgumentException If size is not positive.
     */
    public Memory(final MemoryType type, final int size, final TapeBoundary boundary, final EndOfInput endOfInput) {
        if (size <= 0) {
            throw new IllegalArgumentException("Memory size must be positive: " + size);
        }

        this.type = type;
        this.boundary = boundary;
        this.endOfInput = endOfInput;
        this.cells = new int[size];
    }

    public MemoryType type() {
        return type;
    }

    public TapeBoundary boundary() {
        return boundary;
    }

    public EndOfInput endOfInput() {
        return endOfInput;
    }

    public int size() {
        return cells.length;
    }

    /**
     * @return The live backing array, replaced when the memory grows, values must stay sign-extended to {@link MemoryType#bits}.
     */
    public int[] cells() {
        return cells;
    }

    /**
     * @return The index of the initial first cell within {@link #cells()}, increases when the memory grows to the left.
     */
    public int origin() {
        return origin;
    }

    public int pointer() {
        return pointer;
    }

    /**
     * @param pointer The new pointer position, not bounds checked until the next cell access.
     */
    public void pointer(final int pointer) {
        this.pointer = pointer;
    }

    /**
     * Grows the memory with zeroed cells so the given index becomes valid, independent of {@link #boundary()}.
     *
     * @param index An index relative to the current cells, may be negative.
     * @return The amount all existing indices including the pointer were shifted by, positive when grown to the left.
     * @throws IndexOutOfBoundsException If the required size exceeds the maximum array size.
     */
    public int grow(final int index) {
        return grow(index, Integer.MAX_VALUE);
    }

    /**
     * Grows the memory with zeroed cells so the given index becomes valid, independent of {@link #boundary()}.
     *
     * @param index   An index relative to the current cells, may be negative.
     * @param maxSize The maximum number of cells after growing.
     * @return The amount all existing indices including the pointer were shifted by, positive when grown to the left.
     * @throws IndexOutOfBoundsException If the required size exceeds maxSize or the maximum array size.
     */
    public int grow(final int index, final int maxSize) {
        if (index >= 0 && index < cells.length) {
            return 0;
        }

        final long limit = Math.min(maxSize, MAX_SIZE);
        final long required = index < 0 ? cells.length - (long) index : index + 1L;
        if (required > limit) {
            throw new IndexOutOfBoundsException("Memory can not grow beyond " + limit + " cells to reach cell " + index);
        }

        final int size = (int) Math.min(limit, Math.max(2L * cells.length, required));
        final int[] grown = new int[size];
        final int shift = index < 0 ? size - cells.length : 0;
        System.arraycopy(cells, 0, grown, shift, cells.length);
        cells = grown;
        origin += shift;
        pointer += shift;
        return shift;
    }

}
