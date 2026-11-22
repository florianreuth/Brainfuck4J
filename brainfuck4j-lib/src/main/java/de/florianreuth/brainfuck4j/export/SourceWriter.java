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

package de.florianreuth.brainfuck4j.export;

/**
 * Line based source code builder with brace-aware indentation of four spaces, separates closed blocks from following
 * code with a blank line.
 */
public final class SourceWriter {

    private final StringBuilder builder = new StringBuilder();
    private int indent;
    private boolean afterBlock;

    /**
     * @param indent The initial indentation level.
     */
    public SourceWriter(final int indent) {
        this.indent = indent;
    }

    public SourceWriter line(final String line) {
        if (this.afterBlock) {
            this.builder.append('\n');
        }

        this.write(line);
        return this;
    }

    public SourceWriter blank() {
        this.afterBlock = false;
        this.builder.append('\n');
        return this;
    }

    /**
     * Writes a line and indents all following lines one level deeper.
     */
    public SourceWriter open(final String line) {
        this.line(line);
        this.indent++;
        return this;
    }

    /**
     * Indents all following lines one level less and writes a line.
     */
    public SourceWriter close(final String line) {
        this.indent--;
        this.write(line);
        this.afterBlock = true;
        return this;
    }

    /**
     * Appends the content of another writer, keeping its pending block separation.
     */
    public SourceWriter append(final SourceWriter other) {
        this.builder.append(other.builder);
        this.afterBlock = other.afterBlock;
        return this;
    }

    private void write(final String line) {
        this.afterBlock = false;
        this.builder.append("    ".repeat(this.indent)).append(line).append('\n');
    }

    @Override
    public String toString() {
        return this.builder.toString();
    }

}
