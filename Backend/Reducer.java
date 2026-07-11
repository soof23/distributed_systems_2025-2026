import java.io.*;
import java.net.*;
import java.util.*;

public class Reducer {
    private static final int REDUCER_PORT = 5070;
    private static final int MASTER_PORT = 5055;
    private static final String MASTER_HOST = "localhost";
    private Socket masterSocket;
    private static ObjectOutputStream masterOut;
    private ServerSocket reducerSocket;

    private int expectedWorkers = 0;

    // Η κύρια δομή δεδομένων που κρατάει τα συγκεντρωτικά αποτελέσματα 
    private final Map<String, Object> aggregatedResults = new HashMap<>();

    public Map<String, Object> getAggregatedResults() {
        return aggregatedResults;
    }

    public static void main(String[] args) {
        Reducer red = new Reducer();
        red.loadConfig("config.txt");   // φορτώνει πλήθος workers
        red.startReducer();   // εκκίνηση του reducer server
    }

    private void loadConfig(String filename) {
        Properties pr = new Properties();

        try (FileInputStream fis = new FileInputStream(filename)) {
            pr.load(fis);
            expectedWorkers = Integer.parseInt(pr.getProperty("nodes", "0"));
            System.out.println("Reducer loaded expected workers: " + expectedWorkers);
        } catch (Exception e) {
            System.err.println("Reducer failed to read config file: " + e.getMessage());
            expectedWorkers = 0;
        }
    }

    public void startReducer() {
        try (ServerSocket reducerSocket = new ServerSocket(REDUCER_PORT)) {
            this.reducerSocket = reducerSocket;
            System.out.println("Reducer node running on port " + REDUCER_PORT);

            connectToMasterWithRetry();

            // Thread reporter: Υλοποιεί την ασύγχρονη ενημέρωση του Master
            Thread reporter = new Thread(() -> {
                while(true) {
                    try {
                        synchronized (aggregatedResults) {
                            aggregatedResults.wait(); // Αναμονή μέχρι κάποιος ReducerHandler να κάνει notify
                            // Δημιουργία αντιγράφου των αποτελεσμάτων για ασφαλή αποστολή
                            Map<String, Object> snapshot = new HashMap<>(aggregatedResults);
                            try {
                                masterOut.writeObject(snapshot);
                                masterOut.flush();
                                masterOut.reset(); // Καθαρισμός cache του stream
                            } catch (IOException ioE) {
                                System.out.println("Lost connection to Master. Retrying...");
                                connectToMasterWithRetry(); // Επανασύνδεση αμέσως
                                masterOut.writeObject(snapshot);
                                masterOut.flush();
                                masterOut.reset();
                            }
                            //
                            aggregatedResults.keySet().removeIf(key -> key.startsWith("search_"));
                            //
                            System.out.println("Sent updated data to Master.");
                        }
                    } catch (Exception e) {
                        System.err.println("Reporter error: " + e.getMessage());
                        try {
                            connectToMasterWithRetry();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            reporter.setDaemon(true);
            reporter.start();

            // Αποδοχή συνδέσεων από Workers
            while (true) {
                Socket workerSocket = reducerSocket.accept();
                // Κάθε Worker εξυπηρετείται από δικό του Thread (ReducerHandler)
                new ReducerHandler(workerSocket,aggregatedResults, expectedWorkers).start();
            }
        } catch (IOException e) {
            System.err.println("Reducer Server Error: " + e.getMessage());
        } finally {
            try {
                if (reducerSocket != null && !reducerSocket.isClosed()) {
                    reducerSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (masterSocket != null && !masterSocket.isClosed()) {
                    masterSocket.close();
                }
            }catch (IOException e) {
                    e.printStackTrace();
            }
        }
    }

    // Ο Reducer συνδέεται με retry στον Master ώστε να μην χαλάσει αν ξεκινήσει πριν
    private synchronized void connectToMasterWithRetry() {
        while (true) {
            try {
                if (masterSocket != null && !masterSocket.isClosed()) {
                    try {
                        masterSocket.close();
                    } catch (IOException ignored) {
                    }
                }
                masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
                masterOut = new ObjectOutputStream(masterSocket.getOutputStream());

                masterOut.writeObject("Reducer established connection");
                masterOut.writeObject(null);
                masterOut.flush();

                System.out.println("Connected to Master");
                return;
            } catch (IOException e) {
                System.out.println("Master not ready yet. Reducer retrying...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
