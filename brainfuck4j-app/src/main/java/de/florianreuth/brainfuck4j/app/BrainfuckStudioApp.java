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

import com.formdev.flatlaf.FlatLightLaf;
import de.florianreuth.brainfuck4j.Brainfuck4J;
import de.florianreuth.brainfuck4j.Instruction;
import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.Program;
import de.florianreuth.brainfuck4j.dialect.DialectType;
import de.florianreuth.brainfuck4j.export.ExportLanguage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

public final class BrainfuckStudioApp extends JFrame {

    private static final int DEFAULT_MEMORY_SIZE = 30000;

    private final RSyntaxTextArea editor = new RSyntaxTextArea();
    private final JTextArea outputArea = new JTextArea();
    private final JTextArea inputArea = new JTextArea();
    private final JComboBox<DialectType> dialectBox = new JComboBox<>(DialectType.values());
    private final JComboBox<MemoryType> memoryBox = new JComboBox<>(MemoryType.values());
    private final JTextField memorySizeField = new JTextField(String.valueOf(DEFAULT_MEMORY_SIZE), 8);
    private final JLabel statusLabel = new JLabel("Ready");
    private final InstructionsTableModel instructionsModel = new InstructionsTableModel();
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");

    private SwingWorker<Void, String> currentWorker;
    private TextAreaOutputStream currentOutput;

    public static void main(final String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new BrainfuckStudioApp().setVisible(true));
    }

    public BrainfuckStudioApp() {
        super("Brainfuck4J Studio");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLayout(new BorderLayout());

        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        editor.setCodeFoldingEnabled(true);

        add(buildTopPanel(), BorderLayout.NORTH);

        final JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildEditorPane(), buildOutputPane());
        vertical.setResizeWeight(0.5);
        add(vertical, BorderLayout.CENTER);

        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Dialect:"));
        panel.add(dialectBox);
        panel.add(new JLabel("Memory:"));
        panel.add(memoryBox);
        panel.add(new JLabel("Size:"));
        panel.add(memorySizeField);

        runButton.addActionListener(e -> runProgram());
        panel.add(runButton);

        stopButton.addActionListener(e -> cancelExecution());
        stopButton.setEnabled(false);
        panel.add(stopButton);

        final JButton openButton = new JButton("Open");
        openButton.addActionListener(e -> openFile());
        panel.add(openButton);

        final JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveFile());
        panel.add(saveButton);

        final JButton convertButton = new JButton("Convert...");
        convertButton.addActionListener(e -> new DialectConverterDialog(this).setVisible(true));
        panel.add(convertButton);

        final JButton exportButton = new JButton("Export...");
        exportButton.addActionListener(e -> exportProgram());
        panel.add(exportButton);
        return panel;
    }

    private Component buildEditorPane() {
        final JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new RTextScrollPane(editor), new JScrollPane(inputArea));
        editorSplit.setResizeWeight(0.8);
        inputArea.setBorder(BorderFactory.createTitledBorder("Input Stream (optional)"));
        return editorSplit;
    }

    private Component buildOutputPane() {
        final JPanel panel = new JPanel(new BorderLayout());
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        final JTable instructionsTable = new JTable(instructionsModel);
        instructionsTable.setFillsViewportHeight(true);

        final JScrollPane tableScroll = new JScrollPane(instructionsTable);
        tableScroll.setPreferredSize(new Dimension(400, 150));
        panel.add(tableScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildStatusBar() {
        final JPanel status = new JPanel(new BorderLayout());
        status.add(statusLabel, BorderLayout.CENTER);
        return status;
    }

    private void runProgram() {
        if (currentWorker != null) {
            return;
        }

        outputArea.setText("");
        instructionsModel.setInstructions(List.of());
        statusLabel.setText("Running...");
        runButton.setEnabled(false);
        stopButton.setEnabled(true);

        final String program = normalizedProgram();
        final MemoryType memoryType = selectedMemoryType();
        final int memorySize = selectedMemorySize();
        final byte[] stdin = inputArea.getText().getBytes(StandardCharsets.UTF_8);
        final TextAreaOutputStream output = new TextAreaOutputStream(outputArea);
        currentOutput = output;
        currentWorker = new SwingWorker<>() {
            private List<Instruction> instructions;
            private long elapsedMillis;

            @Override
            protected Void doInBackground() {
                try {
                    final long start = System.currentTimeMillis();
                    final Program compiled = Brainfuck4J.compile(program);
                    compiled.execute(memoryType.create(memorySize), new ByteArrayInputStream(stdin), output);
                    instructions = compiled.instructions();
                    elapsedMillis = System.currentTimeMillis() - start;
                } catch (final Throwable throwable) {
                    if (!isCancelled()) {
                        publish("\n[ERROR] " + throwable.getMessage());
                    }
                }

                return null;
            }

            @Override
            protected void process(final List<String> chunks) {
                for (final String chunk : chunks) {
                    outputArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                // A cancelled worker was already reset and must not touch the state of a newer run
                if (isCancelled()) {
                    return;
                }

                resetExecutionState();
                if (instructions != null) {
                    instructionsModel.setInstructions(instructions);
                    statusLabel.setText("Finished in " + elapsedMillis + " ms | Instructions: " + instructions.size());
                } else {
                    statusLabel.setText("Execution finished with errors");
                }
            }
        };
        currentWorker.execute();
    }

    private void cancelExecution() {
        if (currentWorker != null) {
            currentWorker.cancel(true);
            resetExecutionState();
            statusLabel.setText("Execution cancelled");
        }
    }

    private void resetExecutionState() {
        if (currentOutput != null) {
            currentOutput.close();
            currentOutput = null;
        }

        runButton.setEnabled(true);
        stopButton.setEnabled(false);
        currentWorker = null;
    }

    private void exportProgram() {
        final ExportLanguage language = (ExportLanguage) JOptionPane.showInputDialog(this, "Language:", "Export", JOptionPane.PLAIN_MESSAGE, null, ExportLanguage.values(), ExportLanguage.C);
        if (language == null) {
            return;
        }

        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("Program." + language.extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        final Path path = chooser.getSelectedFile().toPath();
        final String fileName = path.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        try {
            final Program program = Brainfuck4J.compile(normalizedProgram());
            Files.writeString(path, language.export(program, selectedMemoryType(), selectedMemorySize(), dot == -1 ? fileName : fileName.substring(0, dot)));
            statusLabel.setText("Exported " + language.name + " to " + path);
        } catch (final IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String normalizedProgram() {
        final DialectType dialect = (DialectType) dialectBox.getSelectedItem();
        final String program = editor.getText();
        return dialect == null || dialect == DialectType.BRAINFUCK ? program : dialect.convert(program, DialectType.BRAINFUCK);
    }

    private MemoryType selectedMemoryType() {
        final MemoryType memoryType = (MemoryType) memoryBox.getSelectedItem();
        return memoryType == null ? MemoryType.BIT_32 : memoryType;
    }

    private int selectedMemorySize() {
        try {
            final int size = Integer.parseInt(memorySizeField.getText().trim());
            return size > 0 ? size : DEFAULT_MEMORY_SIZE;
        } catch (final NumberFormatException e) {
            return DEFAULT_MEMORY_SIZE;
        }
    }

    private void openFile() {
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final Path path = chooser.getSelectedFile().toPath();
            try {
                editor.setText(Files.readString(path));
            } catch (final IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile() {
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            final Path path = chooser.getSelectedFile().toPath();
            try {
                Files.writeString(path, editor.getText());
            } catch (final IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

}
