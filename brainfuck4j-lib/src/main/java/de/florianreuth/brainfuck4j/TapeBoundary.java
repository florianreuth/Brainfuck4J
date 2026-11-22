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
 * Behavior of a {@link Memory} when a program accesses a cell outside of it.
 */
public enum TapeBoundary {

    /**
     * Throws an {@link IndexOutOfBoundsException}.
     */
    ERROR,
    /**
     * Wraps around to the other end of the memory.
     */
    WRAP,
    /**
     * Grows the memory with zeroed cells in the accessed direction.
     */
    GROW

}
