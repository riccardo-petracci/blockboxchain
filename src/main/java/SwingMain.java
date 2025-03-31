import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SwingMain {

    public static void createAndShowGUI() { //private - messo public per un test
        JFrame frame = new JFrame("MongoDB and Blockchain");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.3);

        // Left Panel
        JPanel leftPanel = new JPanel(new BorderLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();
        DefaultListModel<String> listModelDetails = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(list);
        JButton addButton = new JButton("Aggiungi");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(addButton);

        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Right Panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        JTree jsonTree = new JTree();
        jsonTree.setVisible(false);
        JScrollPane textScrollPane = new JScrollPane(jsonTree);
        
        
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JToggleButton button1 = new JToggleButton("JSON");
        
        JButton button2 = new JButton("Controllo integrità");
        button2.setToolTipText("Controlla l'integrità sulla blockchain");
        rightButtonPanel.add(button1);
        rightButtonPanel.add(button2);
        
        rightPanel.add(textScrollPane, BorderLayout.CENTER);
        rightPanel.add(rightButtonPanel, BorderLayout.SOUTH);

        
        button1.addItemListener(e -> {
            if (button1.isSelected()) {
                textScrollPane.setViewportView(textArea);  // Switch to text area
            } else {
                textScrollPane.setViewportView(jsonTree);  // Switch to JSON tree
            }
        });
    
        // List selection event
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
            	if(!jsonTree.isVisible())
            		jsonTree.setVisible(true);
                String selectedValue = listModelDetails.get(list.getSelectedIndex());
                textArea.setText(selectedValue);
                jsonTree.setModel(buildTreeModel(selectedValue));
                expandAllNodes(jsonTree, 0, jsonTree.getRowCount());
            }
        });


        List<String> items = MongoDBConnection.getStandardEntries();
        int i=0;
        for (String item : items) {
            listModelDetails.add(i, item);
            listModel.add(i, MongoDBConnection.jsonPanelId(item));
            i++;
        }

        button2.addActionListener(e -> 
        {
        	String item = textArea.getText();

        	try {
                 HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
                 SmartContractHash SCH = new SmartContractHash(settings.get("address"), settings.get("credentials"), settings.get("contract"), 
                                			new BigInteger(settings.get("gaslimit")), new BigInteger(settings.get("gasprice")));
                 String normalized = MongoDBConnection.normalizeJson(item);
                 String hashed = SmartContractHash.generateMD5(normalized);
                 System.out.println("\nJSON Normalizzato:\n" + normalized + "\n" + "Hash Normalizzato:\n" + hashed);

                 int blockNumber = SCH.checkHash(hashed);
     
                 if(blockNumber > 0)
                 {
                	 JOptionPane.showMessageDialog(frame, "Hash del documento presente al blocco " + blockNumber, "Success", JOptionPane.INFORMATION_MESSAGE);
                 }
                 else
                 {
                	 JOptionPane.showMessageDialog(frame, "L'Hash del documento non è stato rilevato nella blockchain", "Error", JOptionPane.ERROR_MESSAGE);
                 }
                 
             } catch (Exception e1) 
        	{
            	 JOptionPane.showMessageDialog(frame, "Si è verificato un errore.", "Error", JOptionPane.ERROR_MESSAGE);
                 
            	 e1.printStackTrace();                               
        	}
        });
        
        // Add Button Action
        addButton.addActionListener(e -> {
            JDialog addDialog = new JDialog(frame, "Aggiungi documento", true);
            addDialog.setSize(400, 300);
            addDialog.setLayout(new BorderLayout());

            // Text area for input
            JTextArea inputTextArea = new JTextArea(5, 30);
            JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
            inputTextArea.setLineWrap(true);
            inputTextArea.setWrapStyleWord(true);

            // Panel for label and buttons
            JPanel bottomPanel = new JPanel(new BorderLayout());

            // Label aligned to the left
            JLabel infoLabel = new JLabel("");
            bottomPanel.add(infoLabel, BorderLayout.WEST);

            // Buttons aligned to the right
            JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancelButton = new JButton("Annulla");
            JButton confirmAddButton = new JButton("Aggiungi");
            buttonPanel2.add(cancelButton);
            buttonPanel2.add(confirmAddButton);
            bottomPanel.add(buttonPanel2, BorderLayout.EAST);

            // Progress bar at the bottom
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setVisible(false); // Initially hidden

            // Add components to dialog
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.add(inputScrollPane, BorderLayout.CENTER);
            contentPanel.add(bottomPanel, BorderLayout.SOUTH);

            addDialog.add(contentPanel, BorderLayout.CENTER);
            addDialog.add(progressBar, BorderLayout.SOUTH);

            // Button actions
            cancelButton.addActionListener(ev -> addDialog.dispose());

         // Add WindowListener to detect dispatch events
            addDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                	items.clear();
                    listModelDetails.clear();
                    listModel.clear();
                    List<String> items = MongoDBConnection.getStandardEntries();
                    int i=0;
                    for (String item : items) {
                        listModelDetails.add(i, item);
                        listModel.add(i, MongoDBConnection.jsonPanelId(item));
                        i++;
                    }
                    listScrollPane.repaint(); 
                }
            });
            
            
            confirmAddButton.addActionListener(ev -> {
                String newItem = inputTextArea.getText().trim();
                if (!newItem.isEmpty()) {
                    progressBar.setVisible(true);
                    infoLabel.setText("");
                    confirmAddButton.setEnabled(false);
                    cancelButton.setEnabled(false);

                    // Background task to upload data
                    SwingWorker<Boolean, Void> uploadTask = new SwingWorker<Boolean, Void>() {
                        private String errorMessage = null;

                        @Override
                        protected Boolean doInBackground() {
                            try {
                            	infoLabel.setText("Salvataggio in corso su database...");
                                String normalized = MongoDBConnection.normalizeJson(newItem);
                                System.out.println("\n\nJSON Normalizzato:\n" + normalized + "\n\n");
                                String updatedJson = MongoDBConnection.storeStandardEntry(normalized); // newItem
                                updatedJson = MongoDBConnection.normalizeJson(updatedJson);
                                String entryID = MongoDBConnection.getMongodbIdJson(updatedJson);
                                System.out.println("\n\nJSON Aggiornato con l'id di mongodb:\n" + updatedJson + "\n\n");

                                if (! entryID.equals(""))
                                {
                                	infoLabel.setText("Salvataggio in corso su blockchain...");
                                	HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
                                	SmartContractHash SCH = new SmartContractHash(settings.get("address"), settings.get("credentials"), settings.get("contract"), 
                                			new BigInteger(settings.get("gaslimit")), new BigInteger(settings.get("gasprice")));
//                                	String normalized = MongoDBConnection.normalizeJson(newItem);
//                                    System.out.println("\n\nJSON Normalizzato:\n" + normalized + "\n\n");

                                	String hashed = SmartContractHash.generateMD5(updatedJson);
                                	String receipt = SCH.storeHash(hashed);
                                    System.out.println("Hashed md5: " + hashed + "Receipt SCH.storeHash(hashed): " + receipt);
                                	String blockNumber = SmartContractHash.extractField(receipt, "blockNumber");
                                	if(blockNumber.contains("0x"))
                                	{
                                		blockNumber = blockNumber.replaceFirst("^0x", "");
                                		int DecimalBlockNumber = Integer.parseInt(blockNumber, 16);
                                		blockNumber = "" + DecimalBlockNumber;
                                	}

                                	if (Integer.parseInt(blockNumber) <= 0) 
                                    {
                                    	errorMessage = "Si è verificato un problema durante il salvataggio il blockchain.";
                                        return false;
                                    }
                                	infoLabel.setText("Aggiornamento database...");
                                	MongoDBConnection.storeBlock(entryID, blockNumber);
    
                                }
                                else
                                {
                                	errorMessage = "Si è verificato un problema durante il salvataggio nel database.";
                                    return false;
                                }
                                
                                return true;
                            } catch (Exception e) {
                                errorMessage = "Si è verificato un errore: " + e.getMessage();
                                e.printStackTrace();
                                return false;
                            }
                        }

                        @Override
                        protected void done() {
                            progressBar.setVisible(false);
                            confirmAddButton.setEnabled(true);
                            cancelButton.setEnabled(true);
                            
                            
                            try {
                                boolean success = get();
                                if (success) {
                                    infoLabel.setText("");
                                    JOptionPane.showMessageDialog(addDialog, "Salvataggio riuscito!", "Success", JOptionPane.INFORMATION_MESSAGE);
                                    addDialog.dispose();
                                } else {
                                    infoLabel.setText("");
                                    JOptionPane.showMessageDialog(addDialog, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            } catch (Exception e) {
                                JOptionPane.showMessageDialog(addDialog, "Errore: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                e.printStackTrace();
                            }
                        }
                    };
                    uploadTask.execute();
                }
            });


            addDialog.setLocationRelativeTo(frame);
            addDialog.setVisible(true);
        });

        // Add to SplitPane
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        
        frame.add(splitPane);
        frame.setVisible(true);
    }

//    Metodi grafici visualizzazione JSON
    private static DefaultTreeModel buildTreeModel(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(json);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("JSON");
            buildTree(root, rootNode);
            return new DefaultTreeModel(root);
        } catch (IOException e) {
            e.printStackTrace();
            return new DefaultTreeModel(new DefaultMutableTreeNode("Invalid JSON"));
        }
    }

    private static void buildTree(DefaultMutableTreeNode parent, JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(entry.getKey());
                parent.add(child);
                buildTree(child, entry.getValue());
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode("[" + i + "]");
                parent.add(child);
                buildTree(child, node.get(i));
            }
        } else {
            parent.add(new DefaultMutableTreeNode(node.asText())); // Leaf node
        }
    }

    private static void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }
}
