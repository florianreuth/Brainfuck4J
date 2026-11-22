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

import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.Program;

/**
 * Registry of languages a {@link Program} can be exported to as standalone source file.
 */
public enum ExportLanguage {

    C("C", "c"),
    JAVA("Java", "java");

    public final String name;
    public final String extension;

    ExportLanguage(final String name, final String extension) {
        this.name = name;
        this.extension = extension;
    }

    /**
     * @param program The program to export.
     * @param type    The cell width.
     * @param size    The number of cells.
     * @param name    The base name of the output file, used as Java class name and ignored for C.
     * @return The source code.
     * @throws IllegalArgumentException If size is not positive or the name is not a valid Java class name.
     */
    public String export(final Program program, final MemoryType type, final int size, final String name) {
        return switch (this) {
            case C -> CExporter.export(program, type, size);
            case JAVA -> JavaExporter.export(program, type, size, name);
        };
    }

    /**
     * @param extension A file extension without dot, case-insensitive.
     * @return The matching language, or null if none.
     */
    public static ExportLanguage byExtension(final String extension) {
        for (final ExportLanguage language : values()) {
            if (language.extension.equalsIgnoreCase(extension)) {
                return language;
            }
        }

        return null;
    }

}
