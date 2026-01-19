package software.ulpgc.moneycalculator.application.queen;

import software.ulpgc.moneycalculator.architecture.control.Command;
import software.ulpgc.moneycalculator.architecture.model.Currency;
import software.ulpgc.moneycalculator.architecture.model.Money;
import software.ulpgc.moneycalculator.architecture.ui.CurrencyDialog;
import software.ulpgc.moneycalculator.architecture.ui.MoneyDialog;
import software.ulpgc.moneycalculator.architecture.ui.MoneyDisplay;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Desktop extends JFrame {
    private final Map<String, Command> commands;
    private final List<Currency> currencies;

    private JTextField inputAmount;
    private JComboBox<Currency> inputCurrency;
    private JTextField outputAmount;
    private JComboBox<Currency> outputCurrency;

    private JButton exchangeButton;
    private JButton swapButton;

    private DefaultListModel<String> historyModel;
    private JList<String> historyList;

    private Timer debounceTimer;
    private static final int DEBOUNCE_MS = 300;

    private boolean recordHistoryNext = false;

    private final DecimalFormat df = new DecimalFormat("#0.00");

    public Desktop(List<Currency> currencies) throws HeadlessException {
        this.commands = new HashMap<>();
        this.currencies = currencies;

        this.debounceTimer = new Timer(DEBOUNCE_MS, e -> triggerExchangeNow());
        this.debounceTimer.setRepeats(false);

        setTitle("Money Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(820, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        setContentPane(buildContent());
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        root.add(buildConverterPanel(), BorderLayout.NORTH);
        root.add(buildHistoryPanel(), BorderLayout.CENTER);

        return root;
    }

    private JComponent buildConverterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Converter"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        panel.add(new JLabel("Amount"), c);

        c.gridx = 1;
        inputAmount = amountInput();
        panel.add(inputAmount, c);

        c.gridx = 2;
        inputCurrency = currencySelector();
        panel.add(inputCurrency, c);

        c.gridx = 3;
        swapButton = new JButton("↔");
        swapButton.setToolTipText("Intercambiar monedas");
        swapButton.addActionListener(e -> swapCurrencies());
        panel.add(swapButton, c);

        c.gridx = 4;
        outputCurrency = currencySelector();
        panel.add(outputCurrency, c);

        c.gridx = 5;
        outputAmount = amountOutput();
        panel.add(outputAmount, c);

        c.gridx = 6;
        exchangeButton = new JButton("Exchange");
        exchangeButton.addActionListener(e -> {
            recordHistoryNext = true;
            triggerExchangeNow();
        });
        panel.add(exchangeButton, c);

        return panel;
    }

    private JComponent buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("History"));

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setVisibleRowCount(10);

        JScrollPane scroll = new JScrollPane(historyList);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> historyModel.clear());
        actions.add(clear);

        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JTextField amountInput() {
        JTextField tf = new JTextField(10);
        tf.getDocument().addDocumentListener(new SimpleDocumentListener(this::triggerExchangeDebounced));
        return tf;
    }

    private JTextField amountOutput() {
        JTextField textField = new JTextField(10);
        textField.setEditable(false);
        return textField;
    }

    private JComboBox<Currency> currencySelector() {
        JComboBox<Currency> cb = new JComboBox<>(toArray(currencies));
        cb.addActionListener(e -> triggerExchangeDebounced());
        return cb;
    }

    private Currency[] toArray(List<Currency> currencies) {
        return currencies.toArray(new Currency[0]);
    }

    private void triggerExchangeDebounced() {
        if (commands.get("exchange") == null) return;
        recordHistoryNext = false;
        debounceTimer.restart();
    }

    private void triggerExchangeNow() {
        Command exchange = commands.get("exchange");
        if (exchange != null) exchange.execute();
    }

    public void addCommand(String name, Command command) {
        this.commands.put(name, command);
    }

    public MoneyDialog moneyDialog() {
        return () -> new Money(inputAmountValue(), inputCurrencyValue());
    }

    public CurrencyDialog currencyDialog() {
        return this::outputCurrencyValue;
    }

    public MoneyDisplay moneyDisplay() {
        return money -> {
            outputAmount.setText(df.format(money.amount()));
            if (recordHistoryNext) {
                addToHistory(money);
                recordHistoryNext = false;
            }
        };
    }

    private void addToHistory(Money result) {
        double amount = inputAmountValue();
        Currency from = inputCurrencyValue();
        Currency to = outputCurrencyValue();
        if (from == null || to == null) return;

        String entry = df.format(amount) + " " + from.code() + " → " + df.format(result.amount()) + " " + to.code();
        historyModel.add(0, entry);
    }

    private double inputAmountValue() {
        return toDouble(inputAmount.getText());
    }

    private double toDouble(String text) {
        if (text == null) return 0.0;
        text = text.trim().replace(",", ".");
        if (text.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Currency inputCurrencyValue() {
        return (Currency) inputCurrency.getSelectedItem();
    }

    private Currency outputCurrencyValue() {
        return (Currency) outputCurrency.getSelectedItem();
    }

    private void swapCurrencies() {
        Currency from = (Currency) inputCurrency.getSelectedItem();
        Currency to = (Currency) outputCurrency.getSelectedItem();

        inputCurrency.setSelectedItem(to);
        outputCurrency.setSelectedItem(from);

        recordHistoryNext = false;
        triggerExchangeNow();
    }
}
