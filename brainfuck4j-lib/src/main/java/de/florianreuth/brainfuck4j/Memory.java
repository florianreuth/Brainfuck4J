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

    private final MemoryType type;
    private final int[] cells;
    private int pointer;

    /**
     * @param type The cell width.
     * @param size How many cells this memory can hold before it overflows.
     * @throws IllegalArgumentException If size is not positive.
     */
    public Memory(final MemoryType type, final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Memory size must be positive: " + size);
        }

        this.type = type;
        this.cells = new int[size];
    }

    public MemoryType type() {
        return type;
    }

    public int size() {
        return cells.length;
    }

    /**
     * @return The live backing array, values must stay sign-extended to {@link MemoryType#bits}.
     */
    public int[] cells() {
        return cells;
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

}
