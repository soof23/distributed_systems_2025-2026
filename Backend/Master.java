import com.example.gameapp.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Master {
    private static final int MASTER_PORT = 5055;   // σταθερό port του Master για εισερχόμενες συνδέσεις από Clients
    private static int numNodes;   // Πλήθος Workers που θα διαβαστεί από το config 
    private static List<Integer> workerPorts = new ArrayList<>();   // Λίστα με τις θύρες των Workers
    private static List<Socket> workerSockets = new ArrayList<>();   // Sockets για την επικοινωνία με Workers
    private static String[] hostAddresses = {"10.26.6.202", "10.26.27.44","localhost"};
    /*
    Επικοινωνία Master με SRG
    όταν προστίθεται νέο παιχνίδι ο Master θα ενημερώνει τον SRG
    στέλνοντας του gameName και HashKey
    */
    private static final String SRG_HOST = "10.26.6.202";
    private static final int SRG_PORT = 5060;
    
    //Λίστες με output και input ώστε να μην δημιουργούνται νέα streams σε κάθε ClientHandler
    private static List<ObjectOutputStream> workerOutputs = new ArrayList<>();
    private static List<ObjectInputStream> workerInputs = new ArrayList<>();
    
    // Για συγχρονισμό του Search με τον Reducer
    private static final Map<String, List<Game>> searchWaitMap = new HashMap<>();

    private static final Map<String, Object> statisticsRegistry = new HashMap<>();

    // registry για balance παικτών (in-memory)
    private static final PlayerRegistry playerRegistry = new PlayerRegistry();

    public static void main(String[] args) {
        try {
            // δυναμικός ορισμός πλήθους Workers κατά το initialization
            // Φόρτωση ρυθμίσεων από το αρχείο config.txt
            loadConfig("config.txt");
            System.out.println("Master attempting connections from config with " + numNodes + " workers.");
            System.out.println("Worker ports available: " + workerPorts);
            // εκκίνηση Master Server
            new Master().start();
        } catch (Exception e) {
            System.err.println("Error starting Master server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadConfig(String filename){
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(filename)){
            prop.load(fis);
            // διάβασμα του αριθμού των nodes
            numNodes = Integer.parseInt(prop.getProperty("nodes","0"));
            // δυναμικό διάβασμα των ports
            for(int i=1; i<=numNodes; i++){
                String portValue = prop.getProperty("port"+i);
                if (portValue != null){
                    workerPorts.add(Integer.parseInt(portValue));
                }
            }

            if(workerPorts.size() != numNodes){
                System.err.println("Warning: The number of ports found doesn't match worker nodes value");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Config file not found: " + filename + ". Please create it.");
            System.exit(1);
        } catch (IOException | NumberFormatException e){
            System.err.println("Error reading config file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void connectToWorkers(List<Integer> ports) {
        int i = 1;
        int j = 0;
        for (int port : ports) {
            try {
                String hostAddress = hostAddresses[j];
                // σύνδεση με όλους τους Worker και αποθήκευση των sockets για επαναχρησιμοποίηση
                Socket s = new Socket(hostAddress, port);
                workerSockets.add(s);
                j += 1;
                ObjectOutputStream workerOut = new ObjectOutputStream(s.getOutputStream());
                workerOut.flush();

                ObjectInputStream workerIn = new ObjectInputStream(s.getInputStream());

                workerOutputs.add(workerOut);
                workerInputs.add(workerIn);

                System.out.println("Connected to worker on port " + port + " (Worker " + i + ")");
                i+=1;
            } catch (IOException e) {
                System.err.println("Could not connect to worker on port " + port + " (Worker " + i + ")");
                e.printStackTrace();
            }
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            System.out.println("Master server started on port " + MASTER_PORT);
            System.out.println("Waiting for client connections (Manager, Player, Reducer)...");

            // σύνδεση με τους Workers πριν δεχτεί clients
            connectToWorkers(workerPorts);

            while (true) {
                // αποδοχή νέας σύνδεσης (Manager, Reducer ή Player)
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                // κάθε νέα σύνδεση σε άλλο clienthandler thread
				new ClientHandler(clientSocket, workerSockets).start();
            }
        } catch (IOException e) {
            System.err.println("Master server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static int getNumNodes() {
        return numNodes;
    }

    // Κάθε client εξυπηρετείται σε ξεχωριστό thread
    static class ClientHandler extends Thread {
        private Socket socket;
        private List<Socket> workerSockets;
        private final ObjectOutputStream out;
        private final ObjectInputStream in;

        // αν αυτο ανήκει σε player που έκανε register,
        // κρατάμε το id για να κάνουμε unregister όταν κλείσει η σύνδεση
        private String registeredPlayerId;

        public ClientHandler(Socket socket, List<Socket> workerSockets) throws IOException {
            this.socket = socket; // Socket for Manager, Player, Reducer
            this.workerSockets = workerSockets;

            // αρχικοποίηση streams για επικοινωνία Master <-> Manager, Player, Reducer
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(socket.getInputStream());
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // ανάγνωση εντολής και δεδομένων
                    Object commandObj;
                    Object dataObj;

                    try {
                        commandObj = in.readObject();
                        System.out.println("MASTER READ COMMAND: " + commandObj);
                        dataObj = in.readObject();
                        System.out.println("MASTER READ DATA: " + dataObj);
                    } catch (SocketException e) {
                        System.out.println("Client disconnected.");
                        break;
                    }

                    if (!(commandObj instanceof String)) {
                        out.writeObject("Invalid command format.");
                        out.flush();
                        continue;
                    }

                    /*
                    Όταν ξεκινάει ο Reducer ανοίγει ένα socket προς τον Master και μόλις συνδεθεί, του στέλνει "Reducer established connection". 
                    Μόλις ο Master δει ότι συνδέθηκε ο Reducer, κλειδώνει αυτό το ClientHandler thread μέσα στη μέθοδο handleReducerCommunication.
                    break: για να βγει το thread από το while loop του ClientHandler. το thread είναι ο ακροατής του Reducer
                    */
					if (commandObj instanceof String && "Reducer established connection".equals(commandObj)) {
                        handleReducerCommunication(dataObj);
                        break;
                    }

                    String command = (String) commandObj;
                    System.out.println("Command received: " + command);
                    // καλεί την handleCommand για επεξεργασία της εντολής
                    Object response = handleCommand(command, dataObj);

                    // αποστολή αποτελέσματος πίσω στον Client
                    out.writeObject(response);
                    out.flush();
                }
            } catch (SocketException e) {
                System.out.println("Server closed.");
            } catch (Exception e) {
                System.out.println("Client Handler Error: " + e.getMessage());
            } finally {
                try {
                    if (out!= null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                } catch (Exception e) {
                    System.out.println("Error while closing: " + e.getMessage());
                } finally {
                    try {
                        if (registeredPlayerId != null) {
                            playerRegistry.unregisterActivePlayer(registeredPlayerId);
                            System.out.println("Player disconnected.");
                        }

                        if (out != null) {
                            out.close();
                        }

                        if (in != null) {
                            in.close();
                        }

                        if (socket != null) {
                            socket.close();
                        }
                    } catch (Exception e) {
                        System.out.println("Error while closing." + e.getMessage());
                    }
                }
            }
        }

        private Object handleCommand(String command, Object data) {
            try {
                switch (command) {
                    case "registerPlayer":
                        return handleRegisterPlayer(data);
                    case "getBalance":
                        return handleGetBalance(data);
                    case "addBalance":
                        return handleAddBalance(data);
                    case "getAvailableGames":
                        return handleGetAvailableGames(data);
                    case "search":
                        return handleSearch(data);
                    case "rate":
                        return handleRate(data);
                    case "play":
                        return handlePlay(data);
                    case "addGame":
                        return handleAddGame(data);
                    case"reactivateGame":
                        return handleReactivateGame(data);
                    case "removeGame":
                        return handleRemoveGame(data);
                    case "getAllGames":
                        return handleGetAllGames(data);
                    case "modifyGame":
                        return handleModifyGame(data);
                    case "getProviderStats":
                        return handleGetProviderStats(data);
                    case "getAllPlayers":
                        return handleGetPlayers(data);
                    case "getPlayerStats":
                        return handleGetPlayerStats(data);
                    default: 
                        return "Unknown command.";
                }
            } catch (Exception e) {
                return "Error while handling command: " + command;
            }
        }

        /*
        Το thread δεν τερματίζει, μένει σε loop, περιμένοντας μόνο δεδομένα από τον Reducer.
        Κάθε φορά που ο Reducer τελειώνει μια search ή ένα υπολογισμό στατιστικών, στέλνει ένα Map στον Master.
        Όταν έρθουν τα αποτελέσματα η handleReducerCommunication τα βάζει στο searchWaitMap και καλεί searchWaitMap.notifyAll().
        ξυπνάει το thread του Παίκτη (που είχε κάνει handleSearch και περίμενε με wait()) 
        */
		@SuppressWarnings("unchecked")
        private void handleReducerCommunication(Object initialData) {
            try {
                while (true) {
                    Object data = in.readObject(); // Λήψη aggregated results από τον Reducer
                    if (data instanceof Map) {
                        Map<String, Object> results = (Map<String, Object>) data;
                        synchronized (statisticsRegistry) {
                            // Διαχωρισμός search αποτελεσμάτων από στατιστικά
                            for (String key : results.keySet()) {
                                if (key.startsWith("search_")) {
                                    synchronized (searchWaitMap) {
                                        String searchId = key.substring(7);
                                        Object gamesO = (List<Game>) results.get(key);
                                        if (gamesO instanceof List<?>) {
                                            List<Game> games = new ArrayList<>();
                                            for (Object obj : (List<?>) gamesO) {
                                                if (obj instanceof Game) {
                                                    games.add((Game) obj);
                                                }
                                            }
                                            System.out.println("Master received reduced search result with searchId: " + searchId + "with " + games.size() + " games.");
                                            searchWaitMap.put(searchId, games);
                                        }
                                        searchWaitMap.notifyAll();
                                    }
                                } else if (key.startsWith("provider_") || key.startsWith("player_")) {
                                    // Λογική Στατιστικών: Ενημέρωση του Registry
                                    statisticsRegistry.put(key, results.get(key));
                                }
                            }
                            //searchWaitMap.notifyAll(); // Ξυπνάει το handleSearch
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /*
        Register Player
        ώστε να είναι μοναδικό το κάθε playerId ανά χρονική στιγμή.
        */
        private Object handleRegisterPlayer(Object data) {
            if (!(data instanceof String)) {
                return "Invalid playerId.";
            }
            String playerId = ((String) data).trim();

            if (playerId.isEmpty()) {
                return "Player ID cannot be empty.";
            }

            // αν το socket έχει ήδη κάνει register με αυτό το id -> οκ
            if (registeredPlayerId != null && registeredPlayerId.equals(playerId)) {
                return "REGISTER_OK";
            }

            // αν το socket έχει άλλο registeredPlayerId, το αφαιρούμε πρώτα
            if (registeredPlayerId != null && !registeredPlayerId.equals(playerId)) {
                playerRegistry.unregisterActivePlayer(registeredPlayerId);
                registeredPlayerId = null;
            }

            boolean registered = playerRegistry.registerActivePlayer(playerId);

            if (!registered) {
                return "Player ID already in use.";
            }

            this.registeredPlayerId = playerId; // για να θυμόμαστε ποιο playerId έκανε register

            return "REGISTER_OK";
        }

        /*
        Get Balance
        */
        private Object handleGetBalance(Object data) {
            if (!(data instanceof String)) {
                return "Invalid playerId.";
            }

            String playerId = ((String) data).trim();
            if (playerId.isEmpty()) {
                return "Player ID cannot be empty.";
            }

            // lock για έλεγχο
            Object playerLock = playerRegistry.getPlayerLock(playerId);
            synchronized (playerLock) {
                return playerRegistry.getBalance(playerId);
            }
        }

        /*
        Add Balance
        χρησιμοποιούμε Lock ώστε δύο ταυτόχρονα requests για τον ίδιο παίκτη
        να μην χαλάσουν την τελική τιμή.
        */
        private Object handleAddBalance(Object data) {
            if (!(data instanceof BalanceRequest)) {
                return "Invalid addBalance request.";
            }

            BalanceRequest request = (BalanceRequest) data;
            if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                return "Invalid playerId";
            }

            if (request.getAmount() <= 0) {
                return "Amount must be higher than 0.";
            }

            Object playerLock = playerRegistry.getPlayerLock(request.getPlayerId());
            synchronized (playerLock) {
                double newBalance = playerRegistry.addBalance(request.getPlayerId(), request.getAmount());
                System.out.println("Balance has updated for player: " + request.getPlayerId());
                System.out.println("New Balance is: " + newBalance);
                return newBalance;
            }
        } 

        /*
        Get Available Games
        Ζητάει από όλους τους workers τα active παιχνίδια τους και τα επιστρέφει
        */
        @SuppressWarnings("unchecked")
        private Object handleGetAvailableGames(Object data) {
			// αρχικοποίηση νέας λίστας που θα περιέχει το σύνολο των παιχνιδιών από όλους τους Workers
            List<Game> allAvailableGames = new ArrayList<>();
            
            for (Socket workerSocket : workerSockets) { 
                try {
                    // ανάκτηση των Streams για τον συγκεκριμένο Worker που υπάρχουν ήδη
                    ObjectOutputStream tempOut = getOutput(workerSocket);
                    ObjectInputStream tempIn = getInput(workerSocket);

                    tempOut.reset();
                    tempOut.writeObject("getAvailableGames");
                    tempOut.writeObject(null); // null γιατί η συγκεκριμένη εντολή δεν απαιτεί παραμέτρους δεδομένων
                    tempOut.flush();
            
                    Object response = tempIn.readObject();
                    if (response instanceof List<?>) {
                        // προσθήκη όλων των παιχνιδιών του συγκεκριμένου Worker στην κεντρική λίστα
                        allAvailableGames.addAll((List<Game>) response);
                    }
                } catch (Exception e) {
                    System.err.println("Worker on port " + workerSocket.getPort() + " did not respond.");
                }
            }
            return allAvailableGames;
        }

        /*
        Search
        o Player στέλνει SearchRequest στον Master
        o Master το προωθεί σε όλους τους workers
        κάθε Worker κάνει filtering
        o Master παίρνει τα αποτελέσματα από τον Reducer και τα επιστρέφει στον Player
        */
        private Object handleSearch(Object data) {
            try {
                if (!(data instanceof SearchRequest)) {
                    return "Invalid search request.";
                }
                SearchRequest request = (SearchRequest) data;

                if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                    return "Invalid player ID.";
                }
                String searchId = request.getSearchId();

                // αφαιρούμε παλιά αποτελέσματα με το ίδιο searchId από το searchWaitMap
                synchronized (searchWaitMap) {
                    searchWaitMap.remove(searchId);
                }

                // Ενημέρωση όλων των Workers να φιλτράρουν τα δεδομένα τους και να τα στείλουν στον Reducer
                int i = 0;
                for (Integer port : workerPorts) {
                    try (
                        Socket tempSocket = new Socket(hostAddresses[i], port);
                        ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
                        ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream());
                        ) {
                            tempOut.flush();

							// αποστολή σε worker 
                            tempOut.writeObject("search");
                            tempOut.writeObject(request);
                            tempOut.flush();
							
							// επιβεβαίωση οτι έλαβε το αίτημα
                            Object ack = tempIn.readObject();
                            System.out.println("Search ACK from worker on port: " + port + " " + ack);

                            if (!(ack instanceof String) || !ack.equals("SEARCH_OK")) {
                                System.err.println("Search failed on worker port: " + port);
                            }
                            i++;
                    } catch (Exception e) {
                        System.err.println("Worker on port: " + port + " failed. " + e.getMessage());
                    }
                }
                // λίστα με τα αποτελέσματα (games) του search
                List<Game> searchResults;

                // Ο Master περιμένει το thread του handleReducerCommunication 
                // να τον ειδοποιήσει ότι τα αποτελέσματα συγκεντρώθηκαν
                synchronized (searchWaitMap) {
                    long startTime = System.currentTimeMillis();
                    // loop αναμονής: όσο το searchId δεν υπάρχει στο map και δεν έχουν περάσει 10 δευτερόλεπτα
                    while (!searchWaitMap.containsKey(searchId) && System.currentTimeMillis() - startTime < 10000) {
                        // tο thread του παίκτη sleeps προσωρινά απελευθερώνοντας το lock
                        searchWaitMap.wait(20000); // Αναμονή έως 20 δευτερόλεπτα
                    }
                }

                // Ανάκτηση των reduced αποτελεσμάτων από το searchWaitMap και καθαρισμός της δομής
                searchResults = searchWaitMap.remove(searchId);

				// αν δεν βρέθηκε τίποτα επιστρέφω κενή λίστα
                if (searchResults == null) {
                    return new ArrayList<Game>();
                }

                // ταξινόμηση αποτελεσμάτων με βάση stars (ή votes)
                searchResults.sort ((g1, g2) -> {
                    if (g2.getStars() != g1.getStars()) {
                        return Integer.compare(g2.getStars(), g1.getStars());
                    }
                    return Integer.compare(g2.getNoOfVotes(), g1.getNoOfVotes());
                });

                return searchResults;
            } catch (Exception e) {
                System.err.println("Error in handleSearch: " + e.getMessage());
                e.printStackTrace();
                return "Failed to search games.";
            }
        }

        /*
        Rate
        το rating πηγαίνει μόνο στον worker που έχει το παιχνίδι
        */
        private Object handleRate(Object data) {
            try {
                if (!(data instanceof RateRequest)) {
                    return "Invalid rate request.";
                }

                RateRequest request = (RateRequest) data;

                if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                    return "Invalid Player ID.";
                }

                if (request.getGameName() == null || request.getGameName().trim().isEmpty()) {
                    return "Invalid game name.";
                }

                if (request.getRating() < 1 || request.getRating() > 5) {
                    return "Rating must be from 1 to 5.";
                }

                Socket workerSocket = getWorkerNode(request.getGameName());
                if (workerSocket == null) {
                    return "Could not find worker.";
                }

                ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);

                synchronized (workerOut) {
                    workerOut.writeObject("rate");
                    workerOut.writeObject(request);
                    workerOut.flush();

                    Object workerResponse = workerIn.readObject();

                    if (workerResponse instanceof String) {
                        return workerResponse;
                    }

                    return "Unexpected response from worker.";
                }
            } catch (Exception e) {
                System.err.println("Error in handleRate: " + e.getMessage());
                e.printStackTrace();
                return "Failed to rate game.";
            }
        }

        /*
        Play
        validate request -> παίρνουμε game info από τον worker του παιχνιδιού ->
        ελέγχουμε active, min, max -> κάνουμε lock το balance ->
        ελέγχουμε balance -> στέλνουμε play στον Worker -> new balance
        */
		private Object handlePlay(Object data) {
            try {
                if (!(data instanceof PlayRequest)) {
                    return "Invalid play request.";
                }

                PlayRequest request = (PlayRequest) data;

                if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                    return "Invalid player ID.";
                }

                if (request.getGameName() == null || request.getGameName().trim().isEmpty()) {
                    return "Invalid game name.";
                }

                if (request.getBet() <= 0) {
                    return "Bet amount must be higher than 0.";
                } 

                if (workerSockets == null || workerSockets.isEmpty()) {
                    return "No worker nodes.";
                }
                
                Socket workerSocket = getWorkerNode(request.getGameName());
                if (workerSocket == null) {
                    return "Could not find worker.";
                }

                Game gameInfo = getGameInfoFromWorker(workerSocket, request.getGameName());
                if (gameInfo == null) {
                    return "Game not found.";
                }

                if (gameInfo.getIsActive() == null || !gameInfo.getIsActive()) {
                    return "Game is not active.";
                }

                if (request.getBet() < gameInfo.getMinBet() || request.getBet() > gameInfo.getMaxBet()) {
                    return "Bet amount must be between Min Bet and Max Bet.";
                }

                // χρησιμοποιούμε το ίδιο Lock με το addbalance άρα αυτά τα δύο δεν γίνεται να συμβούν ταυτόχρονα.
                Object playerLock = playerRegistry.getPlayerLock(request.getPlayerId());
                synchronized (playerLock) {
                    double currentBalance = playerRegistry.getBalance(request.getPlayerId());
                    if (request.getBet() > currentBalance) {
                        return "Insufficient balance.";
                    }

                    ObjectOutputStream workerOut = getOutput(workerSocket);
                    ObjectInputStream workerIn = getInput(workerSocket);

                    synchronized (workerOut) {
                        workerOut.writeObject("play");
                        workerOut.writeObject(request);
                        workerOut.flush();

                        Object workerResponse = workerIn.readObject();

                        if (workerResponse instanceof PlayResult) {
                            PlayResult result = (PlayResult) workerResponse;

                            // νέο balance = παλιό balance + καθαρό κέρδος/ζημία
                            double newBalance = currentBalance + result.getNetResult();
                            playerRegistry.setBalance(request.getPlayerId(), newBalance);
                            return result;
                            //return result.getMessage() + "\nRandom Number: " + result.getRandomNumber() + "\nNet Result: " + result.getNetResult() + "\nNew Balance: " + newBalance; 
                        }

                        if (workerResponse instanceof String) {
                            return workerResponse;
                        }

                        return "Unexpected response from worker.";
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in handlePlay: " + e.getMessage());
                e.printStackTrace();
                return "Failed to process play request.";
            }
        }

        // προσθήκη παιχνιδιού από Manager
        private Object handleAddGame(Object data) {
            try {
                System.out.println("Master received addGame command ");
				
				// μεθόδος addingGame: επιστρέφει έναν πίνακα String με αποτελέσματα
                String[] result = addingGame(data);
                boolean success = Boolean.parseBoolean(result[0]);   // αν η διαδικασία ολοκληρώθηκε
                boolean gameExists = Boolean.parseBoolean(result[1]);   // αν το παιχνίδι υπήρχε ήδη
                String status = result.length > 2 ? result[2] : "";   // status: (ACTIVE/INACTIVE/NEW)
                    
                // απάντηση για τον manager
                if (gameExists) {
                    if ("INACTIVE".equals(status)) {
                        return "GAME_INACTIVE"; // Ειδικό μήνυμα για τον Manager
                    }
                    return "Game already exists";
                } else if (success) {
                    return "Game added successfully ";
                } else {
                    return "Failed to add Game ";
                }
            } catch (Exception e) {
                System.err.println("Error processing addGame command: " + e.getMessage());
                e.printStackTrace();
                return "Failed to add Game ";
            }
        }
		
		// returns: {success, gameExists, status}
        private String[] addingGame(Object data){
            try {
                if (!(data instanceof Game)) {
                    System.err.println("Error: Invalid data type. Expected Game object.");
                    return new String[]{"false", "false", "ERROR"};
                }
                Game game = (Game) data;
                String gameName = game.getGameName();
                if (gameName == null || gameName.isEmpty()) {
                    System.err.println("Error: Game name is missing.");
                    return new String[]{"false", "false", "ERROR"};
                }
                
                Socket workerSocket = getWorkerNode(gameName);   // HASHING: σε ποιον Worker ανήκει το παιχνίδι βάσει ονόματος
                Game existingGame = checkIfGameExists(gameName, workerSocket);
                
                if (existingGame != null) {
                    System.out.println("Game " + gameName + " already exists on worker port " + workerSocket.getPort());
                    // επιστρέφουμε status για να δούμε αν το παιχνίδι είναι active ή όχι
                    Boolean isActive = existingGame.getIsActive();
                    String status;
                    // έλεγχος αν το isActive είναι null (για να μην έχουμε null pointer) και αν είναι true ή false για να επιστρέψει το σωστό status
                    if (isActive != null && isActive) {
                        status = "ACTIVE";
                    } else {
                        status = "INACTIVE";
                    }
                    return new String[]{"true", "true", status}; 
                }
                
                // αν το παιχνίδι δεν υπάρχει, το στέλνω για προσθήκη στον κατάλληλο worker
                String command = "addGame";
                System.out.println("Forwarding game to worker on port " + workerSocket.getPort() + ": " + gameName);
                String response = forwardToWorker(workerSocket, command, game);
                boolean success = response != null && !response.startsWith("Error");
                if (success) {
                    // SRG REGISTRATION: δηλώνουμε το παιχνίδι στον SRG, για να μπορεί να παράγει έγκυρους τυχαίους αριθμούς
                    boolean srgRegistered = registerGameSRG(game);
                    if (!srgRegistered) {
                        System.err.println("Game added to worker. Failed to register in SRG");
                        return new String[] {"false", "false"};
                    }
                    System.out.println("Game '" + gameName + "' added successfully to worker on port " + workerSocket.getPort());
                } else {
                    System.err.println("Failed to add game to worker: " + response);
                }            
                return new String[]{String.valueOf(success), "false", "NEW"};
            } catch (Exception e) {
                System.err.println("Error adding game: " + e.getMessage());
                e.printStackTrace();
                return new String[]{"false", "false", "ERROR"};
            }
        }
		
        private Object handleReactivateGame(Object data) {
            try {
                Game game = (Game) data;
                Socket workerSocket = getWorkerNode(game.getGameName());
                System.out.println("Forwarding reactivate request to worker on port: " + workerSocket.getPort());

                String workerResponse = forwardToWorker(workerSocket, "reactivateGame", data);
                return workerResponse;
            } catch (Exception e) {
                System.err.println("Error reactivating game: " + e.getMessage());
                e.printStackTrace();
                return "Failed to reactivate game.";
            }
        }

        /*
        Helper Method για να κάνει register το παιχνίδι στον SRG
        */
        private boolean registerGameSRG(Game game) {
            try (
                Socket srgSocket = new Socket(SRG_HOST, SRG_PORT);
                ObjectOutputStream srgOut = new ObjectOutputStream(srgSocket.getOutputStream());
                ObjectInputStream srgIn = new ObjectInputStream(srgSocket.getInputStream());
            ) {
                srgOut.flush();

                srgOut.writeObject("registerGame");
                srgOut.writeObject(game.getGameName());
                srgOut.writeObject(game.getHashKey());
                srgOut.flush();

                Object response = srgIn.readObject();
                System.out.println("SRG register response: " + response);

                return (response instanceof String) && ((String) response).toLowerCase().contains("success");
            } catch (Exception e) {
                System.err.println("Error registering game in SRG: " + e.getMessage());
                return false;
            }
        }

        private Object handleRemoveGame(Object data) {
            try {
                Game game = (Game) data;
                String gameName = game.getGameName();
                System.out.println("Processing request to remove game '" +  gameName);
            
                // βρίσκει ποιός worker διαχειρίζεται το παιχνίδι
                Socket workerSocket = getWorkerNode(gameName);
                System.out.println("Forwarding game removal request to worker on port: " + workerSocket.getPort());
                
				// εντολή που θα στείλει στον worker
                String command = "removeGame";
                // προωθεί την εντολή στον worker για να γίνει κ εκεί η αλλαγή + παίρνει απάντηση
                String response = forwardToWorker(workerSocket, command, data);
                boolean success = response != null && response.contains("success");
                if (success) {
                    return "Game '" + gameName + "' successfully marked as removed in worker's memory";
                } else {
                    return "Failed to remove game from worker's memory: " + response;
                }
            } catch (Exception e) {
                System.err.println("Error removing game: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

		// επιστρέφει όλα τα παιχνίδια (ενεργά και μη)
        private Object handleGetAllGames(Object data){
            try {
                List<Game> allGames = new ArrayList<>();

                for (Socket workerSocket : workerSockets) {
                    ObjectOutputStream workerOut = getOutput(workerSocket);
                    ObjectInputStream workerIn = getInput(workerSocket);

                    synchronized (workerOut) {
                        workerOut.writeObject("getAllGames");
                        workerOut.flush();

                        Object response = workerIn.readObject();

                        if (response instanceof List<?>) {
                            List<?> workerGames = (List<?>) response;
                            for (Object obj : workerGames) {
                                if (obj instanceof Game) {
                                    allGames.add((Game) obj);
                                }
                            }
                        }
                    }
                }
                return allGames;
            } catch (Exception e) {
                System.err.println("Error in handleGetAllGames: " + e.getMessage());
                e.printStackTrace();
                return "Failed to retrieve all games.";
            }
        }

        private Object handleModifyGame(Object data) {
            try {
                // βρίσκει ποιός worker διαχειρίζεται το παιχνίδι
                Game game = (Game) data;
                Socket workerSocket = getWorkerNode(game.getGameName());
                System.out.println("Forwarding game modification request to worker on port: " + workerSocket.getPort());

                // εντολή που θα στείλει στον worker
                String command = "modifyGame";
				
                // προωθεί την εντολή στον worker για να γίνει κ εκεί η αλλαγή + παίρνει απάντηση
                String workerResponse = forwardToWorker(workerSocket, command, data);
                boolean success = workerResponse != null && !workerResponse.startsWith("Error") && workerResponse.contains("successfully modified game");
                if (success) {
                    return "Successfully modified game: " + game.getGameName();
                } else {
                    return "Failed to modify game. Worker said: " + workerResponse;
                }
            } catch (Exception e) {
                System.err.println("Error modifing game: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        private Object handleGetProviderStats(Object prov) {
            try {
                System.out.println("Retrieving profit/loss data for provider: " + prov);
                if (!(prov instanceof String)) {
                    return "Invalid Provider Name.";
                }

                String providerName = (String) prov;
                String key = "provider_" + providerName;

                synchronized (statisticsRegistry) {
                    if (statisticsRegistry.containsKey(key)) {
                        return statisticsRegistry.get(key); //επιστρέφει Map με games και Total
                    } else {
                        return "No statistics found for provider: " + providerName;
                    }
                } 
            } catch (Exception e) {
                System.err.println("Error retrieving provider stats: " + e.getMessage());
                return "Error processing request";
            }
        }

        private Object handleGetPlayers(Object data) {
            Set<String> actives = playerRegistry.getActivePlayers(); 
            return new ArrayList<>(actives);
        }

        private Object handleGetPlayerStats(Object data) {
            try {
                if (!(data instanceof String)) {
                    return "Invalid Player ID.";
                }
                String playerId = (String) data;
                String key = "player_" + playerId;

                synchronized (statisticsRegistry) {
                    if (statisticsRegistry.containsKey(key)) {
                        return statisticsRegistry.get(key); //επιστρέφει Double (συνολικό Profit/Loss)
                    } else {
                        return 0.0;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error retrieving player stats: " + e.getMessage());
                return "Error processing request";
            }
        }

        /* 
        υπολογισμός του Worker Node για ένα παιχνίδι
        Hash στο όνομα του παιχνιδιού => το ίδιο παιχνίδι θα εξυπηρετείται πάντα από τον ίδιο Worker
        returns: Socket του αντίστοιχου Worker
        */
        private Socket getWorkerNode(String gameName) {
            int hash = gameName.hashCode();
            return workerSockets.get(Math.abs(hash) % workerPorts.size());
        }

        // TCP επικοινωνία με Worker (αποστολή δεδομένων και λήψη απάντησης)
        private String forwardToWorker(Socket workerSocket, String command, Object data) {
            try {
                System.out.println("Forwarding to worker on port " + workerSocket.getPort() + ": " + command);
                
                // χρήση ήδη ανοιγμένων Streams για το συγκεκριμένο Socket
				ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);
                
                // synchronized αν πολλά threads στείλουν στον ίδιο Worker
				synchronized(workerOut){
                    try {
                        // στέλνει στον worker εντολή και αντικείμενο
                        workerOut.writeObject(command);
                        workerOut.writeObject(data);
                        workerOut.flush();
                        // παίρνει απάντηση από τον worker
                        String response = (String) workerIn.readObject();
                        System.out.println("Response from worker: " + response);
                        return response;
                    } catch (IOException e) {
                        System.err.println("Failed to connect to worker on port " + workerSocket.getPort() + ": " + e.getMessage());
                        return "Error: Failed to connect to worker node - " + e.getMessage();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in forwardToWorker: " + e.getMessage());
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }
        }

        //Ελέγχει αν ένα παιχνίδι είναι ήδη αποθηκευμένο στη μνήμη του Worker. 
        private Game checkIfGameExists(String gameName, Socket workerSocket) {
            try {
                System.out.println("Checking if game '" + gameName + "' exists on worker port " + workerSocket.getPort());
                String command = "getGameInfo";

                ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);

                // στέλνει εντολή και το όνομα του παιχνιδιού στον worker
                workerOut.writeObject(command);
                workerOut.writeObject(gameName);
                workerOut.flush();

                // διαβάζει απάντηση απο worker
                Object response = workerIn.readObject();

                // επιστρέφει το παιχνίδι αν υπάρχει στον worker αλλίως null
                if (response instanceof Game) {
                    System.out.println("Game '" + gameName + "' exists on worker port " + workerSocket.getPort());
                    return (Game) response;
                } else {
                    System.out.println("Game '" + gameName + "' does not exist on worker port " + workerSocket.getPort());
                    return null;
                }
            } catch (Exception e) {
                System.err.println("Error checking if game exists: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        /*
        returns: το ήδη ανοιχτό ObjectOutputStream ενός Worker από τη λίστα workerOutputs
        => ο Master στέλνει αντικείμενα στον σωστό Worker χωρίς να ανοίγει νέα σύνδεση
        */
		private ObjectOutputStream getOutput(Socket socket) {
            int index = workerSockets.indexOf(socket);   // θέση του socket στη λίστα workerSockets για να βρούμε το αντίστοιχο stream
            if (index<0) throw new IllegalArgumentException("Socket not found in workerSockets: " + socket);
            return workerOutputs.get(index);
        }
		
        /*
        returns: το ήδη ανοιχτό ObjectInputStream ενός Worker από τη λίστα workerInputs
        => ανάγνωση απαντήσεων από τον Worker
        */
        private ObjectInputStream getInput(Socket socket) {
            int index = workerSockets.indexOf(socket);
            if (index<0) throw new IllegalArgumentException("Socket not found in workerSockets: " + socket);
            return workerInputs.get(index);
        }

        /*
        Ζητάει από τον worker τα στοιχεία ενός παιχνιδιού
        για active check και min/max check
        */
        private Game getGameInfoFromWorker(Socket workerSocket, String gameName) {
            try {
                ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);

                synchronized (workerOut) {
                    workerOut.writeObject("getGameInfo");
                    workerOut.writeObject(gameName);
                    workerOut.flush();

                    Object response = workerIn.readObject();

                    if (response instanceof Game) {
                        return (Game) response;
                    }
                    return null;
                }
            } catch (Exception e) {
                System.err.println("Error in getGameInfoFromWorker: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }

    // in-memory registry για τα balances και locks του κάθε playerId
    // τα locks χρειάζονται αν γίνουν παράλληλα δύο requests για ένα playerId
    // πχ αν κάνει κάποιος addBalance και play ταυτόχρονα
    static class PlayerRegistry {
        // balance ανα playerId
        private final Map<String, Double> balances = new HashMap<>();
        // lock ανα playerId
        private final Map<String, Object> playerLocks = new HashMap<>();
        // active player ids ώστε να είναι μοναδικά.
        private final Set<String> activePlayers = new HashSet<>();

        public synchronized Object getPlayerLock(String playerId) {
            if (!playerLocks.containsKey(playerId)) {
                playerLocks.put(playerId, new Object());
            }
            return playerLocks.get(playerId);
        }

        public synchronized double getBalance(String playerId) {
            if (balances.containsKey(playerId)) {
                return balances.get(playerId);
            }
            // αν δεν υπάρχει το id
            return 0.0;
        }

        public synchronized double addBalance(String playerId, double amount) {
            double current;
            if (balances.containsKey(playerId)) {
                current = balances.get(playerId);
            } else {
                current = 0.0;
            }

            double newb = current + amount;
            balances.put(playerId, newb);
            return newb;
        }

        public synchronized void setBalance(String playerId, double newBalance) {
            balances.put(playerId, newBalance);
        }

        // register μόνο αν δεν είναι ήδη active.
        public synchronized boolean registerActivePlayer(String playerId) {
            if (activePlayers.contains(playerId)) {
                return false;
            }
            activePlayers.add(playerId);
            return true;
        }

        public synchronized void unregisterActivePlayer(String playerId) {
            activePlayers.remove(playerId);
        }

        public synchronized Set<String> getActivePlayers() {
            return new HashSet<>(activePlayers);
        }
    }
}
