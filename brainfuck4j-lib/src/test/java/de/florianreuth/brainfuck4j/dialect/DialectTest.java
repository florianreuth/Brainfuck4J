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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DialectTest {

    private static final String PROGRAM = "++[>+<-]>.<,";

    @ParameterizedTest
    @EnumSource(value = DialectType.class, names = "COW", mode = EnumSource.Mode.EXCLUDE)
    public void roundTrip(final DialectType dialect) {
        assertEquals(PROGRAM, dialect.convert(DialectType.BRAINFUCK.convert(PROGRAM + " comment", dialect), DialectType.BRAINFUCK));
    }

    @Test
    public void cowMapsInputAndOutputToTheSameKeyword() {
        assertEquals("..", DialectType.COW.convert(DialectType.BRAINFUCK.convert(",.", DialectType.COW), DialectType.BRAINFUCK));
    }

    @Test
    public void customDialect() {
        final Dialect words = new Dialect("right ", "left ", "increase ", "decrease ", "open ", "close ", "read ", "write ");
        assertEquals("increase increase open right increase left decrease close right write ", DialectType.BRAINFUCK.dialect.convert("++[>+<-]>.", words));
        assertEquals("++[>+<-]>.", words.convert("increase increase open right increase left decrease close right write ", DialectType.BRAINFUCK.dialect));
    }

}
