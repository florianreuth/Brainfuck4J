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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;

/**
 * An immutable compiled program, usually created via {@link Brainfuck4J#compile(String)}. Can be executed multiple times
 * and from multiple threads as long as each execution uses its own {@link Memory}.
 */
public final class Program {

    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_SET = 1;
    private static final int OPERATION_MOVE = 2;
    private static final int OPERATION_LOOP_START = 3;
    private static final int OPERATION_LOOP_END = 4;
    private static final int OPERATION_LOAD = 5;
    private static final int OPERATION_MULTIPLY = 6;
    private static final int OPERATION_SCAN = 7;
    private static final int OPERATION_OUTPUT = 8;
    private static final int OPERATION_INPUT = 9;
    private static final int CHECK_INTERVAL = 1 << 16;

    private final List<Instruction> instructions;
    private final int[] operations;
    private final int[] values;
    private final int[] offsets;
    private final int span;

    /**
     * @param instructions The instruction stream, see {@link InstructionType} for the operand meanings.
     * @throws IllegalArgumentException If a jump target does not point to its matching instruction.
     */
    public Program(final List<Instruction> instructions) {
        this.instructions = List.copyOf(instructions);
        final int length = this.instructions.size();
        this.operations = new int[length];
        this.values = new int[length];
        this.offsets = new int[length];
        long minimumOffset = Long.MAX_VALUE;
        long maximumOffset = Long.MIN_VALUE;
        for (int i = 0; i < length; i++) {
            final Instruction instruction = this.instructions.get(i);
            this.validate(i, instruction);
            this.operations[i] = switch (instruction.type()) {
                case ADD -> OPERATION_ADD;
                case SET -> OPERATION_SET;
                case MOVE -> OPERATION_MOVE;
                case LOOP_START -> OPERATION_LOOP_START;
                case LOOP_END -> OPERATION_LOOP_END;
                case LOAD -> OPERATION_LOAD;
                case MULTIPLY -> OPERATION_MULTIPLY;
                case SCAN -> OPERATION_SCAN;
                case OUTPUT -> OPERATION_OUTPUT;
                case INPUT -> OPERATION_INPUT;
            };
            this.values[i] = instruction.value();
            this.offsets[i] = instruction.offset();
            if (instruction.type() != InstructionType.MOVE && instruction.type() != InstructionType.LOOP_START && instruction.type() != InstructionType.LOOP_END && instruction.type() != InstructionType.SCAN) {
                minimumOffset = Math.min(minimumOffset, instruction.offset());
                maximumOffset = Math.max(maximumOffset, instruction.offset());
            }
        }

        this.span = minimumOffset > maximumOffset ? 0 : (int) Math.min(Integer.MAX_VALUE, maximumOffset - minimumOffset);
    }

    private void validate(final int index, final Instruction instruction) {
        final int target = instruction.value();
        final boolean valid = switch (instruction.type()) {
            case LOOP_START -> target > index && target < this.instructions.size() && this.instructions.get(target).type() == InstructionType.LOOP_END && this.instructions.get(target).value() == index;
            case LOOP_END -> target >= 0 && target < index && this.instructions.get(target).type() == InstructionType.LOOP_START && this.instructions.get(target).value() == index;
            case LOAD -> target > index && target < this.instructions.size() && this.instructions.subList(index + 1, target + 1).stream().allMatch(following -> following.type() == InstructionType.MULTIPLY);
            default -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid jump target " + target + " of " + instruction.type() + " at index " + index);
        }
    }

    /**
     * @return The optimized instruction stream, immutable.
     */
    public List<Instruction> instructions() {
        return this.instructions;
    }

    /**
     * @return The distance between the lowest and highest cell offset accessed from one pointer position, a {@link TapeBoundary#WRAP} memory needs more cells than this.
     */
    public int span() {
        return this.span;
    }

    /**
     * Executes this program without limits, see {@link #execute(Memory, InputStream, OutputStream, ExecutionLimits)}.
     */
    public void execute(final Memory memory, final InputStream input, final OutputStream output) throws IOException {
        this.execute(memory, input, output, ExecutionLimits.NONE);
    }

