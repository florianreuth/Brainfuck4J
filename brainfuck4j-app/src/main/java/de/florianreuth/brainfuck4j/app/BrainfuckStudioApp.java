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
import de.florianreuth.brainfuck4j.EndOfInput;
import de.florianreuth.brainfuck4j.Instruction;
import de.florianreuth.brainfuck4j.Memory;
import de.florianreuth.brainfuck4j.MemoryType;
import de.florianreuth.brainfuck4j.Program;
import de.florianreuth.brainfuck4j.TapeBoundary;
import de.florianreuth.brainfuck4j.dialect.DialectType;
import de.florianreuth.brainfuck4j.export.ExportLanguage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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
    private final JComboBox<TapeBoundary> boundaryBox = new JComboBox<>(TapeBoundary.values());
    private final JComboBox<EndOfInput> endOfInputBox = new JComboBox<>(EndOfInput.values());
    private final JTextField memorySizeField = new JTextField(String.valueOf(DEFAULT_MEMORY_SIZE), 8);
    private final JLabel statusLabel = new JLabel("Ready");
    private final InstructionsTableModel instructionsModel = new InstructionsTableModel();
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");

    private SwingWorker<Void, String> currentWorker;
    private TextAreaOutputStream currentOutput;

    public static void main(final String[] arguments) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new BrainfuckStudioApp().setVisible(true));
    }

    public BrainfuckStudioApp() {
        super("Brainfuck4J Studio");
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(1100, 700);
        this.setLayout(new BorderLayout());

        this.editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        this.editor.setCodeFoldingEnabled(true);

        this.add(this.buildTopPanel(), BorderLayout.NORTH);

        final JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, this.buildEditorPane(), this.buildOutputPane());
        vertical.setResizeWeight(0.5);
        this.add(vertical, BorderLayout.CENTER);

        this.add(this.buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        final JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settings.add(new JLabel("Dialect:"));
        settings.add(this.dialectBox);
        settings.add(new JLabel("Memory:"));
        settings.add(this.memoryBox);
        settings.add(new JLabel("Size:"));
        settings.add(this.memorySizeField);
        settings.add(new JLabel("Tape:"));
        settings.add(this.boundaryBox);
        settings.add(new JLabel("EOF:"));
        settings.add(this.endOfInputBox);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        this.runButton.addActionListener(event -> this.runProgram());
        actions.add(this.runButton);

        this.stopButton.addActionListener(event -> this.cancelExecution());
        this.stopButton.setEnabled(false);
        actions.add(this.stopButton);

        final JButton openButton = new JButton("Open");
        openButton.addActionListener(event -> this.openFile());
        actions.add(openButton);

        final JButton saveButton = new JButton("Save");
        saveButton.addActionListener(event -> this.saveFile());
        actions.add(saveButton);

        final JButton convertButton = new JButton("Convert...");
        convertButton.addActionListener(event -> new DialectConverterDialog(this).setVisible(true));
        actions.add(convertButton);

        final JButton exportButton = new JButton("Export...");
        exportButton.addActionListener(event -> this.exportProgram());
        actions.add(exportButton);

        // Two rows since a single FlowLayout row in BorderLayout.NORTH hides wrapped components at the default width
        final JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(settings);
        panel.add(actions);
        return panel;
    }

    private Component buildEditorPane() {
        final JSplitPane editorSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new RTextScrollPane(this.editor), new JScrollPane(this.inputArea));
        editorSplit.setResizeWeight(0.8);
        this.inputArea.setBorder(BorderFactory.createTitledBorder("Input Stream (optional)"));
        return editorSplit;
    }

    private Component buildOutputPane() {
        final JPanel panel = new JPanel(new BorderLayout());
        this.outputArea.setEditable(false);
        this.outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(this.outputArea), BorderLayout.CENTER);

        final JTable instructionsTable = new JTable(this.instructionsModel);
        instructionsTable.setFillsViewportHeight(true);

        final JScrollPane tableScroll = new JScrollPane(instructionsTable);
        tableScroll.setPreferredSize(new Dimension(400, 150));
        panel.add(tableScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildStatusBar() {
        final JPanel status = new JPanel(new BorderLayout());
        status.add(this.statusLabel, BorderLayout.CENTER);
        return status;
    }

    private void runProgram() {
        if (this.currentWorker != null) {
            return;
        }

        this.outputArea.setText("");
        this.instructionsModel.setInstructions(List.of());
        this.statusLabel.setText("Running...");
        this.runButton.setEnabled(false);
        this.stopButton.setEnabled(true);

        final String program = this.normalizedProgram();
        final Memory memory = new Memory(this.selectedMemoryType(), this.selectedMemorySize(), (TapeBoundary) this.boundaryBox.getSelectedItem(), (EndOfInput) this.endOfInputBox.getSelectedItem());
        final byte[] input = this.inputArea.getText().getBytes(StandardCharsets.UTF_8);
        final TextAreaOutputStream output = new TextAreaOutputStream(this.outputArea);
        this.currentOutput = output;
        this.currentWorker = new SwingWorker<>() {
            private List<Instruction> instructions;
            private long elapsedMilliseconds;

            @Override
            protected Void doInBackground() {
                try {
                    final long startMilliseconds = System.currentTimeMillis();
                    final Program compiled = Brainfuck4J.compile(program);
                    compiled.execute(memory, new ByteArrayInputStream(input), output);
                    this.instructions = compiled.instructions();
                    this.elapsedMilliseconds = System.currentTimeMillis() - startMilliseconds;
                } catch (final Throwable throwable) {
                    if (!this.isCancelled()) {
                        this.publish("\n[ERROR] " + throwable.getMessage());
                    }
                }

                return null;
            }

            @Override
            protected void process(final List<String> chunks) {
                for (final String chunk : chunks) {
                    BrainfuckStudioApp.this.outputArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                // A cancelled worker was already reset and must not touch the state of a newer run
                if (this.isCancelled()) {
                    return;
                }

                BrainfuckStudioApp.this.resetExecutionState();
                if (this.instructions != null) {
                    BrainfuckStudioApp.this.instructionsModel.setInstructions(this.instructions);
                    BrainfuckStudioApp.this.statusLabel.setText("Finished in " + this.elapsedMilliseconds + " ms | Instructions: " + this.instructions.size());
                } else {
                    BrainfuckStudioApp.this.statusLabel.setText("Execution finished with errors");
                }
            }
        };
        this.currentWorker.execute();
    }

    private void cancelExecution() {
        if (this.currentWorker != null) {
            this.currentWorker.cancel(true);
            this.resetExecutionState();
            this.statusLabel.setText("Execution cancelled");
        }
    }

    private void resetExecutionState() {
        if (this.currentOutput != null) {
            this.currentOutput.close();
            this.currentOutput = null;
        }

        this.runButton.setEnabled(true);
        this.stopButton.setEnabled(false);
        this.currentWorker = null;
    }

    private void exportProgram() {
        if (this.boundaryBox.getSelectedItem() != TapeBoundary.ERROR) {
            JOptionPane.showMessageDialog(this, "Exported programs only support the ERROR tape boundary.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

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
            final Program program = Brainfuck4J.compile(this.normalizedProgram());
            final String source = language.export(program, this.selectedMemoryType(), this.selectedMemorySize(), (EndOfInput) this.endOfInputBox.getSelectedItem(), dot == -1 ? fileName : fileName.substring(0, dot));
            Files.writeString(path, source);
            this.statusLabel.setText("Exported " + language.name + " to " + path);
        } catch (final IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String normalizedProgram() {
        final DialectType dialect = (DialectType) this.dialectBox.getSelectedItem();
        final String program = this.editor.getText();
        return dialect == null || dialect == DialectType.BRAINFUCK ? program : dialect.convert(program, DialectType.BRAINFUCK);
    }

    private MemoryType selectedMemoryType() {
        final MemoryType memoryType = (MemoryType) this.memoryBox.getSelectedItem();
        return memoryType == null ? MemoryType.BIT_32 : memoryType;
    }

    private int selectedMemorySize() {
        try {
            final int size = Integer.parseInt(this.memorySizeField.getText().trim());
            return size > 0 ? size : DEFAULT_MEMORY_SIZE;
        } catch (final NumberFormatException exception) {
            return DEFAULT_MEMORY_SIZE;
        }
    }

    private void openFile() {
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final Path path = chooser.getSelectedFile().toPath();
            try {
                // Comments may contain arbitrary bytes, the String constructor replaces malformed UTF-8 instead of throwing like Files#readString
                this.editor.setText(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            } catch (final IOException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveFile() {
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            final Path path = chooser.getSelectedFile().toPath();
            try {
                Files.writeString(path, this.editor.getText());
            } catch (final IOException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

}
