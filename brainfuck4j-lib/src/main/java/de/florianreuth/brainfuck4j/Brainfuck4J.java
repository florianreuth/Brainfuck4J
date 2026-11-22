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

import de.florianreuth.brainfuck4j.dialect.Dialect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compiler for the Brainfuck programming language, turns source code into an optimized {@link Program}.
 * <p>
 * Pointer movement and cell changes are deferred, so straight-line code collapses into offset-addressed
 * {@link InstructionType#ADD}/{@link InstructionType#SET} instructions.
 *
 * @see Program
 * @see Memory
 * @see Dialect
 */
public final class Brainfuck4J {

    private final List<Instruction> instructions = new ArrayList<>();
    private final TreeMap<Integer, CellChange> pending = new TreeMap<>();
    private final Deque<Integer> loops = new ArrayDeque<>();
    private int offset;

    private Brainfuck4J() {
    }

    /**
     * Parses and optimizes a Brainfuck program.
     *
     * @param input The Brainfuck program code, non-instruction characters are ignored.
     * @return The compiled program, reusable for multiple executions.
     * @throws IllegalArgumentException If the loops in the code are unbalanced.
     */
    public static Program compile(final String input) {
        final byte[] tokens = tokenize(input);
        final Brainfuck4J compiler = new Brainfuck4J();
        for (int i = 0; i < tokens.length; i++) {
            switch (tokens[i]) {
                case '+' -> compiler.add(1);
                case '-' -> compiler.add(-1);
                case '>' -> compiler.offset++;
                case '<' -> compiler.offset--;
                case '.' -> compiler.output();
                case ',' -> compiler.input();
                case '[' -> i = compiler.loop(tokens, i);
                case ']' -> compiler.endLoop();
            }
        }

        compiler.flushCells();
        compiler.flushMove();
        return new Program(compiler.instructions);
    }

    private static byte[] tokenize(final String input) {
        final byte[] tokens = new byte[input.length()];
        final Deque<Integer> loops = new ArrayDeque<>();
        int length = 0;
        for (int i = 0; i < input.length(); i++) {
            final char character = input.charAt(i);
            switch (character) {
                case '+', '-', '>', '<', '.', ',' -> tokens[length++] = (byte) character;
                case '[' -> {
                    loops.push(i);
                    tokens[length++] = (byte) character;
                }
                case ']' -> {
                    if (loops.poll() == null) {
                        throw new IllegalArgumentException("Unmatched ']' at index " + i);
                    }

                    tokens[length++] = (byte) character;
                }
                default -> {
                }
            }
        }

        if (!loops.isEmpty()) {
            throw new IllegalArgumentException("Unclosed '[' at index " + loops.peek());
        }

        return Arrays.copyOf(tokens, length);
    }

    private void add(final int delta) {
        this.pending.merge(this.offset, new CellChange(false, delta), (current, change) -> new CellChange(current.set(), current.value() + change.value()));
    }

    private void clear() {
        this.pending.put(this.offset, new CellChange(true, 0));
    }

    private void output() {
        this.flushCell(this.offset);
        this.emit(InstructionType.OUTPUT, 0, this.offset);
    }

    private void input() {
        // Flushed instead of dropped since EndOfInput.UNCHANGED keeps the cell value at the end of input
        this.flushCell(this.offset);
        this.emit(InstructionType.INPUT, 0, this.offset);
    }

    private int loop(final byte[] tokens, final int start) {
        final Map<Integer, Integer> deltas = new TreeMap<>();
        int move = 0;
        int end = start + 1;
        for (; ; end++) {
            final byte token = tokens[end];
            if (token == '+') {
                deltas.merge(move, 1, Integer::sum);
            } else if (token == '-') {
                deltas.merge(move, -1, Integer::sum);
            } else if (token == '>') {
                move++;
            } else if (token == '<') {
                move--;
            } else {
                break;
            }
        }

        if (tokens[end] == ']') {
            deltas.values().removeIf(delta -> delta == 0);
            final int current = deltas.getOrDefault(0, 0);
            if (move == 0 && deltas.size() == 1 && (current == 1 || current == -1)) {
                this.clear();
                return end;
            } else if (move == 0 && current == -1) {
                this.multiply(deltas);
                return end;
            } else if (move != 0 && deltas.isEmpty()) {
                this.flushCells();
                this.emit(InstructionType.SCAN, move, this.takeMove());
                return end;
            }
        }

        this.flushCells();
        this.loops.push(this.instructions.size());
        this.emit(InstructionType.LOOP_START, -1, this.takeMove());
        return start;
    }

    private void endLoop() {
        this.flushCells();
        final int start = this.loops.pop();
        final int end = this.instructions.size();
        this.emit(InstructionType.LOOP_END, start, this.takeMove());
        this.instructions.set(start, new Instruction(InstructionType.LOOP_START, end, this.instructions.get(start).offset()));
    }

    private void multiply(final Map<Integer, Integer> deltas) {
        for (final int target : deltas.keySet()) {
            this.flushCell(this.offset + target);
        }

        final int load = this.instructions.size();
        this.emit(InstructionType.LOAD, -1, this.offset);
        for (final Map.Entry<Integer, Integer> entry : deltas.entrySet()) {
            if (entry.getKey() != 0) {
                this.emit(InstructionType.MULTIPLY, entry.getValue(), this.offset + entry.getKey());
            }
        }

        this.instructions.set(load, new Instruction(InstructionType.LOAD, this.instructions.size() - 1, this.offset));
    }

    private void flushCell(final int cell) {
        final CellChange change = this.pending.remove(cell);
        if (change != null) {
            this.emitChange(cell, change);
        }
    }

    private void flushCells() {
        this.pending.forEach(this::emitChange);
        this.pending.clear();
    }

    private void flushMove() {
        if (this.offset != 0) {
            this.emit(InstructionType.MOVE, this.takeMove(), 0);
        }
    }

    private int takeMove() {
        final int move = this.offset;
        this.offset = 0;
        return move;
    }

    private void emitChange(final int cell, final CellChange change) {
        if (change.set()) {
            this.emit(InstructionType.SET, change.value(), cell);
        } else if (change.value() != 0) {
            this.emit(InstructionType.ADD, change.value(), cell);
        }
    }

    private void emit(final InstructionType type, final int value, final int offset) {
        this.instructions.add(new Instruction(type, value, offset));
    }

    /**
     * A deferred change of a single cell, either added to or replacing its value.
     */
    private record CellChange(boolean set, int value) {
    }

}
