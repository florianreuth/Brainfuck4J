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

import de.florianreuth.brainfuck4j.dialect.DialectType;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public final class DialectConverterDialog extends JDialog {

    private final JTextArea inputArea = new JTextArea(10, 40);
    private final JTextArea outputArea = new JTextArea(10, 40);
    private final JComboBox<DialectType> fromBox = new JComboBox<>(DialectType.values());
    private final JComboBox<DialectType> toBox = new JComboBox<>(DialectType.values());

    public DialectConverterDialog(final Frame owner) {
        super(owner, "Dialect Converter", true);
        this.setLayout(new BorderLayout());

        final JPanel top = new JPanel(new FlowLayout());
        top.add(new JLabel("From:"));
        top.add(this.fromBox);
        top.add(new JLabel("To:"));
        top.add(this.toBox);

        final JButton convertButton = new JButton("Convert");
        convertButton.addActionListener(event -> this.convert());
        top.add(convertButton);

        final JButton copyButton = new JButton("Copy Output");
        copyButton.addActionListener(event -> this.copyOutput());
        top.add(copyButton);
        this.add(top, BorderLayout.NORTH);

        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(this.inputArea), new JScrollPane(this.outputArea));
        split.setResizeWeight(0.5);
        this.outputArea.setEditable(false);
        this.add(split, BorderLayout.CENTER);

        this.pack();
        this.setLocationRelativeTo(owner);
    }

    private void convert() {
        final DialectType from = (DialectType) this.fromBox.getSelectedItem();
        final DialectType to = (DialectType) this.toBox.getSelectedItem();
        if (from == null || to == null) {
            return;
        }

        final String converted = from.convert(this.inputArea.getText(), to);
        this.outputArea.setText(converted);
    }

    private void copyOutput() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(this.outputArea.getText()), null);
    }

}
