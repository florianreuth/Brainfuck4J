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

package de.florianreuth.brainfuck4j.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Appends UTF-8 decoded output to a text area on each flush, writing after {@link #close()} fails to abort the execution.
 */
public final class TextAreaOutputStream extends OutputStream {

    private final JTextArea textArea;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private volatile boolean closed;

    public TextAreaOutputStream(final JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(final int value) throws IOException {
        this.ensureOpen();
        this.buffer.write(value);
    }

    @Override
    public void write(final byte[] bytes, final int offset, final int length) throws IOException {
        this.ensureOpen();
        this.buffer.write(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
        this.ensureOpen();
        if (this.buffer.size() == 0) {
            return;
        }

        final String text = this.buffer.toString(StandardCharsets.UTF_8);
        this.buffer.reset();
        SwingUtilities.invokeLater(() -> this.textArea.append(text));
    }

    @Override
    public void close() {
        this.closed = true;
    }

    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Execution cancelled");
        }
    }

}
