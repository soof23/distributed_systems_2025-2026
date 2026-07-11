import com.example.gameapp.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Manager {
    // σύνδεση με Master Server
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 5055;

    // Sockets + Streams
    private Socket masterSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public static void main(String[] args) {
        // εκκίνηση της εφαρμογής Manager
        new Manager().start();
    }

    // κάνει την σύνδεση με τον Master
    private void connectToMaster() {
        try {
            masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
            out = new ObjectOutputStream(masterSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(masterSocket.getInputStream());
            System.out.println("Connected to Master server at " + MASTER_HOST + ":" + MASTER_PORT);
        } catch (IOException e) {
            System.err.println("Failed to connect to Master server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1); // τερματίζει αν δεν είναι δυνατή η σύνδεση
        }
    }

    // κλείνει με ασφάλεια τα streams και το socket της σύνδεσης 
    private void disconnectFromMaster() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (masterSocket != null) masterSocket.close();
            System.out.println("Disconnected from Master server");
        } catch (IOException e) {
            System.err.println("Error disconnecting from Master server: " + e.getMessage());
        }
    }
    
    // manager console app
    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Manager started.");

            // αρχική σύνδεση
            connectToMaster();
            try {
				boolean running = true;
                while (running) {
                    // εμφάνιση μενού επιλογών
                    System.out.println("\n Available actions: ");
                    System.out.println("1. addGame - Add a new game");
                    System.out.println("2. removeGame - Remove an available game");
                    System.out.println("3. modifyGame - Modify an available game");
                    System.out.println("4. showProfitLossPerProvider - Show total profit and loss for a provider");
                    System.out.println("5. showProfitLossPerPlayer - Show total profit and loss for a player");
                    System.out.println("6. exit - Exit the application");
                    System.out.print("Enter action: ");

                    String input = scanner.nextLine().trim();
                    // διαχείριση επιλογής χρήστη
                    switch (input) {
                        case "1": 
							addGame(scanner); 
							break;
                        case "2": 
							removeGame(scanner); 
							break;
                        case "3": 
							modifyGame(scanner); 
							break;
                        case "4": 
							showProfitLossPerProvider(scanner); 
							break;
                        case "5": 
							showProfitLossPerPlayer(scanner); 
							break;
                        case "6": 
							System.out.println("Exiting manager application."); 
							running = false;
							break;
                        default: System.out.println("Unknown command. Please try again.");
                    }
                }
            } finally {
                // αποσύνδεση κατά την έξοδο
                disconnectFromMaster();
            }
        } catch (Exception e) {
            System.err.println("Error in Manager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // αποστολή εντολών στον Master και επιστροφή
    private Object sendCommand(String command, Object data) {
        try {
            // Έλεγχος αν το socket είναι ανοιχτό, διαφορετικά προσπάθεια σύνδεσης
            if (masterSocket == null || masterSocket.isClosed() || !masterSocket.isConnected()) {
                connectToMaster();
            }
                
            System.out.println("Sending command to Master: " + command);
            out.writeObject(command);
            System.out.println("Sending data object to Master");
            out.writeObject(data);
            out.flush();

            // Λήψη απάντησης
            System.out.println("Waiting for response from Master...");
            Object response = in.readObject();
            System.out.println("Response received: " + response);
            return response;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with Master: " + e.getMessage());
            return "Communication error: Could not complete command " + command;
        }
    }

    // προσθήκη νέου παιχνιδιού, διαβάζοντας δεδομένα απο json αρχείο 
    private void addGame(Scanner scanner) {
        try{
            System.out.println("\n Add New Game from JSON ");
            System.out.print("Enter path to JSON file: ");
            String jsonPath = scanner.nextLine().trim();

            Path path = Paths.get(jsonPath);
            if (!Files.exists(path)) {
                System.out.println("File not found: " + jsonPath);
                return;
            }

            // διαβάζει το json
            String jsonContent = Files.readString(path);

            // μετατροπή από json σε game
            Game newGame = Game.toJson(jsonContent);

            String fullPath = newGame.getGameLogo();
            String fileNameWithExtension = Paths.get(fullPath).getFileName().toString();
            String fileNameOnly = fileNameWithExtension.substring(0, fileNameWithExtension.lastIndexOf('.'));
        
            // Αποθηκεύουμε μόνο το όνομα που αντιστοιχεί στο drawable
            newGame.setGameLogo(fileNameOnly.toLowerCase()); 
            System.out.println("Logo reference set to: " + fileNameOnly.toLowerCase());

            System.out.println("Sending game '" + newGame.getGameName() + "' to Master...");

            // αποστολή game στον Master και έλεγχος αν υπάρχει ήδη
            Object response = sendCommand("addGame", newGame);
            
            // έλεγχος απάντησης αν το παιχνίδι υπάρχει ήδη και είναι κρυμμένο
            if ("GAME_INACTIVE".equals(response)) {
                System.out.print("The game '" + newGame.getGameName() + "' already exists but is deactivated. Do you want to activate it? (yes/no): ");
                String ans = scanner.nextLine().trim();
				//επανεμφάνιση κρυμμένου παιχνιδιού
                if (ans.equalsIgnoreCase("yes")) {
                    Object reactivateResponse = sendCommand("reactivateGame", newGame);
                    System.out.println("Response: " + reactivateResponse);
                } else {
                    System.out.println("Operation cancelled.");
                }
            } else {
                System.out.println("Response: " + response);
            }
        } catch (Exception e) {
            System.out.println("Error adding game: " + e.getMessage());
        }
    }

    // αφαιρεί (κρύβει) ένα παιχνίδι απο την λίστα ενός παρόχου
    private void removeGame(Scanner scanner) {
        try {
            System.out.println("\n  Remove Game  ");
            // λήψη όλων των παιχνιδιών από τον Master (μέσω όλων των Workers)
            Object response = sendCommand("getAvailableGames", null);

            if (!(response instanceof List)) {
                System.out.println("No games found or error retrieving data.");
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<Game> games = (List<Game>)response;

            if (games.isEmpty()) {
                System.out.println("No active games found");
                return;
            }

            // εμφάνιση λίστας προς επιλογή παιχνιδιού
            System.out.println("\nActive Games: ");
            for (int i = 0; i < games.size(); i++) {
                Game g = games.get(i);
                System.out.printf("%d. %s (Provider: %s)\n", (i + 1), g.getGameName(), g.getProviderName());
            }

            // επιλογή παιχνιδιού για αφαίρεση
            System.out.print("\nEnter the number of the game you want to remove: ");
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

            if (choice < 0 || choice >= games.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            Game selectedGame = games.get(choice);
            // επιβεβαίωση από τον παίκτη
            System.out.print("Are you sure you want to hide '" + selectedGame.getGameName() + " (yes/no): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("yes")) {
                // Ο Master θα βρει τον σωστό Worker μέσω H(GameName) και θα προωθήσει το αίτημα 
                Object removeResponse = sendCommand("removeGame", selectedGame);
                System.out.println("Server Response: " + removeResponse);
            } else {
                System.out.println("Operation cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please try again.");
        } catch (Exception e) {
            System.out.println("Error removing game: " + e.getMessage());
        }
    }

    // τροποποιεί τα χαρακτηριστικά ενός παιχνιδιού
    private void modifyGame(Scanner scanner) {
        try {
            System.out.println("\n  Modify Game");
            // λήψη των διαθέσιμων παιχνιδιών από τον Master (μέσω όλων των Workers)
            Object response = sendCommand("getAvailableGames", null); 
            
            if (!(response instanceof List)) {
                System.out.println("No games found or error retrieving data.");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Game> games = (List<Game>) response;
            if (games.isEmpty()) {
                System.out.println("No games available ");
                return;
            }
            
            // εμφάνιση των παιχνιδιών (ενεργών)
            System.out.println("Registered Games:");
            for (int i = 0; i < games.size(); i++) {
                Game g = games.get(i);
                System.out.printf("%d. %s [Provider: %s] [Current Min Bet: %s][Current Risk: %s] \n", 
                    (i + 1), g.getGameName(), g.getProviderName(), g.getMinBet(), g.getRiskLevel());
            }
            
            System.out.print("Select game number to modify: ");
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

            // έλεγχος
            if (choice < 0 || choice >= games.size()) {
                System.out.println("Invalid selection.");
                return;
            }
            // εμφανίζει επιλεγμένο παιχνίδι
            Game selectedGame = games.get(choice);
            showGameDetails(selectedGame);

            // επιλογές για τροποποίηση
            while (true) {
                System.out.println("\nSelect field to modify (or 0 to finish):");
                System.out.println("1. Min Bet");
                System.out.println("2. Risk Level");
                System.out.println("0. Save and Exit");
                System.out.print("Choice: ");
				//input από χρήστη
                String epilogh = scanner.nextLine().trim();
                if (epilogh.equals("0")) {
                    break;
                }
                switch (epilogh) {
                    case "1":
                        System.out.print("Enter new Min Bet (decimal): ");
                        selectedGame.setMinBet(Double.parseDouble(scanner.nextLine().trim()));
                        break;
                    case "2":
                        System.out.print("Enter new Risk Level (low/medium/high): ");
                        String newRisk = scanner.nextLine().trim().toLowerCase();
                        if (newRisk.equals("low") || newRisk.equals("medium") || newRisk.equals("high")) {
                            selectedGame.setRiskLevel(newRisk);
                        } else {
                            System.out.println("Invalid risk level. Use low, medium or high.");
                        }
                        break;
                    default:
                        System.out.println("Invalid selection.");
                }

                System.out.println("\nUpdated game details:");
                showGameDetails(selectedGame);
            }
            // επιστρέφει το παιχνίδι ενημερωμένο για να ενημερωθεί και στους worker
            Object updateResponse = sendCommand("modifyGame", selectedGame);
            System.out.println("Response: " + updateResponse);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input format. Please enter numbers where required.");
        } catch (Exception e) {
            System.out.println("Error modifying game: " + e.getMessage());
        }
    }
	
    //εμφάνιση όλων των χαρακτηριστικών του παιχνιδιού
    private void showGameDetails(Game game) {
        String status = (game.getIsActive() != null && game.getIsActive()) ? "Active" : "Hidden";
        System.out.println("\nCurrent Game Info:");
        System.out.println("Game Name: " + game.getGameName());
        System.out.println("Provider: " + game.getProviderName());
        System.out.println("Stars: " + game.getStars());
        System.out.println("No Of Votes: " + game.getNoOfVotes());
        System.out.println("Game Logo: " + game.getGameLogo());
        System.out.println("Min Bet: " + game.getMinBet());
        System.out.println("Max Bet: " + game.getMaxBet());
        System.out.println("Risk Level: " + game.getRiskLevel());
        System.out.println("Bet Type: " + game.getBetType());
        System.out.println("Jackpot: " + game.getJackpot());
        System.out.println("Is Active: " + status);
    }

    // ΕΜΦΑΝΙΣΗ ΣΤΑΣΤΙΣΤΙΚΩΝ
    private void showProfitLossPerProvider(Scanner scanner) {
        try {
            System.out.println("\n  Provider stats");
            // λήψη των παιχνιδιών από τον Master (μέσω όλων των Workers)
            Object response = sendCommand("getAllGames", null); 
            
            if (!(response instanceof List)) {
                System.out.println("No games found or error retrieving data.");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Game> games = (List<Game>) response;
            if (games.isEmpty()) {
                System.out.println("No games available ");
                return;
            }
            
            // Set για τους providers
            Set<String> uniqueProviders = new HashSet<>();
            System.out.println("Registered Providers:");
            for (Game g : games) {
                uniqueProviders.add(g.getProviderName());
            }

            List<String> uniqueProvidersList = new ArrayList<>(uniqueProviders);
            int i = 1;
            for (String provider : uniqueProvidersList) {
                System.out.println(i + ". " + provider);
                i++;
            }
            
            System.out.print("Select provider to get stats: ");
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            // έλεγχος
            if (choice < 0 || choice >= uniqueProvidersList.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            Object resp = sendCommand("getProviderStats", uniqueProvidersList.get(choice));
            if (resp instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Double> stats = (Map<String, Double>) resp;
                System.out.println("\nStatistics for Provider: " + uniqueProvidersList.get(choice));
                stats.forEach((game, profit) -> {
                    if (!game.equals("Total")) {
                        System.out.println(game + ": " + profit);
                    }
                });
                System.out.println("Total Profit/Loss: " + (stats.get("Total") >= 0 ? "+" : "") + stats.get("Total") + " FUN");
            } else {
                System.out.println("No statistics found or error: " + resp);
            } 
        } catch (Exception e) {
            System.err.println("Error retrieving data: " + e.getMessage());
        }      
    }

    private void showProfitLossPerPlayer(Scanner scanner) {
        try {
            System.out.println("\n  Player stats");
            // λήψη των παικτών από τον Master
            Object response = sendCommand("getAllPlayers", null); 
            
            if (!(response instanceof List)) {
                System.out.println("No players found or error retrieving data.");
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> players = (List<String>) response;
            if (players.isEmpty()) {
                System.out.println("No players available ");
                return;
            }
            
            // Δημιουργία ενός Set για να κρατάμε τους providers
            System.out.println("Registered players:");
            int i = 1;
            for (String player : players) {
                System.out.println(i +". " +player);
                i++;
            }
            
            System.out.print("Select player to get stats: ");
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

            // έλεγχος
            if (choice < 0 || choice >= players.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            Object resp = sendCommand("getPlayerStats", players.get(choice));            
            if (resp instanceof Double) {
                double val = (Double) resp;
                System.out.println("Player: \"" + players.get(choice) + " Total Profit/Loss\": " + (val >= 0 ? "+" : "") + val + " FUN");
            } else {
                System.out.println("No data for player: " + players.get(choice));
            }
        } catch (Exception e) {
            System.err.println("Error retrieving data: " + e.getMessage());
        }
    }
}
