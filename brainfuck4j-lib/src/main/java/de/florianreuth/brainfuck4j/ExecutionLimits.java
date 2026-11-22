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
 * Resource limits of a single {@link Program#execute(Memory, java.io.InputStream, java.io.OutputStream, ExecutionLimits)} call.
 *
 * @param maxIterations Maximum number of repeated loop iterations, exceeding it throws an {@link IllegalStateException}.
 * @param maxCells      Maximum number of cells a {@link TapeBoundary#GROW} memory may grow to, exceeding it throws an {@link IndexOutOfBoundsException}.
 */
public record ExecutionLimits(long maxIterations, int maxCells) {

    /**
     * No limits, the execution may run forever.
     */
    public static final ExecutionLimits NONE = new ExecutionLimits(Long.MAX_VALUE, Integer.MAX_VALUE);

    /**
     * @throws IllegalArgumentException If a limit is not positive.
     */
    public ExecutionLimits {
        if (maxIterations <= 0 || maxCells <= 0) {
            throw new IllegalArgumentException("Limits must be positive: " + maxIterations + " iterations, " + maxCells + " cells");
        }
    }

}
