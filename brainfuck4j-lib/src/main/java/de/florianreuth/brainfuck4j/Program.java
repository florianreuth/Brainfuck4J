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

    private static final int OP_ADD = 0;
    private static final int OP_SET = 1;
    private static final int OP_MOVE = 2;
    private static final int OP_LOOP_START = 3;
    private static final int OP_LOOP_END = 4;
    private static final int OP_LOAD = 5;
    private static final int OP_MULTIPLY = 6;
    private static final int OP_SCAN = 7;
    private static final int OP_OUTPUT = 8;
    private static final int OP_INPUT = 9;
    private static final int CHECK_INTERVAL = 1 << 16;

    private final List<Instruction> instructions;
    private final int[] ops;
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
        this.ops = new int[length];
        this.values = new int[length];
        this.offsets = new int[length];
        long minOffset = Long.MAX_VALUE;
        long maxOffset = Long.MIN_VALUE;
        for (int i = 0; i < length; i++) {
            final Instruction instruction = this.instructions.get(i);
            validate(i, instruction);
            ops[i] = switch (instruction.type()) {
                case ADD -> OP_ADD;
                case SET -> OP_SET;
                case MOVE -> OP_MOVE;
                case LOOP_START -> OP_LOOP_START;
                case LOOP_END -> OP_LOOP_END;
                case LOAD -> OP_LOAD;
                case MULTIPLY -> OP_MULTIPLY;
                case SCAN -> OP_SCAN;
                case OUTPUT -> OP_OUTPUT;
                case INPUT -> OP_INPUT;
            };
            values[i] = instruction.value();
            offsets[i] = instruction.offset();
            if (instruction.type() != InstructionType.MOVE && instruction.type() != InstructionType.LOOP_START && instruction.type() != InstructionType.LOOP_END && instruction.type() != InstructionType.SCAN) {
                minOffset = Math.min(minOffset, instruction.offset());
                maxOffset = Math.max(maxOffset, instruction.offset());
            }
        }

        this.span = minOffset > maxOffset ? 0 : (int) Math.min(Integer.MAX_VALUE, maxOffset - minOffset);
    }

    private void validate(final int index, final Instruction instruction) {
        final int target = instruction.value();
        final boolean valid = switch (instruction.type()) {
            case LOOP_START -> target > index && target < instructions.size() && instructions.get(target).type() == InstructionType.LOOP_END && instructions.get(target).value() == index;
            case LOOP_END -> target >= 0 && target < index && instructions.get(target).type() == InstructionType.LOOP_START && instructions.get(target).value() == index;
            case LOAD -> target > index && target < instructions.size() && instructions.subList(index + 1, target + 1).stream().allMatch(it -> it.type() == InstructionType.MULTIPLY);
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
        return instructions;
    }

    /**
     * @return The distance between the lowest and highest cell offset accessed from one pointer position, a {@link TapeBoundary#WRAP} memory needs more cells than this.
     */
    public int span() {
        return span;
    }

    /**
     * Executes this program without limits, see {@link #execute(Memory, InputStream, OutputStream, ExecutionLimits)}.
     */
    public void execute(final Memory memory, final InputStream in, final OutputStream out) throws IOException {
        execute(memory, in, out, ExecutionLimits.NONE);
    }

    /**
     * Executes this program, blocking until it finishes, the memory keeps its state afterward.
     *
     * @param memory The memory to execute on, must not be shared with a concurrent execution.
     * @param in     Input stream, read byte-wise, see {@link Memory#endOfInput()} for the end of stream.
     * @param out    Output stream, buffered and flushed on newline, before input and when finished.
     * @param limits The resource limits of this execution.
     * @throws IOException               If reading or writing fails.
     * @throws InterruptedIOException    If the executing thread is interrupted, checked every few thousand loop iterations.
     * @throws IndexOutOfBoundsException If a cell outside the memory is accessed with {@link TapeBoundary#ERROR}, or growing exceeds {@link ExecutionLimits#maxCells()}.
     * @throws IllegalStateException     If {@link ExecutionLimits#maxIterations()} is exceeded.
     * @throws IllegalArgumentException  If the memory wraps and is not larger than {@link #span()}.
     */
    public void execute(final Memory memory, final InputStream in, final OutputStream out, final ExecutionLimits limits) throws IOException {
        // Offset folding assumes distinct offsets are distinct cells, which a wrapping memory only guarantees above the span
        if (memory.boundary() == TapeBoundary.WRAP && memory.size() <= span) {
            throw new IllegalArgumentException("A wrapping memory needs more than " + span + " cells for this program, got " + memory.size());
        }

        final int[] ops = this.ops;
        final int[] values = this.values;
        final int[] offsets = this.offsets;
        final int length = ops.length;
        final int shift = 32 - memory.type().bits;
        final TapeBoundary boundary = memory.boundary();
        final EndOfInput endOfInput = memory.endOfInput();
        final byte[] buffer = new byte[8192];
        int[] mem = memory.cells();
        int buffered = 0;
        int p = memory.pointer();
        int pc = 0;
        int factor = 0;
        // Iterations are handed out in chunks so the hot path only decrements an int on loop back-edges, the budget ends on the first iteration over the limit
        final long budget = limits.maxIterations() == Long.MAX_VALUE ? Long.MAX_VALUE : limits.maxIterations() + 1;
        int tick = (int) Math.min(CHECK_INTERVAL, budget);
        long left = budget - tick;
        try {
            while (pc < length) {
                // Every instruction faults before its first side effect, so it can be retried or replayed after a boundary violation
                try {
                    for (; pc < length; pc++) {
                        switch (ops[pc]) {
                            case OP_ADD -> {
                                final int i = p + offsets[pc];
                                mem[i] = (mem[i] + values[pc]) << shift >> shift;
                            }
                            case OP_SET -> mem[p + offsets[pc]] = values[pc] << shift >> shift;
                            case OP_MOVE -> p += values[pc];
                            case OP_LOOP_START -> {
                                final int next = p + offsets[pc];
                                if (mem[next] == 0) {
                                    pc = values[pc];
                                }

                                p = next;
                            }
                            case OP_LOOP_END -> {
                                final int next = p + offsets[pc];
                                if (mem[next] != 0) {
                                    pc = values[pc];
                                    if (--tick == 0) {
                                        tick = checkpoint(left, limits);
                                        left -= tick;
                                    }
                                }

                                p = next;
                            }
                            case OP_LOAD -> {
                                final int i = p + offsets[pc];
                                factor = mem[i];
                                if (factor == 0) {
                                    pc = values[pc];
                                } else {
                                    mem[i] = 0;
                                }
                            }
                            case OP_MULTIPLY -> {
                                final int i = p + offsets[pc];
                                mem[i] = (mem[i] + factor * values[pc]) << shift >> shift;
                            }
                            case OP_SCAN -> {
                                final int step = values[pc];
                                int next = p + offsets[pc];
                                while (mem[next] != 0) {
                                    next += step;
                                }

                                p = next;
                            }
                            case OP_OUTPUT -> {
                                final byte b = (byte) mem[p + offsets[pc]];
                                buffer[buffered++] = b;
                                if (b == '\n' || buffered == buffer.length) {
                                    buffered = flush(out, buffer, buffered);
                                }
                            }
                            case OP_INPUT -> {
                                final int i = p + offsets[pc];
                                // Reading the cell first makes an out of bounds access fail before any input is consumed
                                final int current = mem[i];
                                buffered = flush(out, buffer, buffered);
                                mem[i] = input(in, current, endOfInput, shift);
                            }
                        }
                    }
                } catch (final ArrayIndexOutOfBoundsException e) {
                    final int op = ops[pc];
                    final int index = op == OP_SCAN ? scanEnd(mem, p + offsets[pc], values[pc]) : p + offsets[pc];
                    if (index >= 0 && index < mem.length) {
                        throw e;
                    }

                    if (boundary == TapeBoundary.GROW) {
                        p += memory.grow(index, limits.maxCells());
                        mem = memory.cells();
                        continue;
                    }

                    if (boundary == TapeBoundary.ERROR) {
                        throw new IndexOutOfBoundsException((index < 0 ? "Memory underflow" : "Memory overflow") + " at cell " + index);
                    }

                    final int i = Math.floorMod(index, mem.length);
                    p = Math.floorMod(p, mem.length);
                    switch (op) {
                        case OP_ADD -> mem[i] = (mem[i] + values[pc]) << shift >> shift;
                        case OP_SET -> mem[i] = values[pc] << shift >> shift;
                        case OP_LOOP_START -> {
                            p = i;
                            if (mem[p] == 0) {
                                pc = values[pc];
                            }
                        }
                        case OP_LOOP_END -> {
                            p = i;
                            if (mem[p] != 0) {
                                pc = values[pc];
                                if (--tick == 0) {
                                    tick = checkpoint(left, limits);
                                    left -= tick;
                                }
                            }
                        }
                        case OP_LOAD -> {
                            factor = mem[i];
                            if (factor == 0) {
                                pc = values[pc];
                            } else {
                                mem[i] = 0;
                            }
                        }
                        case OP_MULTIPLY -> mem[i] = (mem[i] + factor * values[pc]) << shift >> shift;
                        case OP_SCAN -> {
                            int next = i;
                            while (mem[next] != 0) {
                                next = Math.floorMod(next + values[pc], mem.length);
                                // A wrapping scan without zero cell never ends, so it has to stay interruptible
                                if (--tick == 0) {
                                    tick = checkpoint(left, limits);
                                    left -= tick;
                                }
                            }

                            p = next;
                        }
                        case OP_OUTPUT -> {
                            final byte b = (byte) mem[i];
                            buffer[buffered++] = b;
                            if (b == '\n' || buffered == buffer.length) {
                                buffered = flush(out, buffer, buffered);
                            }
                        }
                        case OP_INPUT -> {
                            buffered = flush(out, buffer, buffered);
                            mem[i] = input(in, mem[i], endOfInput, shift);
                        }
                    }

                    pc++;
                }
            }
        } finally {
            memory.pointer(p);
            flush(out, buffer, buffered);
        }
    }

    private static int checkpoint(final long left, final ExecutionLimits limits) throws InterruptedIOException {
        if (Thread.interrupted()) {
            throw new InterruptedIOException("Execution interrupted");
        }

        if (left == 0) {
            throw new IllegalStateException("Loop iteration limit of " + limits.maxIterations() + " exceeded");
        }

        return (int) Math.min(CHECK_INTERVAL, left);
    }

    private static int scanEnd(final int[] mem, final int start, final int step) {
        int index = start;
        while (index >= 0 && index < mem.length && mem[index] != 0) {
            index += step;
        }

        return index;
    }

    private static int flush(final OutputStream out, final byte[] buffer, final int length) throws IOException {
        if (length > 0) {
            out.write(buffer, 0, length);
        }

        out.flush();
        return 0;
    }

    private static int input(final InputStream in, final int current, final EndOfInput endOfInput, final int shift) throws IOException {
        final int read = in.read();
        if (read != -1) {
            return ((byte) read) << shift >> shift;
        }

        return switch (endOfInput) {
            case MINUS_ONE -> -1;
            case ZERO -> 0;
            case UNCHANGED -> current;
        };
    }

}
