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

package de.florianreuth.brainfuck4j.dialect;

/**
 * Keywords of a Brainfuck dialect, see {@link DialectType} for common ones.
 */
public record Dialect(String increasePointer, String decreasePointer, String increaseValue, String decreaseValue,
                      String loopStart, String loopEnd, String input, String output) {

    /**
     * Converts code written in this dialect to another one, unknown characters are dropped.
     *
     * @param code   The code written in this dialect.
     * @param target The dialect to convert to.
     * @return The converted code.
     */
    public String convert(final String code, final Dialect target) {
        final String[] from = this.keywords();
        final String[] to = target.keywords();
        final StringBuilder converted = new StringBuilder();
        int index = 0;
        outer:
        while (index < code.length()) {
            for (int i = 0; i < from.length; i++) {
                if (code.startsWith(from[i], index)) {
                    converted.append(to[i]);
                    index += from[i].length();
                    continue outer;
                }
            }
            index++;
        }
        return converted.toString();
    }

    private String[] keywords() {
        // Output before input since COW uses the same keyword for both and printing is the common case
        return new String[]{this.increasePointer, this.decreasePointer, this.increaseValue, this.decreaseValue, this.loopStart, this.loopEnd, this.output, this.input};
    }

}
