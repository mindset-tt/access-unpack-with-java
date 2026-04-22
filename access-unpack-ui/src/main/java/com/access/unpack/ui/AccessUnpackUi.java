package com.access.unpack.ui;

import com.access.unpack.core.AccessUnpackService;
import com.access.unpack.core.DatabaseScanResult;
import com.access.unpack.core.Diagnostic;
import com.access.unpack.core.ExportDataFormat;
import com.access.unpack.core.ExtractionRequest;
import com.access.unpack.core.ExtractionResult;
import com.access.unpack.core.FullFidelityMode;
import com.access.unpack.core.QueryScanEntry;
import com.access.unpack.core.SqlDialect;
import com.access.unpack.core.SqlTranslator;
import com.access.unpack.core.TableScanEntry;
import com.access.unpack.core.TextEncoding;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.filechooser.FileSystemView;
import javax.swing.DefaultListModel;
import javax.swing.DefaultCellEditor;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.prefs.Preferences;

public final class AccessUnpackUi {
    private static final Theme LIGHT = new Theme(
            "light",
            new Color(245, 247, 250),
            new Color(255, 255, 255),
            new Color(35, 39, 47),
            new Color(28, 99, 214),
            new Color(224, 236, 255),
            new Color(191, 130, 47),
            new Color(199, 84, 80),
            new Color(216, 221, 228),
            new Color(250, 251, 252),
            Color.WHITE);
    private static final Theme DARK = new Theme(
            "dark",
            new Color(30, 30, 30),
            new Color(37, 37, 38),
            new Color(204, 204, 204),
            new Color(0, 122, 204),
            new Color(21, 57, 84),
            new Color(220, 163, 68),
            new Color(241, 76, 76),
            new Color(62, 62, 66),
            new Color(30, 30, 30),
            new Color(43, 43, 43));
    private static final Preferences PREFS = Preferences.userNodeForPackage(AccessUnpackUi.class);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            createAndShow();
        });
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("access-unpack");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1420, 900));

        AccessUnpackService service = new AccessUnpackService();
        SqlTranslator sqlTranslator = new SqlTranslator();
        final DatabaseScanResult[] currentScan = new DatabaseScanResult[1];
        final Path[] lastOutput = new Path[1];
        final String[] lastScannedInput = new String[1];
        final Theme[] currentTheme = new Theme[]{DARK};
        final Path[] currentPreviewedFile = new Path[1];

        JTextField inputField = new JTextField();
        JTextField outputField = new JTextField();
        JComboBox<FullFidelityMode> fidelityBox = new JComboBox<>(FullFidelityMode.values());
        fidelityBox.setSelectedItem(FullFidelityMode.AUTO);
        JComboBox<ExportDataFormat> exportBox = new JComboBox<>(ExportDataFormat.values());
        exportBox.setSelectedItem(ExportDataFormat.ALL);
        JComboBox<TextEncoding> encodingBox = new JComboBox<>(TextEncoding.values());
        encodingBox.setSelectedItem(TextEncoding.UTF8);
        JComboBox<SqlDialect> dialectBox = new JComboBox<>(SqlDialect.values());
        dialectBox.setSelectedItem(SqlDialect.NONE);

        JCheckBox extractSchema = new JCheckBox("Schema + relationships", true);
        JCheckBox extractData = new JCheckBox("Table data", true);
        JCheckBox extractQueries = new JCheckBox("Saved queries", true);
        JCheckBox writeDocs = new JCheckBox("Docs + summaries", true);
        JCheckBox includeSystemObjects = new JCheckBox("Include system objects");
        JCheckBox validate = new JCheckBox("Validate extracted artifacts", true);
        JCheckBox allCrossPlatform = new JCheckBox("Select all cross-platform artifacts", true);

        JTextArea debugArea = createTextArea(false);
        JTextArea logArea = createTextArea(true);
        JTextArea sqlPreviewArea = createTextArea(false);
        JTextArea translatedSqlPreviewArea = createTextArea(false);
        JTextArea allSqlWorkbenchArea = createTextArea(false);
        JTextArea resultViewerArea = createTextArea(false);
        DefaultListModel<String> timelineModel = new DefaultListModel<>();
        JList<String> timelineList = new JList<>(timelineModel);
        JTree generatedTree = new JTree(new DefaultMutableTreeNode("No generated files yet"));
        generatedTree.setRootVisible(true);
        generatedTree.setShowsRootHandles(true);
        generatedTree.setCellRenderer(createFileTreeRenderer());
        generatedTree.setRowHeight(24);
        timelineList.setFixedCellHeight(28);
        setDebug(debugArea, """
                Stage: idle
                Waiting on: choose an Access file to preflight.
                Notes:
                - toolbar actions stay available across themes
                - result viewer loads manifest/errors/coverage after extraction
                """);
        resultViewerArea.setText("Result viewer will show manifest.json, coverage.json, and errors.json after a run.");

        JLabel coverageValue = statValue("0");
        JLabel warningsValue = statValue("0");
        JLabel errorsValue = statValue("0");
        JLabel statusBadge = new JLabel("Idle");
        JLabel preflightHeadline = new JLabel("No database scanned yet");
        JLabel preflightDetails = new JLabel("<html><div style='width:500px'>Choose an Access file, then scan it to inspect tables, saved queries, and likely database role before extraction.</div></html>");
        JLabel preflightBadge = new JLabel("Awaiting scan");

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);

        JButton inputBrowse = toolbarButton("Input", new AppIcon(AppIcon.Kind.FILE, new Color(110, 176, 255)));
        JButton outputBrowse = toolbarButton("Output", new AppIcon(AppIcon.Kind.FOLDER, new Color(240, 193, 97)));
        JButton scanButton = toolbarButton("Scan", new AppIcon(AppIcon.Kind.SEARCH, new Color(102, 214, 184)));
        JButton runButton = toolbarButton("Extract", new AppIcon(AppIcon.Kind.DATABASE, new Color(121, 170, 255)));
        JButton openOutputButton = toolbarButton("Open Output", new AppIcon(AppIcon.Kind.FOLDER, new Color(158, 173, 191)));
        JButton viewManifestButton = toolbarButton("Manifest", new AppIcon(AppIcon.Kind.FILE, new Color(110, 176, 255)));
        JButton viewCoverageButton = toolbarButton("Coverage", new AppIcon(AppIcon.Kind.FILE, new Color(110, 176, 255)));
        JButton viewErrorsButton = toolbarButton("Errors", new AppIcon(AppIcon.Kind.ERROR, new Color(241, 76, 76)));
        JButton exitButton = toolbarButton("Exit", new AppIcon(AppIcon.Kind.CLOSE, new Color(204, 204, 204)));
        JToggleButton themeToggle = new JToggleButton("Light mode", true);
        JButton sidebarToggle = toolbarButton("Sidebar", new AppIcon(AppIcon.Kind.SIDEBAR, new Color(158, 173, 191)));
        JLabel tableCountBadge = new JLabel("Tables: 0");
        JLabel queryCountBadge = new JLabel("Queries: 0");
        JLabel previewPathLabel = new JLabel("No file selected");
        JLabel breadcrumbsLabel = new JLabel("workspace");
        JLabel statusLineLabel = new JLabel("Idle");
        JButton openPreviewButton = secondaryButton("Open file");
        JButton copyPathButton = secondaryButton("Copy path");
        JButton openContainingFolderButton = secondaryButton("Open containing folder");
        JButton quickScanButton = commandButton("Scan", new AppIcon(AppIcon.Kind.SEARCH, new Color(102, 214, 184)));
        JButton quickExtractButton = commandButton("Extract", new AppIcon(AppIcon.Kind.DATABASE, new Color(121, 170, 255)));
        JButton quickOpenOutputButton = commandButton("Open Output", new AppIcon(AppIcon.Kind.FOLDER, new Color(240, 193, 97)));
        JButton quickShowSqlButton = commandButton("Show SQL", new AppIcon(AppIcon.Kind.SQL, new Color(102, 214, 184)));

        openOutputButton.setEnabled(false);
        openPreviewButton.setEnabled(false);
        copyPathButton.setEnabled(false);
        openContainingFolderButton.setEnabled(false);
        quickOpenOutputButton.setEnabled(false);

        styleChoice(extractSchema);
        styleChoice(extractData);
        styleChoice(extractQueries);
        styleChoice(writeDocs);
        styleChoice(includeSystemObjects);
        styleChoice(validate);
        styleChoice(allCrossPlatform);

        allCrossPlatform.addActionListener(e -> {
            boolean selected = allCrossPlatform.isSelected();
            extractSchema.setSelected(selected);
            extractData.setSelected(selected);
            extractQueries.setSelected(selected);
            writeDocs.setSelected(selected);
        });

        DefaultTableModel tableModel = new DefaultTableModel(
                new Object[]{"Extract", "Table", "Rows", "Columns", "Linked", "System"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0 -> Boolean.class;
                    case 2, 3 -> Integer.class;
                    case 4, 5 -> Boolean.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        JTable tableBrowser = new JTable(tableModel);
        styleDataTable(tableBrowser);
        configureSelectionTable(tableBrowser, false);

        DefaultTableModel queryModel = new DefaultTableModel(
                new Object[]{"Extract", "Query", "Type", "Params"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0 -> Boolean.class;
                    case 3 -> Integer.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        JTable queryBrowser = new JTable(queryModel);
        queryBrowser.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleDataTable(queryBrowser);
        configureSelectionTable(queryBrowser, true);

        JButton selectAllTables = secondaryButton("Select all tables");
        JButton clearTables = secondaryButton("Clear table selection");
        JButton selectAllQueries = secondaryButton("Select all queries");
        JButton clearQueries = secondaryButton("Clear query selection");
        selectAllTables.addActionListener(e -> setAllRows(tableModel, true));
        clearTables.addActionListener(e -> setAllRows(tableModel, false));
        selectAllQueries.addActionListener(e -> setAllRows(queryModel, true));
        clearQueries.addActionListener(e -> setAllRows(queryModel, false));

        Runnable refreshSelectionCounts = () -> {
            tableCountBadge.setText("Tables: " + selectedTables(tableModel).size());
            queryCountBadge.setText("Queries: " + selectedQueries(queryModel).size());
        };
        tableModel.addTableModelListener(e -> refreshSelectionCounts.run());
        queryModel.addTableModelListener(e -> refreshSelectionCounts.run());

        queryBrowser.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                refreshSqlPanels(currentScan[0], queryBrowser.getSelectedRow(), (SqlDialect) dialectBox.getSelectedItem(),
                        sqlTranslator, sqlPreviewArea, translatedSqlPreviewArea, allSqlWorkbenchArea);
            }
        });
        final Runnable[] showSqlWorkbench = new Runnable[1];
        dialectBox.addActionListener(e -> refreshSqlPanels(currentScan[0], queryBrowser.getSelectedRow(),
                (SqlDialect) dialectBox.getSelectedItem(), sqlTranslator, sqlPreviewArea, translatedSqlPreviewArea, allSqlWorkbenchArea));
        generatedTree.addTreeSelectionListener((TreeSelectionEvent e) -> {
            Object node = generatedTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof FileNode fileNode) {
                if (Files.isRegularFile(fileNode.path())) {
                    currentPreviewedFile[0] = fileNode.path();
                    updatePreviewActions(currentPreviewedFile[0], previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton);
                    updateBreadcrumbs(currentPreviewedFile[0], breadcrumbsLabel);
                    loadArtifactViewer(fileNode.path(), resultViewerArea, fileNode.path().getFileName().toString(),
                            (TextEncoding) encodingBox.getSelectedItem());
                }
            }
        });
        generatedTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Object node = generatedTree.getLastSelectedPathComponent();
                    if (node instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof FileNode fileNode) {
                        if (Files.isRegularFile(fileNode.path())) {
                            openFile(fileNode.path());
                        }
                    }
                }
            }
        });

        Runnable triggerScan = () -> {
            String inputText = inputField.getText().trim();
            if (inputText.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Choose an Access file first.",
                        "Missing input", JOptionPane.WARNING_MESSAGE);
                return;
            }

            scanButton.setEnabled(false);
            runButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Scanning database metadata...");
            statusBadge.setText("Scanning");
            statusLineLabel.setText("Scanning database metadata...");
            styleBadge(statusBadge, currentTheme[0].accentSoft(), currentTheme[0].accent(), currentTheme[0]);
            preflightHeadline.setText("Scanning " + Path.of(inputText).getFileName());
            preflightDetails.setText("<html><div style='width:500px'>Opening the Access file, enumerating tables, scanning saved queries, and checking whether this looks like a front-end shell or a data database.</div></html>");
            logArea.setText("");
            resetTimeline(timelineModel, "Preflight started");
            addTimelineEntry(timelineModel, "Open database");
            addTimelineEntry(timelineModel, "Read table inventory");
            addTimelineEntry(timelineModel, "Read query SQL");
            addTimelineEntry(timelineModel, "Classify SYS or TBL role");
            append(logArea, "SCAN: starting preflight for " + Path.of(inputText).getFileName());
            append(logArea, "SCAN: opening database container");
            append(logArea, "SCAN: enumerating visible tables and saved queries");
            setDebug(debugArea, """
                    Stage: preflight
                    Waiting on:
                    - opening database container
                    - enumerating visible tables
                    - enumerating saved queries and SQL
                    - checking likely SYS/TBL role
                    """);

            new SwingWorker<DatabaseScanResult, String>() {
                @Override
                protected DatabaseScanResult doInBackground() {
                    publish("Preflight requested for " + inputText);
                    publish("Opening database file");
                    publish("Collecting table inventory");
                    publish("Collecting saved query SQL");
                    publish("Checking likely SYS/TBL role");
                    return service.scanDatabase(Path.of(inputText), includeSystemObjects.isSelected());
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String chunk : chunks) {
                        append(debugArea, chunk);
                        append(logArea, "SCAN: " + chunk);
                    }
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    scanButton.setEnabled(true);
                    runButton.setEnabled(true);
                    try {
                        DatabaseScanResult scan = get();
                        currentScan[0] = scan;
                        lastScannedInput[0] = inputText;
                        populateTableBrowser(tableModel, scan.tables());
                        populateQueryBrowser(queryModel, scan.queries());
                        refreshSelectionCounts.run();
                        updatePreflightUi(scan, preflightHeadline, preflightDetails, preflightBadge, debugArea, sqlPreviewArea, currentTheme[0]);
                        refreshSqlPanels(scan, queryBrowser.getSelectedRow(), (SqlDialect) dialectBox.getSelectedItem(),
                                sqlTranslator, sqlPreviewArea, translatedSqlPreviewArea, allSqlWorkbenchArea);
                        addTimelineEntry(timelineModel, "Preflight complete: " + scan.tableCount() + " tables, " + scan.queryCount() + " queries");
                        statusLineLabel.setText("Preflight complete");
                        append(logArea, "SCAN: found " + scan.tableCount() + " table(s) and " + scan.queryCount() + " querie(s)");
                        if (!scan.diagnostics().isEmpty()) {
                            scan.diagnostics().forEach(diag -> append(logArea, "SCAN WARN: " + diag.message()));
                        } else {
                            append(logArea, "SCAN: completed cleanly");
                        }
                    } catch (Exception ex) {
                        preflightHeadline.setText("Preflight failed");
                        preflightDetails.setText("<html><div style='width:500px'>" + ex.getMessage() + "</div></html>");
                        styleBadge(preflightBadge, new Color(254, 237, 230), currentTheme[0].danger(), currentTheme[0]);
                        preflightBadge.setText("Scan failed");
                        append(debugArea, "Preflight failed: " + ex.getMessage());
                        append(logArea, "SCAN ERROR: " + ex.getMessage());
                        addTimelineEntry(timelineModel, "Preflight failed");
                        statusLineLabel.setText("Preflight failed");
                    }
                }
            }.execute();
        };

        inputBrowse.addActionListener(e -> {
            Path selected = choosePath(frame, JFileChooser.FILES_ONLY);
            if (selected != null) {
                inputField.setText(selected.toString());
                if (outputField.getText().isBlank()) {
                    outputField.setText(defaultOutputFor(selected).toString());
                }
                triggerScan.run();
            }
        });
        outputBrowse.addActionListener(e -> {
            Path selected = choosePath(frame, JFileChooser.DIRECTORIES_ONLY);
            if (selected != null) {
                outputField.setText(selected.toString());
            }
        });
        scanButton.addActionListener(e -> triggerScan.run());

        runButton.addActionListener(e -> {
            String inputText = inputField.getText().trim();
            String outputText = outputField.getText().trim();
            if (inputText.isEmpty() || outputText.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Choose both an Access file and an output folder.",
                        "Missing input", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (currentScan[0] == null || !inputText.equals(lastScannedInput[0])) {
                triggerScan.run();
                JOptionPane.showMessageDialog(frame,
                        "Preflight scan was stale or missing, so a fresh scan was started. Review the table/query browsers and run again.",
                        "Scan required", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if ("likely-front-end".equals(currentScan[0].classification())) {
                String suggestion = currentScan[0].suggestedAlternative() != null
                        ? "\nSuggested sibling data file: " + currentScan[0].suggestedAlternative().getFileName()
                        : "";
                int choice = JOptionPane.showConfirmDialog(frame,
                        "This input looks like a front-end SYS database. It may fail or only partially open on Linux/macOS if linked-table paths are stale."
                                + suggestion + "\n\nContinue anyway?",
                        "Front-end database warning",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.OK_OPTION) {
                    return;
                }
            }

            Set<String> selectedTables = selectedTables(tableModel);
            Set<String> selectedQueries = selectedQueries(queryModel);
            if ((extractSchema.isSelected() || extractData.isSelected()) && selectedTables.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                        "Select at least one table in the browser before extracting schema or table data.",
                        "No tables selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (extractQueries.isSelected() && selectedQueries.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                        "Select at least one query in the browser before extracting saved queries.",
                        "No queries selected", JOptionPane.WARNING_MESSAGE);
                return;
            }

            runButton.setEnabled(false);
            scanButton.setEnabled(false);
            openOutputButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Extracting artifacts...");
            statusBadge.setText("Running");
            statusLineLabel.setText("Extracting selected artifacts...");
            styleBadge(statusBadge, currentTheme[0].accentSoft(), currentTheme[0].accent(), currentTheme[0]);
            coverageValue.setText("...");
            warningsValue.setText("...");
            errorsValue.setText("...");
            logArea.setText("");
            resultViewerArea.setText("Waiting for result artifacts...");
            resetTimeline(timelineModel, "Extraction started");
            addTimelineEntry(timelineModel, "Open database");
            addTimelineEntry(timelineModel, "Export selected tables");
            addTimelineEntry(timelineModel, "Preserve saved queries");
            addTimelineEntry(timelineModel, "Write manifest and coverage");

            setDebug(debugArea, """
                    Stage: extraction
                    Waiting on:
                    - database open
                    - selected table export
                    - saved query export and SQL preservation
                    - manifest, warnings, errors, and coverage writers
                    Planned scopes:
                    - schema=%s
                    - data=%s
                    - queries=%s
                    - docs=%s
                    Selected tables: %d
                    Selected queries: %d
                    """.formatted(
                    extractSchema.isSelected(),
                    extractData.isSelected(),
                    extractQueries.isSelected(),
                    writeDocs.isSelected(),
                    selectedTables.size(),
                    selectedQueries.size()));

            append(logArea, "Preservation-first extraction started.");

            new SwingWorker<ExtractionResult, String>() {
                @Override
                protected ExtractionResult doInBackground() throws Exception {
                    ExtractionRequest request = new ExtractionRequest(
                            Path.of(inputText),
                            Path.of(outputText),
                            List.of("jrxml", "crystal"),
                            (FullFidelityMode) fidelityBox.getSelectedItem(),
                            includeSystemObjects.isSelected(),
                            (ExportDataFormat) exportBox.getSelectedItem(),
                            (TextEncoding) encodingBox.getSelectedItem(),
                            (SqlDialect) dialectBox.getSelectedItem(),
                            validate.isSelected(),
                            extractSchema.isSelected(),
                            extractData.isSelected(),
                            extractQueries.isSelected(),
                            writeDocs.isSelected(),
                            selectedTables,
                            selectedQueries);

                    publish("Input: " + request.input());
                    publish("Output: " + request.output());
                    publish("Mode: " + request.fullFidelity() + ", data: " + request.exportData() + ", SQL: " + request.translateSql());
                    publish("Selected tables: " + request.selectedTables().size());
                    publish("Selected queries: " + request.selectedQueries().size());
                    return service.extract(request);
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String chunk : chunks) {
                        append(logArea, chunk);
                        if (chunk.startsWith("Input:")) {
                            addTimelineEntry(timelineModel, "Input confirmed");
                        } else if (chunk.startsWith("Output:")) {
                            addTimelineEntry(timelineModel, "Output path ready");
                        } else if (chunk.startsWith("Selected tables:")) {
                            addTimelineEntry(timelineModel, "Table scope locked");
                        } else if (chunk.startsWith("Selected queries:")) {
                            addTimelineEntry(timelineModel, "Query scope locked");
                        }
                    }
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    runButton.setEnabled(true);
                    scanButton.setEnabled(true);
                    try {
                        ExtractionResult result = get();
                        lastOutput[0] = result.outputPath();
                        openOutputButton.setEnabled(true);
                        quickOpenOutputButton.setEnabled(true);
                        coverageValue.setText(String.valueOf(result.coverage().size()));
                        warningsValue.setText(String.valueOf(result.warnings().size()));
                        errorsValue.setText(String.valueOf(result.errors().size()));
                        append(logArea, "Completed. Output: " + result.outputPath());
                        append(debugArea, "Finished writing artifacts and refreshing in-app viewers.");
                        currentPreviewedFile[0] = result.outputPath().resolve("manifest.json");
                        updatePreviewActions(currentPreviewedFile[0], previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton);
                        updateBreadcrumbs(currentPreviewedFile[0], breadcrumbsLabel);
                        loadArtifactViewer(result.outputPath().resolve("manifest.json"), resultViewerArea, "manifest.json",
                                (TextEncoding) encodingBox.getSelectedItem());
                        populateGeneratedTree(generatedTree, result.outputPath());
                        addTimelineEntry(timelineModel, "Extraction complete");
                        statusLineLabel.setText("Extraction complete");

                        if (!result.errors().isEmpty()) {
                            statusBadge.setText("Completed with issues");
                            styleBadge(statusBadge, new Color(254, 237, 230), currentTheme[0].danger(), currentTheme[0]);
                            result.errors().forEach(error -> append(logArea, "ERROR: " + error.message()));
                            statusLineLabel.setText("Completed with errors");
                        } else if (!result.warnings().isEmpty()) {
                            statusBadge.setText("Completed with warnings");
                            styleBadge(statusBadge, new Color(255, 243, 218), currentTheme[0].warn(), currentTheme[0]);
                            result.warnings().forEach(warning -> append(logArea, "WARN: " + warning.message()));
                            statusLineLabel.setText("Completed with warnings");
                        } else {
                            statusBadge.setText("Completed cleanly");
                            styleBadge(statusBadge, currentTheme[0].accentSoft(), currentTheme[0].accent(), currentTheme[0]);
                            statusLineLabel.setText("Completed cleanly");
                        }
                    } catch (Exception ex) {
                        statusBadge.setText("Failed");
                        styleBadge(statusBadge, new Color(254, 237, 230), currentTheme[0].danger(), currentTheme[0]);
                        errorsValue.setText("1");
                        append(logArea, "Extraction failed: " + ex.getMessage());
                        append(debugArea, "Failure: " + ex.getMessage());
                        addTimelineEntry(timelineModel, "Extraction failed");
                        statusLineLabel.setText("Extraction failed");
                    }
                }
            }.execute();
        });

        openOutputButton.addActionListener(e -> {
            if (lastOutput[0] != null) {
                openDirectory(lastOutput[0]);
            }
        });
        quickOpenOutputButton.addActionListener(e -> {
            if (lastOutput[0] != null) {
                openDirectory(lastOutput[0]);
            }
        });
        viewManifestButton.addActionListener(e -> {
            if (lastOutput[0] != null) {
                currentPreviewedFile[0] = lastOutput[0].resolve("manifest.json");
                updatePreviewActions(currentPreviewedFile[0], previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton);
                updateBreadcrumbs(currentPreviewedFile[0], breadcrumbsLabel);
                loadArtifactViewer(currentPreviewedFile[0], resultViewerArea, "manifest.json",
                        (TextEncoding) encodingBox.getSelectedItem());
            }
        });
        viewCoverageButton.addActionListener(e -> {
            if (lastOutput[0] != null) {
                currentPreviewedFile[0] = lastOutput[0].resolve("coverage.json");
                updatePreviewActions(currentPreviewedFile[0], previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton);
                updateBreadcrumbs(currentPreviewedFile[0], breadcrumbsLabel);
                loadArtifactViewer(currentPreviewedFile[0], resultViewerArea, "coverage.json",
                        (TextEncoding) encodingBox.getSelectedItem());
            }
        });
        viewErrorsButton.addActionListener(e -> {
            if (lastOutput[0] != null) {
                currentPreviewedFile[0] = lastOutput[0].resolve("errors.json");
                updatePreviewActions(currentPreviewedFile[0], previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton);
                updateBreadcrumbs(currentPreviewedFile[0], breadcrumbsLabel);
                loadArtifactViewer(currentPreviewedFile[0], resultViewerArea, "errors.json",
                        (TextEncoding) encodingBox.getSelectedItem());
            }
        });
        openPreviewButton.addActionListener(e -> {
            if (currentPreviewedFile[0] != null) {
                openFile(currentPreviewedFile[0]);
            }
        });
        copyPathButton.addActionListener(e -> {
            if (currentPreviewedFile[0] != null) {
                copyToClipboard(currentPreviewedFile[0].toString());
                append(debugArea, "Copied path: " + currentPreviewedFile[0]);
            }
        });
        openContainingFolderButton.addActionListener(e -> {
            if (currentPreviewedFile[0] != null && currentPreviewedFile[0].getParent() != null) {
                openDirectory(currentPreviewedFile[0].getParent());
            }
        });
        quickScanButton.addActionListener(e -> scanButton.doClick());
        quickExtractButton.addActionListener(e -> runButton.doClick());
        quickShowSqlButton.addActionListener(e -> {
            if (showSqlWorkbench[0] != null) {
                showSqlWorkbench[0].run();
            }
        });
        exitButton.addActionListener(e -> frame.dispose());
        themeToggle.addActionListener(e -> {
            currentTheme[0] = themeToggle.isSelected() ? DARK : LIGHT;
            themeToggle.setText(themeToggle.isSelected() ? "Light mode" : "Dark mode");
            applyTheme(frame, currentTheme[0], debugArea, logArea, sqlPreviewArea, translatedSqlPreviewArea, allSqlWorkbenchArea, resultViewerArea,
                    statusBadge, preflightBadge, coverageValue, warningsValue, errorsValue, generatedTree,
                    tableCountBadge, queryCountBadge, timelineList, breadcrumbsLabel, statusLineLabel);
        });

        JButton presetEverythingButton = secondaryButton("Preset: export everything");
        JButton presetSchemaOnlyButton = secondaryButton("Preset: schema-only");
        presetEverythingButton.addActionListener(e -> {
            extractSchema.setSelected(true);
            extractData.setSelected(true);
            extractQueries.setSelected(true);
            writeDocs.setSelected(true);
            allCrossPlatform.setSelected(true);
            exportBox.setSelectedItem(ExportDataFormat.ALL);
            dialectBox.setSelectedItem(SqlDialect.NONE);
            setAllRows(tableModel, true);
            setAllRows(queryModel, true);
            append(debugArea, "Preset applied: export everything.");
        });
        presetSchemaOnlyButton.addActionListener(e -> {
            extractSchema.setSelected(true);
            extractData.setSelected(false);
            extractQueries.setSelected(false);
            writeDocs.setSelected(true);
            allCrossPlatform.setSelected(false);
            exportBox.setSelectedItem(ExportDataFormat.JSON);
            dialectBox.setSelectedItem(SqlDialect.NONE);
            setAllRows(tableModel, true);
            setAllRows(queryModel, false);
            append(debugArea, "Preset applied: schema-only.");
        });

        JPanel toolbar = createToolbar(sidebarToggle, inputBrowse, outputBrowse, scanButton, runButton,
                viewManifestButton, viewCoverageButton, viewErrorsButton, openOutputButton, themeToggle, exitButton,
                tableCountBadge, queryCountBadge, quickScanButton, quickExtractButton, quickOpenOutputButton, quickShowSqlButton);
        JPanel header = createHeader(statusBadge, breadcrumbsLabel);
        JPanel left = createLeftPanel(
                inputField, outputField, fidelityBox, exportBox, encodingBox, dialectBox,
                extractSchema, extractData, extractQueries, writeDocs, allCrossPlatform,
                includeSystemObjects, validate, preflightHeadline, preflightDetails, preflightBadge,
                progressBar, tableBrowser, queryBrowser, sqlPreviewArea, translatedSqlPreviewArea,
                selectAllTables, clearTables, selectAllQueries, clearQueries,
                presetEverythingButton, presetSchemaOnlyButton);
        JPanel right = createRightPanel(coverageValue, warningsValue, errorsValue, debugArea, logArea, allSqlWorkbenchArea,
                resultViewerArea, generatedTree, previewPathLabel, openPreviewButton, copyPathButton,
                openContainingFolderButton, timelineList, showSqlWorkbench);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        int savedDivider = PREFS.getInt("mainSplitDivider", 620);
        splitPane.setDividerLocation(savedDivider);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(10);
        final int[] lastSidebarWidth = new int[]{savedDivider};
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            int value = splitPane.getDividerLocation();
            if (value > 40) {
                lastSidebarWidth[0] = value;
                PREFS.putInt("mainSplitDivider", value);
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.CENTER);

        sidebarToggle.addActionListener(e -> {
            if (splitPane.getDividerLocation() > 40) {
                lastSidebarWidth[0] = splitPane.getDividerLocation();
                splitPane.setDividerLocation(0);
            } else {
                splitPane.setDividerLocation(lastSidebarWidth[0]);
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(splitPane, BorderLayout.CENTER);
        frame.add(createStatusBar(statusLineLabel, breadcrumbsLabel), BorderLayout.SOUTH);

        Map<String, Runnable> paletteActions = new LinkedHashMap<>();
        paletteActions.put("Scan database", () -> scanButton.doClick());
        paletteActions.put("Extract selected artifacts", () -> runButton.doClick());
        paletteActions.put("Open output folder", () -> quickOpenOutputButton.doClick());
        paletteActions.put("Show SQL workbench", () -> quickShowSqlButton.doClick());
        installShortcuts(frame.getRootPane(), paletteActions, quickScanButton, quickExtractButton, quickOpenOutputButton, quickShowSqlButton);

        refreshSelectionCounts.run();
        applyTheme(frame, currentTheme[0], debugArea, logArea, sqlPreviewArea, translatedSqlPreviewArea, allSqlWorkbenchArea, resultViewerArea,
                statusBadge, preflightBadge, coverageValue, warningsValue, errorsValue, generatedTree,
                tableCountBadge, queryCountBadge, timelineList, breadcrumbsLabel, statusLineLabel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createHeader(JLabel statusBadge, JLabel breadcrumbsLabel) {
        JPanel header = new JPanel(new BorderLayout());
        header.setName("appHeader");
        header.setBorder(new EmptyBorder(12, 20, 10, 20));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Access Unpack Studio");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        JLabel subtitle = new JLabel("Scan databases, inspect tables and SQL, then extract into a git-friendly artifact tree.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        breadcrumbsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        text.add(Box.createVerticalStrut(6));
        text.add(breadcrumbsLabel);

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(statusBadge);

        header.add(text, BorderLayout.WEST);
        header.add(badgeWrap, BorderLayout.EAST);
        return header;
    }

    private static JPanel createStatusBar(JLabel statusLineLabel, JLabel breadcrumbsLabel) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setName("statusBar");
        bar.setBorder(new EmptyBorder(6, 12, 6, 12));
        statusLineLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JLabel crumbCopy = new JLabel();
        crumbCopy.setFont(new Font("SansSerif", Font.PLAIN, 12));
        bar.add(statusLineLabel, BorderLayout.WEST);
        bar.add(crumbCopy, BorderLayout.EAST);
        breadcrumbsLabel.addPropertyChangeListener("text", e -> crumbCopy.setText("Selected: " + e.getNewValue()));
        crumbCopy.setText("Selected: " + breadcrumbsLabel.getText());
        return bar;
    }

    private static JPanel createToolbar(JButton sidebarToggle, JButton inputBrowse, JButton outputBrowse, JButton scanButton, JButton runButton,
                                        JButton viewManifestButton, JButton viewCoverageButton, JButton viewErrorsButton,
                                        JButton openOutputButton, JToggleButton themeToggle, JButton exitButton,
                                        JLabel tableCountBadge, JLabel queryCountBadge,
                                        JButton quickScanButton, JButton quickExtractButton,
                                        JButton quickOpenOutputButton, JButton quickShowSqlButton) {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setName("topToolbar");
        toolbar.setBorder(new EmptyBorder(0, 20, 10, 20));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(sidebarToggle);
        left.add(inputBrowse);
        left.add(outputBrowse);
        left.add(scanButton);
        left.add(runButton);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        JPanel docs = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        docs.setOpaque(false);
        docs.add(viewManifestButton);
        docs.add(viewCoverageButton);
        docs.add(viewErrorsButton);
        docs.add(openOutputButton);
        docs.add(tableCountBadge);
        docs.add(queryCountBadge);
        JPanel commandBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        commandBar.setOpaque(false);
        commandBar.add(quickScanButton);
        commandBar.add(quickExtractButton);
        commandBar.add(quickOpenOutputButton);
        commandBar.add(quickShowSqlButton);
        center.add(docs);
        center.add(Box.createVerticalStrut(6));
        center.add(commandBar);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(themeToggle);
        right.add(exitButton);

        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(center, BorderLayout.CENTER);
        toolbar.add(right, BorderLayout.EAST);
        return toolbar;
    }

    private static JPanel createLeftPanel(
            JTextField inputField,
            JTextField outputField,
            JComboBox<FullFidelityMode> fidelityBox,
            JComboBox<ExportDataFormat> exportBox,
            JComboBox<TextEncoding> encodingBox,
            JComboBox<SqlDialect> dialectBox,
            JCheckBox extractSchema,
            JCheckBox extractData,
            JCheckBox extractQueries,
            JCheckBox writeDocs,
            JCheckBox allCrossPlatform,
            JCheckBox includeSystemObjects,
            JCheckBox validate,
            JLabel preflightHeadline,
            JLabel preflightDetails,
            JLabel preflightBadge,
            JProgressBar progressBar,
            JTable tableBrowser,
            JTable queryBrowser,
            JTextArea sqlPreviewArea,
            JTextArea translatedSqlPreviewArea,
            JButton selectAllTables,
            JButton clearTables,
            JButton selectAllQueries,
            JButton clearQueries,
            JButton presetEverythingButton,
            JButton presetSchemaOnlyButton) {

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setName("navRail");
        wrapper.setBorder(new EmptyBorder(12, 12, 16, 8));

        JPanel setupCard = panelCard("Extraction setup");
        JPanel locationsSection = sectionBody(
                inputRow("Access file", "Pick the `.mdb` or `.accdb` you want to unpack.", inputField),
                inputRow("Output folder", "Generated artifacts are best kept outside source folders for git cleanliness.", outputField));
        JPanel engineSection = sectionBody(
                comboRow("Full fidelity", "Cross-platform parsing runs everywhere. Helper-backed recovery adds more only when available.", fidelityBox),
                comboRow("Data export", "CSV and JSON are dependable. Parquet is there for analytics workflows.", exportBox),
                comboRow("Text encoding", "Choose how CSV and SQL/text artifacts are written when you need legacy Japanese encodings.", encodingBox),
                comboRow("SQL translation", "All original Access SQL is preserved exactly; translations stay conservative.", dialectBox));
        allCrossPlatform.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel scopeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        scopeRow.setOpaque(false);
        scopeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        scopeRow.add(extractSchema);
        scopeRow.add(extractData);
        scopeRow.add(extractQueries);
        scopeRow.add(writeDocs);
        includeSystemObjects.setAlignmentX(Component.LEFT_ALIGNMENT);
        validate.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        presets.setOpaque(false);
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        presets.add(presetEverythingButton);
        presets.add(presetSchemaOnlyButton);
        JPanel extractionSection = sectionBody(
                allCrossPlatform,
                scopeRow,
                includeSystemObjects,
                validate,
                progressBar,
                presets);
        setupCard.add(collapsibleSection("Locations", locationsSection, true));
        setupCard.add(Box.createVerticalStrut(8));
        setupCard.add(collapsibleSection("Engine + export", engineSection, true));
        setupCard.add(Box.createVerticalStrut(8));
        setupCard.add(collapsibleSection("Scope + presets", extractionSection, true));
        JScrollPane setupScroll = new JScrollPane(setupCard);
        setupScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel browserCard = panelCard("Database browser");
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(false);
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.add(preflightBadge, BorderLayout.WEST);
        JPanel bannerText = new JPanel();
        bannerText.setOpaque(false);
        bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
        bannerText.add(preflightHeadline);
        bannerText.add(Box.createVerticalStrut(4));
        bannerText.add(preflightDetails);
        banner.add(bannerText, BorderLayout.CENTER);
        browserCard.add(banner);
        browserCard.add(Box.createVerticalStrut(12));

        CardLayout browserLayout = new CardLayout();
        JPanel browserCards = new JPanel(browserLayout);
        browserCards.add(tableTab(tableBrowser, selectAllTables, clearTables), "tables");
        browserCards.add(queryTab(queryBrowser, sqlPreviewArea, translatedSqlPreviewArea, selectAllQueries, clearQueries), "queries");
        JToggleButton tablesTab = workbenchButton("Tables", new AppIcon(AppIcon.Kind.TABLE, new Color(110, 176, 255)));
        JToggleButton queriesTab = workbenchButton("Queries", new AppIcon(AppIcon.Kind.SQL, new Color(102, 214, 184)));
        markAsTab(tablesTab);
        markAsTab(queriesTab);
        ButtonGroup browserGroup = new ButtonGroup();
        browserGroup.add(tablesTab);
        browserGroup.add(queriesTab);
        tablesTab.setSelected(true);
        tablesTab.addActionListener(e -> browserLayout.show(browserCards, "tables"));
        queriesTab.addActionListener(e -> browserLayout.show(browserCards, "queries"));
        JPanel browserToggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        browserToggleBar.setOpaque(false);
        browserToggleBar.add(tablesTab);
        browserToggleBar.add(queriesTab);
        browserCard.add(browserToggleBar);
        browserCard.add(Box.createVerticalStrut(10));
        browserCard.add(browserCards);
        JScrollPane browserScroll = new JScrollPane(browserCard);
        browserScroll.setBorder(BorderFactory.createEmptyBorder());

        CardLayout navLayout = new CardLayout();
        JPanel navContent = new JPanel(navLayout);
        navContent.add(setupScroll, "setup");
        navContent.add(browserScroll, "browser");

        JToggleButton setupNav = navButton("Setup", new AppIcon(AppIcon.Kind.SETTINGS, new Color(158, 173, 191)));
        JToggleButton browserNav = navButton("Browser", new AppIcon(AppIcon.Kind.EXPLORER, new Color(110, 176, 255)));
        ButtonGroup navGroup = new ButtonGroup();
        navGroup.add(setupNav);
        navGroup.add(browserNav);
        setupNav.setSelected(true);
        setupNav.addActionListener(e -> navLayout.show(navContent, "setup"));
        browserNav.addActionListener(e -> navLayout.show(navContent, "browser"));

        JPanel navRail = new JPanel();
        navRail.setLayout(new BoxLayout(navRail, BoxLayout.Y_AXIS));
        navRail.setBorder(new EmptyBorder(10, 8, 10, 8));
        navRail.add(setupNav);
        navRail.add(Box.createVerticalStrut(8));
        navRail.add(browserNav);
        navRail.add(Box.createVerticalGlue());

        wrapper.add(navRail, BorderLayout.WEST);
        wrapper.add(navContent, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel createRightPanel(JLabel coverageValue, JLabel warningsValue, JLabel errorsValue,
                                           JTextArea debugArea, JTextArea logArea, JTextArea allSqlWorkbenchArea,
                                           JTextArea resultViewerArea, JTree generatedTree,
                                           JLabel previewPathLabel, JButton openPreviewButton, JButton copyPathButton,
                                           JButton openContainingFolderButton, JList<String> timelineList,
                                           Runnable[] showSqlWorkbench) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setName("workbenchRoot");
        wrapper.setBorder(new EmptyBorder(12, 8, 16, 16));

        JPanel stats = new JPanel(new GridBagLayout());
        stats.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 10);
        stats.add(statCard("Coverage", "Tracked extraction artifacts", coverageValue), gbc);
        gbc.gridx = 1;
        stats.add(statCard("Warnings", "Partial or environment-limited output", warningsValue), gbc);
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 14, 0);
        stats.add(statCard("Errors", "Hard failures that need attention", errorsValue), gbc);

        wrapper.add(stats, BorderLayout.NORTH);
        wrapper.add(workbenchPanel(debugArea, logArea, allSqlWorkbenchArea, resultViewerArea, generatedTree,
                previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton, timelineList, showSqlWorkbench), BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel tableTab(JTable tableBrowser, JButton selectAllTables, JButton clearTables) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(selectAllTables);
        actions.add(clearTables);

        panel.add(actions, BorderLayout.NORTH);
        panel.add(new JScrollPane(tableBrowser), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel queryTab(JTable queryBrowser, JTextArea sqlPreviewArea, JTextArea translatedSqlPreviewArea,
                                   JButton selectAllQueries, JButton clearQueries) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(selectAllQueries);
        actions.add(clearQueries);

        JSplitPane sqlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                textCard("Access SQL", "Original Access SQL is preserved exactly.", sqlPreviewArea),
                textCard("Translated SQL", "Conservative preview for the selected dialect.", translatedSqlPreviewArea));
        sqlSplit.setResizeWeight(0.5);
        sqlSplit.setOneTouchExpandable(true);
        sqlSplit.setDividerSize(8);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(queryBrowser),
                sqlSplit);
        split.setResizeWeight(0.45);
        split.setOneTouchExpandable(true);
        split.setDividerSize(8);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel panelCard(String title) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 220, 208), 1),
                new EmptyBorder(16, 16, 16, 16)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);
        card.add(Box.createVerticalStrut(10));
        return card;
    }

    private static JPanel collapsibleSection(String title, JPanel content, boolean expanded) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        JToggleButton toggle = new JToggleButton();
        toggle.setSelected(expanded);
        toggle.setAction(new javax.swing.AbstractAction((expanded ? "▾ " : "▸ ") + title) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        });
        toggle.setSelected(expanded);
        toggle.setFocusPainted(false);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setBorder(new EmptyBorder(8, 10, 8, 10));
        content.setVisible(expanded);
        toggle.addActionListener(e -> {
            boolean open = toggle.isSelected();
            toggle.setText((open ? "▾ " : "▸ ") + title);
            content.setVisible(open);
            wrapper.revalidate();
        });
        wrapper.add(toggle, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel sectionBody(Component... children) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (int i = 0; i < children.length; i++) {
            body.add(children[i]);
            if (i < children.length - 1) {
                body.add(Box.createVerticalStrut(10));
            }
        }
        body.setBorder(new EmptyBorder(4, 8, 10, 8));
        return body;
    }

    private static JPanel textCard(String title, String subtitle, JTextArea area) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 220, 208), 1),
                new EmptyBorder(18, 18, 18, 18)));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        JLabel subtitleLabel = new JLabel("<html><div style='width:420px'>" + subtitle + "</div></html>");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        card.add(text, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private static JPanel treeViewerCard(JTree tree, JTextArea area, JLabel previewPathLabel,
                                         JButton openPreviewButton, JButton copyPathButton,
                                         JButton openContainingFolderButton) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree),
                resultViewerPanel(area, previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton));
        split.setResizeWeight(0.28);
        split.setOneTouchExpandable(true);
        split.setDividerSize(8);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(split, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel workbenchPanel(JTextArea debugArea, JTextArea logArea, JTextArea allSqlWorkbenchArea,
                                         JTextArea resultViewerArea, JTree generatedTree, JLabel previewPathLabel,
                                         JButton openPreviewButton, JButton copyPathButton,
                                         JButton openContainingFolderButton, JList<String> timelineList,
                                         Runnable[] showSqlWorkbench) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        CardLayout cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);
        cards.add(textCard("Problems", "State, warnings, and blockers during scan/extract.", debugArea), "problems");
        cards.add(outputWorkbench(logArea, timelineList), "output");
        cards.add(textCard("SQL Workbench", "All discovered query SQL plus conservative translation for the selected dialect.", allSqlWorkbenchArea), "sql");
        cards.add(treeViewerCard(generatedTree, resultViewerArea, previewPathLabel, openPreviewButton, copyPathButton, openContainingFolderButton), "viewer");

        JToggleButton problemsButton = workbenchButton("Problems", new AppIcon(AppIcon.Kind.WARNING, new Color(220, 163, 68)));
        JToggleButton outputButton = workbenchButton("Output", new AppIcon(AppIcon.Kind.OUTPUT, new Color(110, 176, 255)));
        JToggleButton sqlButton = workbenchButton("SQL", new AppIcon(AppIcon.Kind.SQL, new Color(102, 214, 184)));
        JToggleButton viewerButton = workbenchButton("Result Viewer", new AppIcon(AppIcon.Kind.FOLDER, new Color(158, 173, 191)));
        markAsTab(problemsButton);
        markAsTab(outputButton);
        markAsTab(sqlButton);
        markAsTab(viewerButton);
        ButtonGroup group = new ButtonGroup();
        group.add(problemsButton);
        group.add(outputButton);
        group.add(sqlButton);
        group.add(viewerButton);
        outputButton.setSelected(true);

        problemsButton.addActionListener(e -> cardLayout.show(cards, "problems"));
        outputButton.addActionListener(e -> cardLayout.show(cards, "output"));
        sqlButton.addActionListener(e -> cardLayout.show(cards, "sql"));
        viewerButton.addActionListener(e -> cardLayout.show(cards, "viewer"));
        showSqlWorkbench[0] = () -> sqlButton.doClick();

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);
        tabs.add(outputButton);
        tabs.add(problemsButton);
        tabs.add(sqlButton);
        tabs.add(viewerButton);

        wrapper.add(tabs, BorderLayout.NORTH);
        wrapper.add(cards, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel outputWorkbench(JTextArea logArea, JList<String> timelineList) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                timelineCard(timelineList),
                textCard("Output", "Live scan and extraction activity lands here.", logArea));
        split.setResizeWeight(0.34);
        split.setDividerSize(8);
        split.setOneTouchExpandable(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel timelineCard(JList<String> timelineList) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 220, 208), 1),
                new EmptyBorder(18, 18, 18, 18)));
        JLabel title = new JLabel("Scan timeline");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        JLabel subtitle = new JLabel("<html><div style='width:260px'>Pipeline milestones appear here in order so you can see what the tool is waiting on.</div></html>");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(Box.createVerticalStrut(4));
        top.add(subtitle);
        JScrollPane scrollPane = new JScrollPane(timelineList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        card.add(top, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private static JPanel resultViewerPanel(JTextArea area, JLabel previewPathLabel,
                                            JButton openPreviewButton, JButton copyPathButton,
                                            JButton openContainingFolderButton) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(228, 220, 208), 1),
                new EmptyBorder(18, 18, 18, 18)));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel("Result viewer");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        JLabel subtitleLabel = new JLabel("<html><div style='width:420px'>Browse generated files, preview text artifacts in-app, or open the selected file in your desktop environment.</div></html>");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.add(titleLabel);
        heading.add(Box.createVerticalStrut(4));
        heading.add(subtitleLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(openPreviewButton);
        actions.add(copyPathButton);
        actions.add(openContainingFolderButton);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.add(heading, BorderLayout.NORTH);
        header.add(actions, BorderLayout.CENTER);
        header.add(previewPathLabel, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        card.add(header, BorderLayout.NORTH);
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    private static JPanel statCard(String title, String subtitle, JLabel value) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel subtitleLabel = new JLabel("<html><div style='width:220px'>" + subtitle + "</div></html>");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        value.setFont(new Font("Monospaced", Font.BOLD, 34));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(value);
        card.add(Box.createVerticalStrut(8));
        card.add(subtitleLabel);
        return card;
    }

    private static JLabel statValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 28));
        return label;
    }

    private static JPanel inputRow(String title, String subtitle, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(0, 8));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JLabel hint = new JLabel("<html><div style='width:500px'>" + subtitle + "</div></html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label);
        text.add(Box.createVerticalStrut(4));
        text.add(hint);
        field.setPreferredSize(new Dimension(420, 34));
        row.add(text, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static JPanel comboRow(String title, String subtitle, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(0, 8));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JLabel hint = new JLabel("<html><div style='width:500px'>" + subtitle + "</div></html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label);
        text.add(Box.createVerticalStrut(4));
        text.add(hint);
        input.setPreferredSize(new Dimension(420, 32));
        row.add(text, BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    private static void populateTableBrowser(DefaultTableModel model, List<TableScanEntry> tables) {
        model.setRowCount(0);
        for (TableScanEntry table : tables) {
            model.addRow(new Object[]{
                    Boolean.TRUE,
                    table.name(),
                    table.rowCount() >= 0 ? table.rowCount() : null,
                    table.columnCount(),
                    table.linked(),
                    table.systemObject()
            });
        }
    }

    private static void populateQueryBrowser(DefaultTableModel model, List<QueryScanEntry> queries) {
        model.setRowCount(0);
        for (QueryScanEntry query : queries) {
            model.addRow(new Object[]{Boolean.TRUE, query.name(), query.type(), query.parameterCount()});
        }
    }

    private static void updatePreflightUi(DatabaseScanResult scan, JLabel headline, JLabel details, JLabel badge,
                                          JTextArea debugArea, JTextArea sqlPreviewArea, Theme theme) {
        headline.setText(scan.headline());
        List<String> notes = new ArrayList<>();
        notes.add(scan.recommendation());
        notes.add("Tables discovered: " + scan.tableCount() + ", queries discovered: " + scan.queryCount() + ".");
        if (!scan.diagnostics().isEmpty()) {
            notes.add("Warning: " + scan.diagnostics().get(0).message());
        }
        details.setText("<html><div style='width:500px'>" + String.join("<br/><br/>", notes) + "</div></html>");

        if ("likely-front-end".equals(scan.classification())) {
            styleBadge(badge, new Color(255, 243, 218), theme.warn(), theme);
            badge.setText("Likely SYS front-end");
        } else if ("likely-data".equals(scan.classification())) {
            styleBadge(badge, theme.accentSoft(), theme.accent(), theme);
            badge.setText("Likely TBL data file");
        } else {
            styleBadge(badge, new Color(238, 232, 221), theme.ink(), theme);
            badge.setText(scan.openable() ? "Scanned" : "Partial scan");
        }

        setDebug(debugArea, """
                Stage: preflight complete
                Openable: %s
                Classification: %s
                Tables discovered: %d
                Queries discovered: %d
                Waiting on:
                - choose table rows for extraction
                - browse saved queries and inspect SQL
                - start extraction when ready
                """.formatted(
                scan.openable(),
                scan.classification(),
                scan.tableCount(),
                scan.queryCount()));
        for (Diagnostic diagnostic : scan.diagnostics()) {
            append(debugArea, "Preflight note: " + diagnostic.message());
        }
        if (!scan.queries().isEmpty()) {
            sqlPreviewArea.setText(scan.queries().get(0).sql().isBlank() ? "-- empty SQL --" : scan.queries().get(0).sql());
            sqlPreviewArea.setCaretPosition(0);
        } else {
            sqlPreviewArea.setText("-- no saved queries discovered --");
        }
    }

    private static void loadArtifactViewer(Path file, JTextArea area, String label, TextEncoding preferredEncoding) {
        try {
            if (Files.exists(file)) {
                String fileName = file.getFileName().toString().toLowerCase();
                var charset = (fileName.endsWith(".json") || fileName.endsWith(".parquet.metadata.json"))
                        ? StandardCharsets.UTF_8
                        : preferredEncoding.charset();
                area.setText(Files.readString(file, charset));
                area.setCaretPosition(0);
            } else {
                area.setText(label + " not found at:\n" + file);
            }
        } catch (Exception e) {
            area.setText("Failed to load " + label + ":\n" + e.getMessage());
        }
    }

    private static void populateGeneratedTree(JTree tree, Path root) {
        DefaultMutableTreeNode rootNode = buildTreeNode(root);
        tree.setModel(new DefaultTreeModel(rootNode));
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        tree.setSelectionPath(new TreePath(rootNode.getPath()));
    }

    private static DefaultMutableTreeNode buildTreeNode(Path path) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileNode(path));
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                List<Path> children = stream.sorted().toList();
                for (Path child : children) {
                    node.add(buildTreeNode(child));
                }
            } catch (Exception ignored) {
            }
        }
        return node;
    }

    private static Set<String> selectedTables(DefaultTableModel model) {
        Set<String> selected = new LinkedHashSet<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                selected.add(String.valueOf(model.getValueAt(row, 1)));
            }
        }
        return selected;
    }

    private static Set<String> selectedQueries(DefaultTableModel model) {
        Set<String> selected = new LinkedHashSet<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            if (Boolean.TRUE.equals(model.getValueAt(row, 0))) {
                selected.add(String.valueOf(model.getValueAt(row, 1)));
            }
        }
        return selected;
    }

    private static void setAllRows(DefaultTableModel model, boolean value) {
        for (int row = 0; row < model.getRowCount(); row++) {
            model.setValueAt(value, row, 0);
        }
    }

    private static JTextArea createTextArea(boolean wrap) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(wrap);
        area.setWrapStyleWord(wrap);
        area.setFont(resolveUiFont(true, 13));
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        return area;
    }

    private static JButton toolbarButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(7, 10, 7, 10));
        return button;
    }

    private static JButton commandButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.putClientProperty("commandStyle", Boolean.TRUE);
        return button;
    }

    private static JToggleButton workbenchButton(String text, Icon icon) {
        JToggleButton button = new JToggleButton(text, icon);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(7, 10, 7, 10));
        return button;
    }

    private static void markAsTab(JToggleButton button) {
        button.putClientProperty("tabStyle", Boolean.TRUE);
    }

    private static JToggleButton navButton(String text, Icon icon) {
        JToggleButton button = new JToggleButton(text, icon);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(JToggleButton.LEFT);
        button.setMaximumSize(new Dimension(120, 38));
        button.setBorder(new EmptyBorder(8, 10, 8, 10));
        return button;
    }

    private static void styleChoice(JCheckBox box) {
        box.setOpaque(false);
    }

    private static void styleDataTable(JTable table) {
        table.setRowHeight(28);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setFont(resolveUiFont(false, 13));
        table.getTableHeader().setReorderingAllowed(false);
        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
        header.setFont(resolveUiFont(false, 12).deriveFont(Font.BOLD));
        DefaultTableCellRenderer zebra = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    component.setBackground(row % 2 == 0 ? new Color(35, 37, 41) : new Color(30, 32, 36));
                    component.setForeground(new Color(212, 212, 212));
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return component;
            }
        };
        zebra.setOpaque(true);
        table.setDefaultRenderer(String.class, zebra);
        table.setDefaultRenderer(Integer.class, zebra);
        table.setDefaultRenderer(Object.class, zebra);
        JCheckBox checkbox = new JCheckBox();
        checkbox.setHorizontalAlignment(SwingConstants.CENTER);
        checkbox.setOpaque(true);
        checkbox.setBorder(new EmptyBorder(0, 0, 0, 0));
        table.getColumnModel().getColumn(0).setCellRenderer((t, value, isSelected, hasFocus, row, column) -> {
            checkbox.setSelected(Boolean.TRUE.equals(value));
            checkbox.setBackground(isSelected
                    ? new Color(21, 57, 84)
                    : (row % 2 == 0 ? new Color(35, 37, 41) : new Color(30, 32, 36)));
            checkbox.setForeground(new Color(212, 212, 212));
            return checkbox;
        });
        JCheckBox editorCheck = new JCheckBox();
        editorCheck.setHorizontalAlignment(SwingConstants.CENTER);
        editorCheck.setOpaque(true);
        editorCheck.setBackground(new Color(21, 57, 84));
        table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(editorCheck));
    }

    private static void configureSelectionTable(JTable table, boolean queryTable) {
        table.getColumnModel().getColumn(0).setPreferredWidth(58);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(0).setMinWidth(58);
        table.getColumnModel().getColumn(1).setPreferredWidth(queryTable ? 470 : 320);
        table.getColumnModel().getColumn(2).setPreferredWidth(queryTable ? 130 : 90);
        if (queryTable) {
            table.getColumnModel().getColumn(3).setPreferredWidth(80);
        } else {
            table.getColumnModel().getColumn(3).setPreferredWidth(90);
            table.getColumnModel().getColumn(4).setPreferredWidth(70);
            table.getColumnModel().getColumn(5).setPreferredWidth(70);
        }
    }

    private static Font resolveUiFont(boolean monospaced, int size) {
        String[] candidates = monospaced
                ? new String[]{"Noto Sans Mono CJK JP", "Noto Sans Mono", "IPAGothic", "VL Gothic", "MS Gothic", Font.MONOSPACED}
                : new String[]{"Noto Sans CJK JP", "Noto Sans JP", "IPAGothic", "VL Gothic", "Yu Gothic UI", Font.SANS_SERIF};
        var available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : candidates) {
            if (available.contains(family)) {
                return new Font(family, Font.PLAIN, size);
            }
        }
        return new Font(monospaced ? Font.MONOSPACED : Font.SANS_SERIF, Font.PLAIN, size);
    }

    private static void styleBadge(JLabel label, Color background, Color foreground, Theme theme) {
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setBorder(new EmptyBorder(8, 14, 8, 14));
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
    }

    private static void setDebug(JTextArea area, String text) {
        area.setText(text);
        area.setCaretPosition(area.getDocument().getLength());
    }

    private static void append(JTextArea area, String text) {
        area.append(text + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    private static Path choosePath(JFrame frame, int mode) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(mode);
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    private static Path defaultOutputFor(Path selectedInput) {
        String stem = selectedInput.getFileName().toString().replaceAll("\\.[^.]+$", "");
        return selectedInput.getParent().resolve("generated-runs").resolve(stem);
    }

    private static void openDirectory(Path path) {
        try {
            if (Files.exists(path) && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    private static void openFile(Path path) {
        try {
            if (Files.exists(path) && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    private static void copyToClipboard(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private static void updatePreviewActions(Path path, JLabel previewPathLabel, JButton openPreviewButton,
                                             JButton copyPathButton, JButton openContainingFolderButton) {
        if (path == null) {
            previewPathLabel.setText("No file selected");
            openPreviewButton.setEnabled(false);
            copyPathButton.setEnabled(false);
            openContainingFolderButton.setEnabled(false);
            return;
        }
        previewPathLabel.setText(path.toString());
        boolean exists = Files.exists(path);
        openPreviewButton.setEnabled(exists);
        copyPathButton.setEnabled(true);
        openContainingFolderButton.setEnabled(path.getParent() != null && Files.exists(path.getParent()));
    }

    private static void updateBreadcrumbs(Path path, JLabel breadcrumbsLabel) {
        if (path == null) {
            breadcrumbsLabel.setText("workspace");
            return;
        }
        List<String> names = new ArrayList<>();
        Path current = path;
        int maxParts = 5;
        while (current != null && maxParts-- > 0) {
            if (current.getFileName() != null) {
                names.add(current.getFileName().toString());
            }
            current = current.getParent();
        }
        names = names.stream().filter(s -> !s.isBlank()).toList();
        java.util.Collections.reverse(names);
        breadcrumbsLabel.setText(String.join("  >  ", names));
    }

    private static void resetTimeline(DefaultListModel<String> model, String firstEntry) {
        model.clear();
        model.addElement("01  " + firstEntry);
    }

    private static void addTimelineEntry(DefaultListModel<String> model, String message) {
        int next = model.getSize() + 1;
        model.addElement(String.format("%02d  %s", next, message));
    }

    private static void installShortcuts(JRootPane rootPane, Map<String, Runnable> paletteActions,
                                         JButton quickScanButton, JButton quickExtractButton,
                                         JButton quickOpenOutputButton, JButton quickShowSqlButton) {
        var inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var actionMap = rootPane.getActionMap();
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("control shift S"), "scan");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("control ENTER"), "extract");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("control O"), "openOutput");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("control L"), "showSql");
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("control shift P"), "palette");
        actionMap.put("scan", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quickScanButton.doClick();
            }
        });
        actionMap.put("extract", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quickExtractButton.doClick();
            }
        });
        actionMap.put("openOutput", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quickOpenOutputButton.doClick();
            }
        });
        actionMap.put("showSql", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                quickShowSqlButton.doClick();
            }
        });
        actionMap.put("palette", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openCommandPalette(rootPane, paletteActions);
            }
        });
    }

    private static void openCommandPalette(JRootPane rootPane, Map<String, Runnable> paletteActions) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(rootPane), "Command Palette");
        dialog.setModal(true);
        dialog.setSize(520, 360);
        dialog.setLocationRelativeTo(rootPane);
        JTextField filterField = new JTextField();
        DefaultListModel<String> model = new DefaultListModel<>();
        paletteActions.keySet().forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refresh() {
                String filter = filterField.getText().trim().toLowerCase();
                model.clear();
                paletteActions.keySet().stream()
                        .filter(name -> name.toLowerCase().contains(filter))
                        .forEach(model::addElement);
                if (!model.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refresh();
            }
        });
        Runnable invokeSelection = () -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                dialog.dispose();
                paletteActions.get(selected).run();
            }
        };
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    invokeSelection.run();
                }
            }
        });
        filterField.addActionListener(e -> invokeSelection.run());
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(filterField, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.getRootPane().setDefaultButton(null);
        SwingUtilities.invokeLater(filterField::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private static DefaultTreeCellRenderer createFileTreeRenderer() {
        FileSystemView fileSystemView = FileSystemView.getFileSystemView();
        return new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof FileNode fileNode) {
                    Path path = fileNode.path();
                    if (Files.exists(path)) {
                        setIcon(fileSystemView.getSystemIcon(path.toFile()));
                    } else if (Files.isDirectory(path)) {
                        setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    } else {
                        setIcon(UIManager.getIcon("FileView.fileIcon"));
                    }
                    setBorder(new EmptyBorder(4, 6, 4, 6));
                }
                return component;
            }
        };
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private static void applyTheme(JFrame frame, Theme theme, JTextArea debugArea, JTextArea logArea,
                                   JTextArea sqlPreviewArea, JTextArea translatedSqlPreviewArea, JTextArea allSqlWorkbenchArea,
                                   JTextArea resultViewerArea, JLabel statusBadge,
                                   JLabel preflightBadge, JLabel coverageValue, JLabel warningsValue, JLabel errorsValue,
                                   JTree generatedTree, JLabel tableCountBadge, JLabel queryCountBadge,
                                   JList<String> timelineList, JLabel breadcrumbsLabel, JLabel statusLineLabel) {
        themeComponent(frame.getContentPane(), theme);
        frame.getContentPane().setBackground(theme.paper());
        debugArea.setBackground(theme.textSurface());
        logArea.setBackground(theme.textSurface());
        sqlPreviewArea.setBackground(theme.textSurface());
        translatedSqlPreviewArea.setBackground(theme.textSurface());
        allSqlWorkbenchArea.setBackground(theme.textSurface());
        resultViewerArea.setBackground(theme.textSurface());
        debugArea.setForeground(theme.ink());
        logArea.setForeground(theme.ink());
        sqlPreviewArea.setForeground(theme.ink());
        translatedSqlPreviewArea.setForeground(theme.ink());
        allSqlWorkbenchArea.setForeground(theme.ink());
        resultViewerArea.setForeground(theme.ink());
        coverageValue.setForeground(theme.ink());
        warningsValue.setForeground(theme.ink());
        errorsValue.setForeground(theme.ink());
        tableCountBadge.setForeground(theme.ink());
        queryCountBadge.setForeground(theme.ink());
        breadcrumbsLabel.setForeground(theme.ink());
        statusLineLabel.setForeground(theme.ink());
        generatedTree.setBackground(theme.surface());
        generatedTree.setForeground(theme.ink());
        timelineList.setBackground(theme.surface());
        timelineList.setForeground(theme.ink());
        timelineList.setSelectionBackground(theme.accentSoft());
        timelineList.setSelectionForeground(theme.ink());
        styleBadge(statusBadge, theme.accentSoft(), theme.accent(), theme);
        styleBadge(preflightBadge, new Color(238, 232, 221), theme.ink(), theme);
        SwingUtilities.updateComponentTreeUI(frame);
    }

    private static void themeComponent(Component component, Theme theme) {
        if (component instanceof JPanel panel) {
            if ("appHeader".equals(panel.getName()) || "topToolbar".equals(panel.getName())) {
                panel.setBackground(theme.textSurface());
            } else if ("statusBar".equals(panel.getName())) {
                panel.setBackground(theme.surface());
            } else if ("navRail".equals(panel.getName())) {
                panel.setBackground(theme.paper());
            } else if ("workbenchRoot".equals(panel.getName())) {
                panel.setBackground(theme.paper());
            } else {
                panel.setBackground(theme.paper());
            }
            panel.setForeground(theme.ink());
        }
        if (component instanceof JSplitPane splitPane) {
            splitPane.setBackground(theme.paper());
            splitPane.setForeground(theme.line());
        }
        if (component instanceof JTable table) {
            table.setBackground(theme.surface());
            table.setForeground(theme.ink());
            table.setGridColor(theme.line());
            table.setSelectionBackground(theme.accentSoft());
            table.setSelectionForeground(theme.ink());
            table.getTableHeader().setBackground(theme.textSurface());
            table.getTableHeader().setForeground(theme.ink());
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                    if (!isSelected) {
                        c.setBackground(row % 2 == 0 ? theme.surface() : theme.textSurface());
                        c.setForeground(theme.ink());
                    }
                    setBorder(new EmptyBorder(0, 8, 0, 8));
                    return c;
                }
            };
            table.setDefaultRenderer(String.class, renderer);
            table.setDefaultRenderer(Integer.class, renderer);
            table.setDefaultRenderer(Object.class, renderer);
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(theme.surface());
        }
        if (component instanceof JList<?> list) {
            list.setBackground(theme.surface());
            list.setForeground(theme.ink());
            list.setSelectionBackground(theme.accentSoft());
            list.setSelectionForeground(theme.ink());
        }
        if (component instanceof JButton button) {
            boolean command = Boolean.TRUE.equals(button.getClientProperty("commandStyle"));
            button.setBackground(command ? theme.textSurface() : theme.surface());
            button.setForeground(theme.ink());
            button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        }
        if (component instanceof JToggleButton toggle) {
            boolean tabStyle = Boolean.TRUE.equals(toggle.getClientProperty("tabStyle"));
            toggle.setBackground(theme.surface());
            toggle.setForeground(theme.ink());
            toggle.setFont(new Font("SansSerif", Font.PLAIN, 12));
            if (toggle.isSelected()) {
                toggle.setBackground(tabStyle ? theme.textSurface() : theme.accentSoft());
                toggle.setBorder(tabStyle
                        ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, theme.accent()), new EmptyBorder(7, 10, 5, 10))
                        : new EmptyBorder(7, 10, 7, 10));
            } else if (tabStyle) {
                toggle.setBorder(new EmptyBorder(7, 10, 7, 10));
            }
        }
        if (component instanceof JLabel label) {
            if (!label.isOpaque()) {
                label.setForeground(theme.ink());
            }
        }
        if (component instanceof JTextField field) {
            field.setBackground(theme.surface());
            field.setForeground(theme.ink());
            field.setCaretColor(theme.ink());
        }
        if (component instanceof JComboBox<?> combo) {
            combo.setBackground(theme.surface());
            combo.setForeground(theme.ink());
        }
        if (component instanceof JCheckBox box) {
            box.setBackground(theme.paper());
            box.setForeground(theme.ink());
        }
        if (component instanceof JTabbedPane tabs) {
            tabs.setBackground(theme.paper());
            tabs.setForeground(theme.ink());
        }
        if (component instanceof JProgressBar bar) {
            bar.setBackground(theme.surface());
            bar.setForeground(theme.accent());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                themeComponent(child, theme);
            }
        }
    }

    private static void refreshSqlPanels(DatabaseScanResult scan, int selectedRow, SqlDialect dialect,
                                         SqlTranslator translator, JTextArea rawArea, JTextArea translatedArea,
                                         JTextArea allSqlArea) {
        if (scan == null || scan.queries().isEmpty()) {
            rawArea.setText("-- no saved queries discovered --");
            translatedArea.setText("-- no translated SQL available --");
            allSqlArea.setText("-- scan a database to view all preserved SQL and translations --");
            return;
        }

        int safeRow = selectedRow >= 0 && selectedRow < scan.queries().size() ? selectedRow : 0;
        QueryScanEntry query = scan.queries().get(safeRow);
        String rawSql = query.sql().isBlank() ? "-- empty SQL --" : query.sql();
        rawArea.setText(rawSql);
        rawArea.setCaretPosition(0);

        if (dialect == null || dialect == SqlDialect.NONE) {
            translatedArea.setText("-- choose a SQL translation target to preview translated SQL --");
        } else {
            SqlTranslator.TranslationResult translated = translator.translate(query.sql(), dialect);
            StringBuilder builder = new StringBuilder();
            builder.append("-- dialect: ").append(dialect.name().toLowerCase()).append('\n');
            builder.append("-- confidence: ").append(String.format("%.2f", translated.confidence())).append("\n\n");
            if (translated.sql() != null) {
                builder.append(translated.sql());
            }
            if (!translated.warnings().isEmpty()) {
                builder.append("\n\n-- warnings --\n");
                translated.warnings().forEach(warning -> builder.append("- ").append(warning).append('\n'));
            }
            translatedArea.setText(builder.toString().trim());
        }
        translatedArea.setCaretPosition(0);

        StringBuilder allSql = new StringBuilder();
        allSql.append("All query SQL");
        allSql.append("\nDialect: ").append(dialect == null ? "none" : dialect.name().toLowerCase());
        allSql.append("\nQuery count: ").append(scan.queries().size()).append("\n\n");
        for (QueryScanEntry entry : scan.queries()) {
            allSql.append("===== ").append(entry.name()).append(" =====\n");
            allSql.append("[Access SQL]\n");
            allSql.append(entry.sql().isBlank() ? "-- empty SQL --" : entry.sql()).append("\n\n");
            if (dialect != null && dialect != SqlDialect.NONE) {
                SqlTranslator.TranslationResult translated = translator.translate(entry.sql(), dialect);
                allSql.append("[Translated ").append(dialect.name().toLowerCase()).append("]\n");
                allSql.append(translated.sql() == null || translated.sql().isBlank() ? "-- no translation --" : translated.sql()).append("\n");
                if (!translated.warnings().isEmpty()) {
                    allSql.append("[Warnings]\n");
                    translated.warnings().forEach(warning -> allSql.append("- ").append(warning).append('\n'));
                }
                allSql.append("[Confidence] ").append(String.format("%.2f", translated.confidence())).append("\n");
            } else {
                allSql.append("[Translated SQL]\n-- choose a SQL translation target to preview translated SQL --\n");
            }
            allSql.append("\n");
        }
        allSqlArea.setText(allSql.toString().trim());
        allSqlArea.setCaretPosition(0);
    }

    private record Theme(
            String name,
            Color paper,
            Color surface,
            Color ink,
            Color accent,
            Color accentSoft,
            Color warn,
            Color danger,
            Color line,
            Color textSurface,
            Color header) {
    }

    private record FileNode(Path path) {
        @Override
        public String toString() {
            return path.getFileName() != null ? path.getFileName().toString() : path.toString();
        }
    }

    private static final class AppIcon implements Icon {
        private final Kind kind;
        private final Color color;

        private AppIcon(Kind kind, Color color) {
            this.kind = kind;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            switch (kind) {
                case FILE -> {
                    g2.drawRect(3, 2, 10, 13);
                    g2.drawLine(10, 2, 13, 5);
                    g2.drawLine(10, 2, 10, 5);
                    g2.drawLine(10, 5, 13, 5);
                }
                case FOLDER -> {
                    g2.drawRect(2, 5, 13, 9);
                    g2.drawLine(2, 5, 5, 2);
                    g2.drawLine(5, 2, 9, 2);
                    g2.drawLine(9, 2, 11, 5);
                }
                case SEARCH -> {
                    g2.drawOval(2, 2, 9, 9);
                    g2.drawLine(9, 9, 14, 14);
                }
                case DATABASE -> {
                    g2.drawOval(2, 2, 12, 4);
                    g2.drawLine(2, 4, 2, 12);
                    g2.drawLine(14, 4, 14, 12);
                    g2.drawOval(2, 10, 12, 4);
                    g2.drawLine(2, 8, 14, 8);
                }
                case ERROR -> {
                    g2.drawOval(1, 1, 14, 14);
                    g2.drawLine(5, 5, 11, 11);
                    g2.drawLine(11, 5, 5, 11);
                }
                case CLOSE -> {
                    g2.drawLine(3, 3, 13, 13);
                    g2.drawLine(13, 3, 3, 13);
                }
                case SIDEBAR -> {
                    g2.drawRect(2, 2, 12, 12);
                    g2.drawLine(6, 2, 6, 14);
                }
                case WARNING -> {
                    g2.drawLine(8, 2, 14, 14);
                    g2.drawLine(8, 2, 2, 14);
                    g2.drawLine(2, 14, 14, 14);
                    g2.fillRect(7, 6, 2, 4);
                    g2.fillRect(7, 11, 2, 2);
                }
                case OUTPUT -> {
                    g2.drawRect(2, 3, 12, 10);
                    g2.drawLine(4, 6, 8, 6);
                    g2.drawLine(4, 8, 12, 8);
                    g2.drawLine(4, 10, 10, 10);
                }
                case SQL -> {
                    g2.drawLine(2, 5, 6, 8);
                    g2.drawLine(6, 8, 2, 11);
                    g2.drawLine(14, 5, 10, 8);
                    g2.drawLine(10, 8, 14, 11);
                    g2.drawLine(8, 4, 8, 12);
                }
                case SETTINGS -> {
                    g2.drawOval(5, 5, 6, 6);
                    g2.drawLine(8, 1, 8, 4);
                    g2.drawLine(8, 12, 8, 15);
                    g2.drawLine(1, 8, 4, 8);
                    g2.drawLine(12, 8, 15, 8);
                    g2.drawLine(3, 3, 5, 5);
                    g2.drawLine(11, 11, 13, 13);
                    g2.drawLine(11, 5, 13, 3);
                    g2.drawLine(3, 13, 5, 11);
                }
                case EXPLORER -> {
                    g2.drawRect(2, 2, 12, 12);
                    g2.drawLine(6, 2, 6, 14);
                    g2.drawLine(2, 6, 14, 6);
                    g2.fillRect(3, 3, 2, 2);
                    g2.fillRect(8, 3, 2, 2);
                    g2.fillRect(3, 8, 2, 2);
                }
                case TABLE -> {
                    g2.drawRect(2, 3, 12, 10);
                    g2.drawLine(2, 6, 14, 6);
                    g2.drawLine(2, 9, 14, 9);
                    g2.drawLine(6, 3, 6, 13);
                    g2.drawLine(10, 3, 10, 13);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        private enum Kind {
            FILE, FOLDER, SEARCH, DATABASE, ERROR, CLOSE, SIDEBAR, WARNING, OUTPUT, SQL, SETTINGS, EXPLORER, TABLE
        }
    }
}
