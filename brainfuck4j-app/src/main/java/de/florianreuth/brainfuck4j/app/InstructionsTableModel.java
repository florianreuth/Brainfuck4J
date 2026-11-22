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

import de.florianreuth.brainfuck4j.Instruction;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Read-only table of an optimized instruction stream.
 */
public final class InstructionsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Index", "Type", "Value", "Offset"};

    private List<Instruction> data = List.of();

    public void setInstructions(final List<Instruction> instructions) {
        this.data = instructions;
        this.fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return this.data.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(final int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
        final Instruction instruction = this.data.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> rowIndex;
            case 1 -> instruction.type();
            case 2 -> instruction.value();
            case 3 -> instruction.offset();
            default -> "";
        };
    }

}
