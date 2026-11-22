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

    private final List<Instruction> instructions;
    private final int[] ops;
    private final int[] values;
    private final int[] offsets;

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
        }
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
     * Executes this program, blocking until it finishes, the memory keeps its state afterward.
     *
     * @param memory The memory to execute on, must not be shared with a concurrent execution.
     * @param in     Input stream, read byte-wise, end of stream is read as -1.
     * @param out    Output stream, buffered and flushed on newline, before input and when finished.
     * @throws IOException               If reading or writing fails.
     * @throws IndexOutOfBoundsException If a cell outside the memory is accessed.
     */
    public void execute(final Memory memory, final InputStream in, final OutputStream out) throws IOException {
        final int[] ops = this.ops;
        final int[] values = this.values;
        final int[] offsets = this.offsets;
        final int length = ops.length;
        final int[] mem = memory.cells();
        final int shift = 32 - memory.type().bits;
        final byte[] buffer = new byte[8192];
        int buffered = 0;
        int p = memory.pointer();
        int pc = 0;
        int factor = 0;
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
                        p += offsets[pc];
                        if (mem[p] == 0) {
                            pc = values[pc];
                        }
                    }
                    case OP_LOOP_END -> {
                        p += offsets[pc];
                        if (mem[p] != 0) {
                            pc = values[pc];
                        }
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
                        p += offsets[pc];
                        final int step = values[pc];
                        while (mem[p] != 0) {
                            p += step;
                        }
                    }
                    case OP_OUTPUT -> {
                        final byte b = (byte) mem[p + offsets[pc]];
                        buffer[buffered++] = b;
                        if (b == '\n' || buffered == buffer.length) {
                            out.write(buffer, 0, buffered);
                            out.flush();
                            buffered = 0;
                        }
                    }
                    case OP_INPUT -> {
                        if (buffered > 0) {
                            out.write(buffer, 0, buffered);
                            out.flush();
                            buffered = 0;
                        }

                        mem[p + offsets[pc]] = ((byte) in.read()) << shift >> shift;
                    }
                }
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            // Bounds are only checked by the JVM's array access to keep pointer movement free of branches
            final int op = ops[pc];
            final int index = op == OP_SCAN || op == OP_LOOP_START || op == OP_LOOP_END ? p : p + offsets[pc];
            if (index >= 0 && index < mem.length) {
                throw e;
            }

            throw new IndexOutOfBoundsException((index < 0 ? "Memory underflow" : "Memory overflow") + " at cell " + index);
        } finally {
            memory.pointer(p);
            if (buffered > 0) {
                out.write(buffer, 0, buffered);
            }

            out.flush();
        }
    }

}