    /**
     * Executes this program, blocking until it finishes, the memory keeps its state afterward.
     *
     * @param memory The memory to execute on, must not be shared with a concurrent execution.
     * @param input  Input stream, read byte-wise, see {@link Memory#endOfInput()} for the end of stream.
     * @param output Output stream, buffered and flushed on newline, before input and when finished.
     * @param limits The resource limits of this execution.
     * @throws IOException               If reading or writing fails.
     * @throws InterruptedIOException    If the executing thread is interrupted, checked every few thousand loop iterations.
     * @throws IndexOutOfBoundsException If a cell outside the memory is accessed with {@link TapeBoundary#ERROR}, or growing exceeds {@link ExecutionLimits#maximumCells()}.
     * @throws IllegalStateException     If {@link ExecutionLimits#maximumIterations()} is exceeded.
     * @throws IllegalArgumentException  If the memory wraps and is not larger than {@link #span()}.
     */
    public void execute(final Memory memory, final InputStream input, final OutputStream output, final ExecutionLimits limits) throws IOException {
        // Offset folding assumes distinct offsets are distinct cells, which a wrapping memory only guarantees above the span
        if (memory.boundary() == TapeBoundary.WRAP && memory.size() <= this.span) {
            throw new IllegalArgumentException("A wrapping memory needs more than " + this.span + " cells for this program, got " + memory.size());
        }

        // Local copies keep the arrays in registers during the interpreter loop
        final int[] operations = this.operations;
        final int[] values = this.values;
        final int[] offsets = this.offsets;
        final int length = operations.length;
        final int shift = 32 - memory.type().bits;
        final TapeBoundary boundary = memory.boundary();
        final EndOfInput endOfInput = memory.endOfInput();
        final byte[] buffer = new byte[8192];
        int[] cells = memory.cells();
        int buffered = 0;
        int pointer = memory.pointer();
        int programCounter = 0;
        int factor = 0;
        // Iterations are handed out in chunks so the hot path only decrements an int on loop back-edges, the budget ends on the first iteration over the limit
        final long budget = limits.maximumIterations() == Long.MAX_VALUE ? Long.MAX_VALUE : limits.maximumIterations() + 1;
        int tick = (int) Math.min(CHECK_INTERVAL, budget);
        long left = budget - tick;
        try {
            while (programCounter < length) {
                // Every instruction faults before its first side effect, so it can be retried or replayed after a boundary violation
                try {
                    for (; programCounter < length; programCounter++) {
                        switch (operations[programCounter]) {
                            case OPERATION_ADD -> {
                                final int cell = pointer + offsets[programCounter];
                                cells[cell] = (cells[cell] + values[programCounter]) << shift >> shift;
                            }
                            case OPERATION_SET -> cells[pointer + offsets[programCounter]] = values[programCounter] << shift >> shift;
                            case OPERATION_MOVE -> pointer += values[programCounter];
                            case OPERATION_LOOP_START -> {
                                final int next = pointer + offsets[programCounter];
                                if (cells[next] == 0) {
                                    programCounter = values[programCounter];
                                }

                                pointer = next;
                            }
                            case OPERATION_LOOP_END -> {
                                final int next = pointer + offsets[programCounter];
                                if (cells[next] != 0) {
                                    programCounter = values[programCounter];
                                    if (--tick == 0) {
                                        tick = checkpoint(left, limits);
                                        left -= tick;
                                    }
                                }

                                pointer = next;
                            }
                            case OPERATION_LOAD -> {
                                final int cell = pointer + offsets[programCounter];
                                factor = cells[cell];
                                if (factor == 0) {
                                    programCounter = values[programCounter];
                                } else {
                                    cells[cell] = 0;
                                }
                            }
                            case OPERATION_MULTIPLY -> {
                                final int cell = pointer + offsets[programCounter];
                                cells[cell] = (cells[cell] + factor * values[programCounter]) << shift >> shift;
                            }
                            case OPERATION_SCAN -> {
                                final int step = values[programCounter];
                                int next = pointer + offsets[programCounter];
                                while (cells[next] != 0) {
                                    next += step;
                                }

                                pointer = next;
                            }
                            case OPERATION_OUTPUT -> {
                                final byte value = (byte) cells[pointer + offsets[programCounter]];
                                buffer[buffered++] = value;
                                if (value == '\n' || buffered == buffer.length) {
                                    buffered = flush(output, buffer, buffered);
                                }
                            }
                            case OPERATION_INPUT -> {
                                final int cell = pointer + offsets[programCounter];
                                // Reading the cell first makes an out of bounds access fail before any input is consumed
                                final int current = cells[cell];
                                buffered = flush(output, buffer, buffered);
                                cells[cell] = read(input, current, endOfInput, shift);
                            }
                        }
                    }
                } catch (final ArrayIndexOutOfBoundsException exception) {
                    final int operation = operations[programCounter];
                    final int index = operation == OPERATION_SCAN ? scanEnd(cells, pointer + offsets[programCounter], values[programCounter]) : pointer + offsets[programCounter];
                    if (index >= 0 && index < cells.length) {
                        throw exception;
                    }

                    if (boundary == TapeBoundary.GROW) {
                        pointer += memory.grow(index, limits.maximumCells());
                        cells = memory.cells();
                        continue;
                    }

                    if (boundary == TapeBoundary.ERROR) {
                        throw new IndexOutOfBoundsException((index < 0 ? "Memory underflow" : "Memory overflow") + " at cell " + index);
                    }

                    final int cell = Math.floorMod(index, cells.length);
                    pointer = Math.floorMod(pointer, cells.length);
                    switch (operation) {
                        case OPERATION_ADD -> cells[cell] = (cells[cell] + values[programCounter]) << shift >> shift;
                        case OPERATION_SET -> cells[cell] = values[programCounter] << shift >> shift;
                        case OPERATION_LOOP_START -> {
                            pointer = cell;
                            if (cells[pointer] == 0) {
                                programCounter = values[programCounter];
                            }
                        }
                        case OPERATION_LOOP_END -> {
                            pointer = cell;
                            if (cells[pointer] != 0) {
                                programCounter = values[programCounter];
                                if (--tick == 0) {
                                    tick = checkpoint(left, limits);
                                    left -= tick;
                                }
                            }
                        }
                        case OPERATION_LOAD -> {
                            factor = cells[cell];
                            if (factor == 0) {
                                programCounter = values[programCounter];
                            } else {
                                cells[cell] = 0;
                            }
                        }
                        case OPERATION_MULTIPLY -> cells[cell] = (cells[cell] + factor * values[programCounter]) << shift >> shift;
                        case OPERATION_SCAN -> {
                            int next = cell;
                            while (cells[next] != 0) {
                                next = Math.floorMod(next + values[programCounter], cells.length);
                                // A wrapping scan without zero cell never ends, so it has to stay interruptible
                                if (--tick == 0) {
                                    tick = checkpoint(left, limits);
                                    left -= tick;
                                }
                            }

                            pointer = next;
                        }
                        case OPERATION_OUTPUT -> {
                            final byte value = (byte) cells[cell];
                            buffer[buffered++] = value;
                            if (value == '\n' || buffered == buffer.length) {
                                buffered = flush(output, buffer, buffered);
                            }
                        }
                        case OPERATION_INPUT -> {
                            buffered = flush(output, buffer, buffered);
                            cells[cell] = read(input, cells[cell], endOfInput, shift);
                        }
                    }

                    programCounter++;
                }
            }
        } finally {
            memory.pointer(pointer);
            flush(output, buffer, buffered);
        }
    }

    private static int checkpoint(final long left, final ExecutionLimits limits) throws InterruptedIOException {
        if (Thread.interrupted()) {
            throw new InterruptedIOException("Execution interrupted");
        }

        if (left == 0) {
            throw new IllegalStateException("Loop iteration limit of " + limits.maximumIterations() + " exceeded");
        }

        return (int) Math.min(CHECK_INTERVAL, left);
    }

    private static int scanEnd(final int[] cells, final int start, final int step) {
        int index = start;
        while (index >= 0 && index < cells.length && cells[index] != 0) {
            index += step;
        }

        return index;
    }

    private static int flush(final OutputStream output, final byte[] buffer, final int length) throws IOException {
        if (length > 0) {
            output.write(buffer, 0, length);
        }

        output.flush();
        return 0;
    }

    private static int read(final InputStream input, final int current, final EndOfInput endOfInput, final int shift) throws IOException {
        final int value = input.read();
        if (value != -1) {
            return ((byte) value) << shift >> shift;
        }

        return switch (endOfInput) {
            case MINUS_ONE -> -1;
            case ZERO -> 0;
            case UNCHANGED -> current;
        };
    }

}
