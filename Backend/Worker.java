import java.io.*;
import java.net.*;
import java.util.*;


public class Worker {
    // Στοιχεία σύνδεσης
    private int WORKER_PORT;
    private ServerSocket workerSocket;
    private static final int MASTER_PORT = 5055;
    private static final String MASTER_HOST = "localhost";

    // workerId για να τον ξεχωρίζει ο Reducer
    private final String workerId;

    // maps για την αποθήκευση των κερδών και πονταρισμάτων
    private final Map<String, Double> gameProfits = new HashMap<>();
    private final Map<String, Double> playerProfits = new HashMap<>();
    private final Map<String, Double> gameTotalBets = new HashMap<>();
    private final Map<String, Double> playerTotalBets = new HashMap<>();
    
    public Worker() {
        // δημιουργεί μοναδικό workerID 8 χαρακτήρων
        this.workerId = "Worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Worker <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        new Worker().startWorker(port);
    }

    public void startWorker(int port) {
        try {
            WORKER_PORT = port;
            try {
                workerSocket = new ServerSocket(WORKER_PORT);
            } catch (IOException e) {
                System.err.println("Error starting Worker: " + e.getMessage());
                return;
            }
            System.out.println("Worker node " + workerId + " running on port " + WORKER_PORT);
            System.out.println("Waiting for requests from Master...");
			
			// αποδοχή συνδέσεων από master, κάθε σύνδεση σε νέο thread
            while (true) {
                Socket masterSocket = workerSocket.accept();
                System.out.println("\nReceived connection from: " + masterSocket.getInetAddress().getHostAddress() + ":" + masterSocket.getPort());
                WorkerHandler handler = new WorkerHandler(masterSocket, workerId, this);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            System.err.println("Error starting Worker: " + e.getMessage());
        } finally {
            // Close sockets
            try {
                if (workerSocket  != null && !workerSocket.isClosed())
                    workerSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // για την ενημέρωση των στατιστικών
    public synchronized void updateLocalStats(String gameName, String playerId, double bet, double netResult) {
        // Ενημέρωση Πονταρισμάτων
        gameTotalBets.put(gameName, gameTotalBets.getOrDefault(gameName, 0.0) + bet);
        playerTotalBets.put(playerId, playerTotalBets.getOrDefault(playerId, 0.0) + bet);

        // Ενημέρωση Κερδών/Ζημιών (Net Result)
        gameProfits.put(gameName, gameProfits.getOrDefault(gameName, 0.0) + netResult);
        playerProfits.put(playerId, playerProfits.getOrDefault(playerId, 0.0) + netResult);
        
        System.out.println("[Worker] Stats updated for " + playerId + " on " + gameName);
    }

    public synchronized Map<String, Double> getGameProfits() {
        return new HashMap<>(gameProfits);
    }

    public synchronized Map<String, Double> getPlayerProfits() {
        return new HashMap<>(playerProfits);
    }
    
    public int getPort() {
        return WORKER_PORT;
    }
}