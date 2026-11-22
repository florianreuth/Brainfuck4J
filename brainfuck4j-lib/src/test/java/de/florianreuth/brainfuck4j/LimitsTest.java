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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class LimitsTest {

    private static final byte[] NO_INPUT = new byte[0];

    @Test
    public void iterationLimitIsExact() {
        // Three loop passes are two repeated iterations
        final Program program = Brainfuck4J.compile("+++[.-]");
        assertDoesNotThrow(() -> execute(program, MemoryType.BIT_8.create(10), new ExecutionLimits(2, 10)));
        assertThrows(IllegalStateException.class, () -> execute(program, MemoryType.BIT_8.create(10), new ExecutionLimits(1, 10)));
    }

    @Test
    public void endlessLoopHitsLimit() {
        assertThrows(IllegalStateException.class, () -> execute(Brainfuck4J.compile("+[]"), MemoryType.BIT_8.create(10), new ExecutionLimits(1_000_000, 10)));
    }

    @Test
    public void growthLimit() {
        final Memory memory = new Memory(MemoryType.BIT_8, 10, TapeBoundary.GROW, EndOfInput.MINUS_ONE);
        assertThrows(IndexOutOfBoundsException.class, () -> execute(Brainfuck4J.compile("+[>+]"), memory, new ExecutionLimits(Long.MAX_VALUE, 1000)));
        assertFalse(memory.size() > 1000);
    }

    @Test
    public void interruptStopsExecution() throws InterruptedException {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread = new Thread(() -> {
            try {
                execute(Brainfuck4J.compile("+[]"), MemoryType.BIT_8.create(10), ExecutionLimits.NONE);
            } catch (final Throwable throwable) {
                failure.set(throwable);
            }
        });
        thread.start();
        Thread.sleep(100);
        thread.interrupt();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertInstanceOf(InterruptedIOException.class, failure.get());
    }

    @Test
    public void invalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLimits(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLimits(10, 0));
    }

    private static void execute(final Program program, final Memory memory, final ExecutionLimits limits) throws Exception {
        program.execute(memory, new ByteArrayInputStream(NO_INPUT), OutputStream.nullOutputStream(), limits);
    }

}
