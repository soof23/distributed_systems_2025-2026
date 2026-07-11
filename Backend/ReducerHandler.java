import com.example.gameapp.*;
import java.io.*;
import java.net.*;
import java.util.*;

// Κλάση ReducerHandler: Υλοποιεί την επεξεργασία του Reduce για κάθε Worker ξεχωριστά
class ReducerHandler extends Thread {
    // ΟΧΙ static: Κάθε Handler πρέπει να έχει το δικό του socket και streams
    private Socket workerSocket; // Sockets για να συνδέονται οι workers
    private ObjectOutputStream out; // για τους workers
    private ObjectInputStream in;
    private final Map<String, Object> aggregatedResults; // Πρόσβαση στην κοινόχρηστη δομή αποτελεσμάτων του Reducer

    // μετρητής ολοκλήρωσης Workers ανά Search ID, static για να μοιράζεται σε όλα τα threads των Handlers
    private static final Map<String, Integer> searchCompletionMap = new HashMap<>();
    // αριθμός των workers που αναμένονται συνολικά
    private final int expectedWorkers;

    public ReducerHandler(Socket workerSocket, Map<String, Object> aggregatedResults, int expectedWorkers) throws IOException {
        this.workerSocket = workerSocket;
        this.aggregatedResults = aggregatedResults;
        this.expectedWorkers = expectedWorkers;
        // Σειρά αρχικοποίησης: Πρώτα το Output, μετά το Input για αποφυγή deadlocks
        this.out = new ObjectOutputStream(workerSocket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(workerSocket.getInputStream());
    }

    @Override
    public void run() {
        try {
            // Ανάγνωση εντολής από τον Worker
            String command = (String) in.readObject();
            System.out.println("Reducer received command: " + command);
                
            // Επεξεργασία αν η εντολή είναι "mapResult"
            // Διαδικασία Reduce: Συγχώνευση ενδιάμεσων τιμών
            if ("mapResult".equals(command)) {
                // Λήψη map results από τον worker
                Object data = in.readObject();
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) data;
                    processWorkerData(resultMap);   // συγχώνευση των δεδομένων
                } else {
                    out.writeObject("Invalid data format");
                    out.flush();
                }
            } else {
                out.writeObject("Unknown command");
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("Error handling connection: " + e.getMessage());
        } finally {
            // Κλείσιμο σύνδεσης με τον Worker μετά την επεξεργασία
            try {
                workerSocket.close(); 
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processWorkerData(Map<String, Object> resultMap) {
        // Όλες οι αλλαγές μέσα σε synchronized block για thread-safety
        synchronized (aggregatedResults) {
			// ΠΕΡΙΠΤΩΣΗ Α: ΑΠΟΤΕΛΕΣΜΑΤΑ ΑΝΑΖΗΤΗΣΗΣ: Συγκεντρώνει τα παιχνίδια που βρήκαν όλοι οι Workers για ένα συγκεκριμένο Search ID
			if (resultMap.containsKey("searchId")) {
                String searchId = (String) resultMap.get("searchId");
                @SuppressWarnings("unchecked")
                List<Game> gamesFound = (List<Game>) resultMap.get("games");

                // Συγκέντρωση αποτελεσμάτων για το συγκεκριμένο Search ID
                String searchKey = "search_" + searchId;

				// Λήψη της μέχρι τώρα λίστας παιχνιδιών ή δημιουργία νέας αν είναι ο πρώτος Worker
                @SuppressWarnings("unchecked")
                List<Game> totalGames = (List<Game>) aggregatedResults.getOrDefault(searchKey, new ArrayList<Game>());

                if (gamesFound != null) {
                    totalGames.addAll(gamesFound);
                }
                    
                aggregatedResults.put(searchKey, totalGames);

                // Ενημέρωση μετρητή Workers
                int count = searchCompletionMap.getOrDefault(searchId, 0) + 1;
                searchCompletionMap.put(searchId, count);

                System.out.println("Reducer got partial results for " + searchId + " from worker " + count + ". Total games so far: " + totalGames.size());

                // Έλεγχος αν απάντησαν όλοι οι Workers (numNodes)
                if (count >= expectedWorkers) {
                    System.out.println("Search " + searchId + " completed by all workers.");
                    searchCompletionMap.remove(searchId); // Καθαρισμός
                    aggregatedResults.notifyAll(); // Ειδοποίηση του Reporter για αποστολή στον Master
                }
            } else if (resultMap.containsKey("ProviderName")) {    // ΠΕΡΙΠΤΩΣΗ Β: ΣΤΑΤΙΣΤΙΚΑ (PROFIT/LOSS)
                updateStats(resultMap); 
                aggregatedResults.notifyAll(); // Αφύπνιση του Reporter thread στον Reducer για αποστολή των νέων δεδομένων στον Master
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void updateStats(Map<String, Object> resultMap) {
        // Λήψη βασικών στοιχείων από το αποτέλεσμα του Worker
        String providerName = (String) resultMap.get("ProviderName");
        String gameName     = (String) resultMap.get("GameName");
        String playerId     = (String) resultMap.get("PlayerID");
        // Το profitLoss μπορεί να είναι θετικό (κέρδος παίκτη/ζημιά συστήματος) ή αρνητικό (ζημιά παίκτη/κέρδος συστήματος)
        double profitLoss   = (Double) resultMap.get("ProfitLoss");
        
        // Α) Ενημέρωση στατιστικών ανά Provider
        String providerKey = "provider_" + providerName;
        Map<String, Double> providerStats = (Map<String, Double>) aggregatedResults.getOrDefault(providerKey, new HashMap<String, Double>());
        // Ενημέρωση κέρδους για το συγκεκριμένο παιχνίδι μέσα στον πάροχο
        double providerEffect = - profitLoss;
        providerStats.put(gameName, providerStats.getOrDefault(gameName, 0.0) + providerEffect);
        // Ενημέρωση συνολικού για τον πάροχο
        providerStats.put("Total", providerStats.getOrDefault("Total", 0.0) + providerEffect);
        aggregatedResults.put(providerKey, providerStats);

        // Β) Ενημέρωση συνολικών κερδών/ζημιών ανά Παίκτη
        String playerKey = "player_" + playerId;
        double currentTotal = (Double) aggregatedResults.getOrDefault(playerKey, 0.0);
        aggregatedResults.put(playerKey, currentTotal + profitLoss);
    }

}