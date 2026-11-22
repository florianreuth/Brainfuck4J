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
 * Registry of supported cell widths, use {@link #create(int)} to get started.
 */
public enum MemoryType {

    BIT_8(8),
    BIT_16(16),
    BIT_32(32);

    public final String name;
    public final int bits;

    MemoryType(final int bits) {
        this.name = bits + "-bit";
        this.bits = bits;
    }

    public Memory create(final int size) {
        return new Memory(this, size);
    }

    /**
     * @param bits The cell width in bits.
     * @return The matching memory type, or null if none.
     */
    public static MemoryType byBits(final int bits) {
        for (final MemoryType type : values()) {
            if (type.bits == bits) {
                return type;
            }
        }

        return null;
    }

}
