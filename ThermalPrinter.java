package pos;

import pos.EventBus.EventType;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.util.Arrays;
import java.util.List;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;


    private static final Color CLR_BG        = new Color(245, 247, 250);
    private static final Color CLR_PRIMARY   = new Color(30,  87, 153);
    private static final Color CLR_ACCENT    = new Color(0,  168, 107);
    private static final Color CLR_DANGER    = new Color(204, 51,  51);
    private static final Color CLR_WARNING   = new Color(230, 126,  34);
    private static final Color CLR_WHITE     = Color.WHITE;
    private static final Color CLR_HEADER_FG = Color.WHITE;
    private static final Color CLR_ROW_ALT   = new Color(235, 242, 253);
    private static final Color CLR_STATUS_BG = new Color(52,  73,  94);

    // ── Domain objects ────────────────────────────────────────────────────────
    private final Inventory      inventory;
    private final Cart           cart;
    private final EventBus       bus;
    private final POSController  controller;

    // ── Catalog panel widgets ─────────────────────────────────────────────────
    private JTextField          searchField;
    private JTable              catalogTable;
    private DefaultTableModel   catalogModel;
    private JTextField          qtyField;
    private JComboBox<String>   categoryFilter;

    // ── Cart panel widgets ────────────────────────────────────────────────────
    private JTable              cartTable;
    private DefaultTableModel   cartModel;
    private JLabel              lblSubtotal, lblDiscount, lblTax, lblTotal;
    private JTextField          discountField;
    private JTextField          tenderedField;
    private JComboBox<String>   paymentMethodBox;

    // ── Status bar ────────────────────────────────────────────────────────────
    private JLabel statusBar;

    // ── Timer for real-time clock ─────────────────────────────────────────────
    private Timer clockTimer;

    // ── Thermal printer state ─────────────────────────────────────────────────
    private JTextArea receiptArea     = new JTextArea();   // holds last receipt text
    private String    savedPrinterName = null;              // remembered for session

    // =========================================================================
    //  Construction
    // =========================================================================

    public POSApp() {
        inventory  = Inventory.createDefault();
        cart       = new Cart();
        bus        = new EventBus();
        controller = new POSController(inventory, cart, bus);

        configureFrame();
        buildUI();
        wireEventHandlers();
        startClock();

        refreshCatalog("");
        setVisible(true);
    }

    // ── Frame setup ───────────────────────────────────────────────────────────

    private void configureFrame() {
        setTitle("Bonnie's — POS System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(CLR_BG);
    }

    // =========================================================================
    //  UI Construction
    // =========================================================================

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CLR_PRIMARY);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("☕  Bonnie's  —  Point of Sale");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(CLR_HEADER_FG);

        JLabel clock = new JLabel();
        clock.setFont(new Font("Monospaced", Font.PLAIN, 14));
        clock.setForeground(new Color(180, 210, 255));
        clock.setName("clock");

        // Store reference for the timer
        this.clockTimer = new Timer(1000, e -> {
            clock.setText(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE  dd MMM yyyy  HH:mm:ss")));
        });

        header.add(title, BorderLayout.WEST);
        header.add(clock, BorderLayout.EAST);
        return header;
    }

    // ── Main split panel ──────────────────────────────────────────────────────

    private JSplitPane buildMainPanel() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(620);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setBackground(CLR_BG);
        return split;
    }

    // =========================================================================
    //  LEFT PANEL — product catalog
    // =========================================================================

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(CLR_BG);
        panel.setBorder(new EmptyBorder(10, 10, 10, 5));

        panel.add(buildSearchBar(),     BorderLayout.NORTH);
        panel.add(buildCatalogTable(),  BorderLayout.CENTER);
        panel.add(buildAddToCartBar(),  BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBackground(CLR_BG);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search products…");
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 200), 1, true),
                new EmptyBorder(6, 10, 6, 10)));

        // Category filter dropdown
        categoryFilter = new JComboBox<>();
        categoryFilter.addItem("All Categories");
        inventory.getCategories().forEach(categoryFilter::addItem);
        categoryFilter.setFont(new Font("SansSerif", Font.PLAIN, 13));
        categoryFilter.setPreferredSize(new Dimension(160, 34));

        JButton btnSearch = styledButton("🔍 Search", CLR_PRIMARY, CLR_WHITE);

        bar.add(searchField,    BorderLayout.CENTER);
        bar.add(categoryFilter, BorderLayout.WEST);
        bar.add(btnSearch,      BorderLayout.EAST);

        // ── Event: search button click ─────────────────────────────────────
        btnSearch.addActionListener(e -> {
            String term = searchField.getText().trim();
            controller.handleSearch(term);
        });

        // ── Event: Enter key in search field ──────────────────────────────
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    controller.handleSearch(searchField.getText().trim());
                }
            }
        });

        // ── Event: category filter change ──────────────────────────────────
        categoryFilter.addActionListener(e -> {
            String selected = (String) categoryFilter.getSelectedItem();
            if ("All Categories".equals(selected)) {
                refreshCatalog(searchField.getText().trim());
            } else {
                showProductsInTable(inventory.getByCategory(selected));
            }
        });

        return bar;
    }

    private JScrollPane buildCatalogTable() {
        String[] cols = {"ID", "Product Name", "Category", "Price", "Stock", "Tax"};
        catalogModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        catalogTable = new JTable(catalogModel);
        styleTable(catalogTable);

        // Column widths
        int[] widths = {70, 200, 110, 70, 55, 45};
        for (int i = 0; i < widths.length; i++) {
            catalogTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // ── Event: double-click to auto-fill product ID ────────────────────
        catalogTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = catalogTable.getSelectedRow();
                    if (row >= 0) {
                        String id = (String) catalogModel.getValueAt(row, 0);
                        // Pre-fill the ID field in the add-to-cart bar
                        // (the field is accessible via a named component search)
                        fillProductIdField(id);
                        setStatus("Selected: " + catalogModel.getValueAt(row, 1)
                                + "  —  Double-click again or press Add", CLR_PRIMARY);
                    }
                }
            }
        });

        // ── Event: hover effect via selection listener ─────────────────────
        catalogTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && catalogTable.getSelectedRow() >= 0) {
                int row = catalogTable.getSelectedRow();
                String name = (String) catalogModel.getValueAt(row, 1);
                setStatus("Tip: Double-click a row to select  ›  " + name, CLR_PRIMARY);
            }
        });

        JScrollPane scroll = new JScrollPane(catalogTable);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 210), 1, true),
                "  Product Catalog  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13), CLR_PRIMARY));
        return scroll;
    }

    private JPanel buildAddToCartBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(CLR_BG);
        bar.setBorder(new EmptyBorder(4, 0, 4, 0));

        JLabel lblId  = new JLabel("Product ID:");
        JTextField idField = new JTextField(8);
        idField.setName("productIdField");
        idField.setFont(new Font("Monospaced", Font.PLAIN, 14));

        JLabel lblQty = new JLabel("Qty:");
        qtyField = new JTextField("1", 4);
        qtyField.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton btnAdd    = styledButton("➕ Add to Cart", CLR_ACCENT, CLR_WHITE);
        JButton btnRemove = styledButton("➖ Remove",      CLR_WARNING, CLR_WHITE);
        JButton btnClear  = styledButton("🗑 Clear Cart",  CLR_DANGER,  CLR_WHITE);

        bar.add(lblId); bar.add(idField);
        bar.add(lblQty); bar.add(qtyField);
        bar.add(btnAdd); bar.add(btnRemove); bar.add(btnClear);

        // ── Event: Add button click ────────────────────────────────────────
        btnAdd.addActionListener(e ->
                controller.handleAddItem(idField.getText(), qtyField.getText()));

        // ── Event: Remove button click ─────────────────────────────────────
        btnRemove.addActionListener(e ->
                controller.handleRemoveItem(idField.getText(), qtyField.getText()));

        // ── Event: Clear cart button click ────────────────────────────────
        btnClear.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Clear the entire cart?", "Confirm Clear",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) controller.handleClearCart();
        });

        // ── Event: Enter key in ID field ──────────────────────────────────
        idField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    controller.handleAddItem(idField.getText(), qtyField.getText());
            }
        });

        // ── Event: Enter key in Qty field ─────────────────────────────────
        qtyField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    controller.handleAddItem(idField.getText(), qtyField.getText());
            }
        });

        return bar;
    }

    // =========================================================================
    //  RIGHT PANEL — cart + checkout
    // =========================================================================

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(CLR_BG);
        panel.setBorder(new EmptyBorder(10, 5, 10, 10));

        panel.add(buildCartTable(),    BorderLayout.CENTER);
        panel.add(buildCheckoutPanel(), BorderLayout.SOUTH);

        return panel;
    }

    private JScrollPane buildCartTable() {
        String[] cols = {"ID", "Product", "Price", "Qty", "Line Total"};
        cartModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        cartTable = new JTable(cartModel);
        styleTable(cartTable);

        int[] widths = {70, 200, 80, 50, 90};
        for (int i = 0; i < widths.length; i++) {
            cartTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JScrollPane scroll = new JScrollPane(cartTable);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 210), 1, true),
                "  Shopping Cart  ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13), CLR_ACCENT));
        return scroll;
    }

    private JPanel buildCheckoutPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(CLR_BG);
        outer.add(buildSummaryPanel(),  BorderLayout.NORTH);
        outer.add(buildPaymentPanel(),  BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 6, 4));
        panel.setBackground(CLR_WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 220), 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        lblSubtotal = summaryLabel("$0.00");
        lblDiscount = summaryLabel("$0.00");
        lblTax      = summaryLabel("$0.00");
        lblTotal    = summaryLabel("$0.00");
        lblTotal.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblTotal.setForeground(CLR_PRIMARY);

        panel.add(sectionLabel("Subtotal:"));          panel.add(lblSubtotal);
        panel.add(sectionLabel("Discount:"));          panel.add(lblDiscount);
        panel.add(sectionLabel("Tax (8.5%):"));        panel.add(lblTax);
        panel.add(new JSeparator());                   panel.add(new JSeparator());
        panel.add(sectionLabel("GRAND TOTAL:"));       panel.add(lblTotal);

        return panel;
    }

    private JPanel buildPaymentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CLR_WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 220), 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 4, 4, 8);
        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets  = new Insets(4, 0, 4, 4);

        // Discount row
        lc.gridy = 0; fc.gridy = 0;
        lc.gridx = 0; fc.gridx = 1;
        discountField = new JTextField("0", 6);
        discountField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JButton btnDiscount = styledButton("Apply %", CLR_WARNING, CLR_WHITE);
        JPanel discRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        discRow.setBackground(CLR_WHITE);
        discRow.add(discountField); discRow.add(btnDiscount);
        panel.add(new JLabel("Discount %:"), lc);
        panel.add(discRow, fc);

        // Tendered row
        lc.gridy = 1; fc.gridy = 1;
        tenderedField = new JTextField("0.00", 10);
        tenderedField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        panel.add(new JLabel("Tendered ($):"), lc);
        panel.add(tenderedField, fc);

        // Payment method row
        lc.gridy = 2; fc.gridy = 2;
        paymentMethodBox = new JComboBox<>(new String[]{"Cash", "Credit Card", "Debit Card", "Mobile Pay"});
        paymentMethodBox.setFont(new Font("SansSerif", Font.PLAIN, 13));
        panel.add(new JLabel("Payment Method:"), lc);
        panel.add(paymentMethodBox, fc);

        // Checkout button (full width)
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = 3; bc.gridx = 0; bc.gridwidth = 2;
        bc.fill = GridBagConstraints.HORIZONTAL;
        bc.insets = new Insets(10, 4, 4, 4);
        JButton btnCheckout = styledButton("💳  CHECKOUT", CLR_PRIMARY, CLR_WHITE);
        btnCheckout.setFont(new Font("SansSerif", Font.BOLD, 16));
        btnCheckout.setPreferredSize(new Dimension(0, 46));
        panel.add(btnCheckout, bc);

        // ── Event: Apply discount button ───────────────────────────────────
        btnDiscount.addActionListener(e ->
                controller.handleApplyDiscount(discountField.getText()));

        // ── Event: Discount field Enter key ───────────────────────────────
        discountField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    controller.handleApplyDiscount(discountField.getText());
            }
        });

        // ── Event: Checkout button click ───────────────────────────────────
        btnCheckout.addActionListener(e -> {
            String method = (String) paymentMethodBox.getSelectedItem();
            controller.handleCheckout(tenderedField.getText(), method);
        });

        // ── Event: Tendered field change (live "change due" hint) ──────────
        tenderedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { updateChangeDue(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { updateChangeDue(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateChangeDue(); }
        });

        return panel;
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CLR_STATUS_BG);
        bar.setBorder(new EmptyBorder(5, 14, 5, 14));

        statusBar = new JLabel("Welcome to Bonnie's POS System — Ready.");
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusBar.setForeground(new Color(200, 230, 255));
        bar.add(statusBar, BorderLayout.WEST);

        JLabel version = new JLabel("v1.0");
        version.setFont(new Font("Monospaced", Font.PLAIN, 11));
        version.setForeground(new Color(120, 150, 170));
        bar.add(version, BorderLayout.EAST);

        return bar;
    }

    // =========================================================================
    //  Event wiring — all EventBus subscriptions in one place
    // =========================================================================

    private void wireEventHandlers() {

        // ITEM_ADDED — refresh cart view, show feedback
        bus.on(EventType.ITEM_ADDED, payload -> SwingUtilities.invokeLater(() -> {
            CartItem ci = (CartItem) payload;
            refreshCartTable();
            refreshTotals();
            setStatus("✔  Added: " + ci.getProduct().getName()
                    + " x" + ci.getQuantity(), CLR_ACCENT);
        }));

        // ITEM_REMOVED — refresh cart view, show feedback
        bus.on(EventType.ITEM_REMOVED, payload -> SwingUtilities.invokeLater(() -> {
            refreshCartTable();
            refreshTotals();
            setStatus("✖  Item removed from cart.", CLR_WARNING);
        }));

        // CART_CLEARED
        bus.on(EventType.CART_CLEARED, payload -> SwingUtilities.invokeLater(() -> {
            refreshCartTable();
            refreshTotals();
            setStatus("Cart cleared.", CLR_WARNING);
        }));

        // DISCOUNT_CHANGED — totals already updated by VIEW_REFRESHED, just show feedback
        bus.on(EventType.DISCOUNT_CHANGED, payload -> SwingUtilities.invokeLater(() -> {
            double pct = (double) payload;
            setStatus(String.format("Discount set to %.1f%%", pct), CLR_ACCENT);
        }));

        // STOCK_CHANGED — refresh catalog to reflect new stock numbers
        bus.on(EventType.STOCK_CHANGED, payload -> SwingUtilities.invokeLater(() -> {
            String term = searchField == null ? "" : searchField.getText().trim();
            refreshCatalog(term);
        }));

        // SEARCH_REQUESTED — filter catalog
        bus.on(EventType.SEARCH_REQUESTED, payload -> SwingUtilities.invokeLater(() -> {
            refreshCatalog((String) payload);
        }));

        // VIEW_REFRESHED — general redraw (totals, etc.)
        bus.on(EventType.VIEW_REFRESHED, payload -> SwingUtilities.invokeLater(() -> {
            refreshCartTable();
            refreshTotals();
        }));

        // TRANSACTION_COMPLETE — show receipt dialog
        bus.on(EventType.TRANSACTION_COMPLETE, payload -> SwingUtilities.invokeLater(() -> {
            Receipt receipt = (Receipt) payload;
            showReceiptDialog(receipt);
            setStatus("✔  Transaction complete! Receipt: " + receipt.getReceiptId(), CLR_ACCENT);
            tenderedField.setText("0.00");
            discountField.setText("0");
        }));

        // ERROR_OCCURRED — show error in status bar and dialog
        bus.on(EventType.ERROR_OCCURRED, payload -> SwingUtilities.invokeLater(() -> {
            String msg = (String) payload;
            setStatus("⚠  " + msg, CLR_DANGER);
            JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }));
    }

    // =========================================================================
    //  Refresh helpers
    // =========================================================================

    private void refreshCatalog(String searchTerm) {
        List<Product> products = controller.search(searchTerm);
        showProductsInTable(products);
    }

    private void showProductsInTable(List<Product> products) {
        catalogModel.setRowCount(0);
        for (Product p : products) {
            catalogModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    p.getCategory(),
                    String.format("$%.2f", p.getPrice()),
                    p.getStockQuantity(),
                    p.isTaxable() ? "Yes" : "No"
            });
        }
    }

    private void refreshCartTable() {
        cartModel.setRowCount(0);
        for (CartItem ci : cart.getItems()) {
            cartModel.addRow(new Object[]{
                    ci.getProduct().getId(),
                    ci.getProduct().getName(),
                    String.format("$%.2f", ci.getProduct().getPrice()),
                    ci.getQuantity(),
                    String.format("$%.2f", ci.getLineTotal())
            });
        }
    }

    private void refreshTotals() {
        lblSubtotal.setText(String.format("$%.2f", cart.getSubtotal()));
        lblDiscount.setText(String.format("-$%.2f (%.0f%%)",
                cart.getDiscountAmount(), cart.getDiscountPercent()));
        lblTax.setText(String.format("$%.2f", cart.getTaxAmount()));
        lblTotal.setText(String.format("$%.2f", cart.getGrandTotal()));
    }

    /** Live change-due hint in the status bar as the user types. */
    private void updateChangeDue() {
        try {
            double tendered = Double.parseDouble(tenderedField.getText().trim());
            double change   = tendered - cart.getGrandTotal();
            if (change >= 0) {
                setStatus(String.format("Change due: $%.2f", change), CLR_ACCENT);
            } else {
                setStatus(String.format("Still owed: $%.2f", -change), CLR_WARNING);
            }
        } catch (NumberFormatException ignored) { /* user still typing */ }
    }

    // =========================================================================
    //  Dialogs
    // =========================================================================

    private void showReceiptDialog(Receipt receipt) {
        // Load receipt text into the shared receiptArea
        receiptArea.setText(receipt.format());
        receiptArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        receiptArea.setEditable(false);
        receiptArea.setBackground(new Color(252, 252, 248));
        receiptArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(receiptArea);
        scroll.setPreferredSize(new Dimension(520, 480));

        // Action buttons — View and Print
        JButton btnPrint = new JButton("🖨 Print Receipt");
        btnPrint.setBackground(new Color(30, 87, 153));
        btnPrint.setForeground(Color.WHITE);
        btnPrint.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnPrint.addActionListener(e -> printReceipt());

        JButton btnClose = new JButton("Close");

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.add(btnClose);
        btnPanel.add(btnPrint);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(scroll,    BorderLayout.CENTER);
        panel.add(btnPanel,  BorderLayout.SOUTH);

        JDialog dialog = new JDialog(this, "Receipt — " + receipt.getReceiptId(), true);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);

        btnClose.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    // ── Thermal Printer ───────────────────────────────────────────────────────

    private void printReceipt() {
        String receiptText = receiptArea.getText();
        if (receiptText.isBlank()) {
            JOptionPane.showMessageDialog(this,
                "No receipt to print. Complete a checkout first.",
                "Print Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Look up all available printers
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services.length == 0) {
            JOptionPane.showMessageDialog(this,
                "No printers found. Make sure your thermal printer is connected.",
                "Print Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // If no printer saved yet, ask user to pick one
        if (savedPrinterName == null) {
            String[] printerNames = Arrays.stream(services)
                .map(PrintService::getName)
                .toArray(String[]::new);

            String chosen = (String) JOptionPane.showInputDialog(
                this,
                "Select your thermal printer (remembered for this session):",
                "Select Printer",
                JOptionPane.PLAIN_MESSAGE,
                null,
                printerNames,
                printerNames[0]
            );
            if (chosen == null) return; // user cancelled
            savedPrinterName = chosen;
        }

        // Find saved PrintService
        final String nameToFind = savedPrinterName;
        PrintService printer = Arrays.stream(services)
            .filter(ps -> ps.getName().equals(nameToFind))
            .findFirst().orElse(null);
        if (printer == null) {
            JOptionPane.showMessageDialog(this,
                "Saved printer not found. Resetting.", "Print Error", JOptionPane.ERROR_MESSAGE);
            savedPrinterName = null;
            return;
        }

        try {
            byte[] ESC_INIT  = { 0x1B, 0x40 };              // Initialize printer
            byte[] FEED      = { 0x0A, 0x0A, 0x0A };        // 3 line feeds before cut
            byte[] CUT_PAPER = { 0x1D, 0x56, 0x41, 0x10 };  // Full paper cut

            byte[] textBytes  = receiptText.getBytes("UTF-8");
            byte[] printData  = new byte[ESC_INIT.length + textBytes.length + FEED.length + CUT_PAPER.length];

            int pos = 0;
            System.arraycopy(ESC_INIT,  0, printData, pos, ESC_INIT.length);  pos += ESC_INIT.length;
            System.arraycopy(textBytes, 0, printData, pos, textBytes.length); pos += textBytes.length;
            System.arraycopy(FEED,      0, printData, pos, FEED.length);      pos += FEED.length;
            System.arraycopy(CUT_PAPER, 0, printData, pos, CUT_PAPER.length);

            DocPrintJob job = printer.createPrintJob();
            Doc doc = new SimpleDoc(printData, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            job.print(doc, new HashPrintRequestAttributeSet());

            setStatus("✔  Receipt printed on " + savedPrinterName, CLR_ACCENT);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Print failed: " + e.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    //  Utility helpers
    // =========================================================================

    private void startClock() {
        clockTimer.start();
        // Trigger immediately so the label isn't blank for 1 second
        clockTimer.getActionListeners()[0].actionPerformed(null);
    }

    private void setStatus(String msg, Color color) {
        statusBar.setText(msg);
        statusBar.setForeground(color == CLR_DANGER ? new Color(255, 120, 120)
                : color == CLR_WARNING              ? new Color(255, 210, 100)
                : new Color(120, 230, 160));
    }

    /**
     * Finds the productIdField by name and sets its text.
     * Called when the user double-clicks a row in the catalog table.
     */
    private void fillProductIdField(String id) {
        // Walk the component tree to find the named field
        findAndFill(getContentPane(), "productIdField", id);
    }

    private boolean findAndFill(Container container, String name, String value) {
        for (Component c : container.getComponents()) {
            if (name.equals(c.getName()) && c instanceof JTextField) {
                ((JTextField) c).setText(value);
                return true;
            }
            if (c instanceof Container && findAndFill((Container) c, name, value)) return true;
        }
        return false;
    }

    private void styleTable(JTable table) {
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setRowHeight(26);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setBackground(CLR_PRIMARY);
        table.getTableHeader().setForeground(CLR_WHITE);
        table.setSelectionBackground(new Color(180, 215, 255));
        table.setSelectionForeground(Color.BLACK);
        table.setGridColor(new Color(220, 225, 235));
        table.setShowVerticalLines(true);

        // Alternating row colours via a custom renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean selected, boolean focused, int row, int col) {
                super.getTableCellRendererComponent(t, val, selected, focused, row, col);
                if (!selected) setBackground(row % 2 == 0 ? CLR_WHITE : CLR_ROW_ALT);
                setBorder(new EmptyBorder(0, 6, 0, 6));
                return this;
            }
        });
    }

    private JButton styledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                new EmptyBorder(6, 14, 6, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ── Event: hover visual feedback ───────────────────────────────────
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(bg); }
            @Override public void mousePressed(MouseEvent e) { btn.setBackground(bg.darker()); }
            @Override public void mouseReleased(MouseEvent e){ btn.setBackground(bg); }
        });

        return btn;
    }

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(new Color(60, 60, 80));
        return lbl;
    }

    private JLabel summaryLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.RIGHT);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 14));
        lbl.setForeground(new Color(30, 30, 60));
        return lbl;
    }

    // =========================================================================
    //  Entry point
    // =========================================================================

    public static void main(String[] args) {
        // Apply system look-and-feel for native widget rendering
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fall back to default Swing L&F */ }

        SwingUtilities.invokeLater(POSApp::new);
    }
}
