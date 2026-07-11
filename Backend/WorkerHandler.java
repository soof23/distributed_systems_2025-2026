import java.io.*;
import java.net.*;
import java.util.*;
import com.example.gameapp.*;
/*
Χειρίζεται αιτήματα από τον Master.
*/

public class WorkerHandler extends Thread {
    private Socket masterSocket;
    private String workerId;
    private Worker worker;
	
    private ObjectOutputStream out;   // to master
    private ObjectInputStream in;   // from master

	// Lock για συγχρονισμό κατά την αποστολή δεδομένων στον Reducer
    private static final Object reducerLock = new Object();

    private static final String SRG_HOST = "10.26.6.202";
    private static final int SRG_PORT = 5060;

    // static map: αποθηκεύει Game objects, κοινή για όλους τους handlers του worker
    private static final Map<String, Game> gameCache = new HashMap<>();

    // άθροισμα των ratings ώστε ο μέσος όρος να ενημερώνεται όταν γίνονται πολλά rate
    private static final Map<String, Integer> gameRatingSum = new HashMap<>();

    //constructor worker handler
    public WorkerHandler(Socket masterSocket, String workerId, Worker worker) throws IOException {
        this.masterSocket = masterSocket;
        this.workerId = workerId;
        this.worker = worker;

		// αρχικοποίηση streams
        this.out = new ObjectOutputStream(masterSocket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(masterSocket.getInputStream());
    }

    @Override
    public void run() {
        try {  
            while (true) {
                Object commandObj = in.readObject();
                // Έλεγχος για την εντολή που λαμβάνεται από master και εκτέλεση της αντίστοιχης ενέργειας
                if (commandObj instanceof String) {
                    String command = (String) commandObj;
                    System.out.println("\n[Worker " + workerId + "] Έλαβε εντολή ->  " + command);
                    switch (command) {
                        case "addGame":
                            Game newGame = (Game) in.readObject(); // Διαβάζουμε το αντικείμενο Game που στέλνει ο Master
                            System.out.println("[Worker " + workerId + "] Adding game: " + newGame.getGameName());
                            String addResponse = addGame(newGame); // Προσθέτουμε το νέο παιχνίδι στον χάρτη των παιχνιδιών
                            out.writeObject(addResponse);
                            out.flush();
                            break;
                        case "removeGame":
                            Game gameToRemove = (Game) in.readObject(); // Διαβάζουμε το όνομα του παιχνιδιού που θέλουμε να αφαιρέσουμε
                            System.out.println("[Worker " + workerId + "] Processing removeGame operation");
                            String remRsponse = DeactivateGame(gameToRemove);
                            out.writeObject(remRsponse);
                            out.flush();
                            break;
                        case "modifyGame":
                            System.out.println("[Worker " + workerId + "] Processing modify game operation");        
                            Game modifiedGame = (Game) in.readObject(); // Διαβάζουμε το αντικείμενο Game με τις νέες πληροφορίες
                            String modResponse = modifyGame(modifiedGame); // Επεξεργαζόμαστε το παιχνίδι στον χάρτη των παιχνιδιών
                            out.writeObject(modResponse);
                            out.flush();
                            break;
                        case "GET_PLAYER_PROFITS":
                            //response = handlePlayerProfits(command);
                            break;
                        case "GET_GAME_PROFITS":
                            //response = handleGameProfits(command);
                            break;
                        case "play":
                            Object playData = in.readObject();
                            if (playData instanceof PlayRequest) {
                                PlayRequest request = (PlayRequest) playData;
                                Object result = handlePlay(request);
                                out.writeObject(result);
                            } else {
                                out.writeObject("Invalid play request");
                            }
                            out.flush();
                            break;
                        case "search":
                            Object searchData = in.readObject();
                            if (searchData instanceof SearchRequest) {
                                SearchRequest request = (SearchRequest) searchData;
                                List<Game> filteredGames = handleSearch(request);

                                System.out.println("WORKER " + workerId + "filteredGames size = " + filteredGames.size());

                                boolean reducerSend = false;
                                // στέλνουμε τα partial results στον Reducer
                                // αυτός τα συγκεντρώνει από όλους τους workers
                                // και μετά ενημερώνει τον Master
                                synchronized (reducerLock){
                                    Map<String,Object> mapResult = new HashMap<>();
                                    mapResult.put("searchId", request.getSearchId()); // το playerId ως key
                                    mapResult.put("games", filteredGames);

                                    System.out.println("Worker " + workerId + "will send search result to Reducer with searchId: " + request.getSearchId() + ", games found: " + filteredGames.size());
            
									// σύνδεση με Reducer για αποστολή αποτελεσμάτων αναζήτησης
                                    try (Socket tempSocket = new Socket("10.26.6.202", 5070);
                                        ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream())) {
                                        tempOut.flush();
                                        tempOut.writeObject("mapResult");
                                        tempOut.writeObject(mapResult);
                                        tempOut.flush();

                                        reducerSend = true;
                                        System.out.println("Worker " + workerId + " successfully sent search result to Reducer.");
                                    } catch (IOException e) { 
                                        System.err.println("Reducer error during search"); 
                                    }
                                }
                                
                                if (reducerSend) {
                                    out.writeObject("SEARCH_OK");
                                } else {
                                    out.writeObject("SEARCH_FAIL");
                                }
                                out.flush();
                            } else {
                                out.writeObject("Invalid search request.");
                                out.flush();
                            }
                            break;
                        case "getAvailableGames":
                            try {
                                System.out.println("[Worker " + workerId + "] Getting available games");
                                List<Game> availableGames = getAvailableGames();
                                
                                out.writeObject(availableGames);
                                out.flush();
                                out.reset();
                            } catch (Exception e) {
                                System.err.println("[Worker " + workerId + "] Error processing getAvailableGames: " + e.getMessage());
                                out.writeObject("Error in getting available games");
                                out.flush();
                            }
                            break;
                        case "rate":
                            Object rateData = in.readObject();
                            if (rateData instanceof RateRequest) {
                                RateRequest request = (RateRequest) rateData;
                                String result = handleRate(request);
                                out.writeObject(result);
                            } else {
                                out.writeObject("Invalid rate request.");
                            }
                            out.flush();
                            break;
                        case "getAllGames":
                            try {
                                System.out.println("[Worker " + workerId + "] Getting all games");
                                List<Game> allGames = getAllGames();
                                out.writeObject(allGames);
                                out.flush();
                            } catch (Exception e) {
                                System.err.println("[Worker " + workerId + "] Error processing getAllGames: " + e.getMessage());
                                out.writeObject("Error in getting all games");
                                out.flush();
                            }
                            break;
                        case "getGameInfo":
                            try {
                                Object data = in.readObject();
                                String gameName = ((String) data).trim();
                                System.out.println("[Worker " + workerId + "] Getting info for game: " + data);
                                synchronized(gameCache){
                                    if (gameCache.containsKey(gameName)) {
                                        Game game = gameCache.get(gameName);
                                        System.out.println("[Worker " + workerId + "] Found game in cache: " + gameName);
                                        out.reset();
										out.writeObject(game);
                                        out.flush();
                                    } else {
                                        System.out.println("[Worker " + workerId + "] Game not found in cache: " + gameName);
                                        out.writeObject("Game not found");
                                        out.flush();
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("[Worker " + workerId + "] Error processing getGameInfo: " + e.getMessage());
                                out.writeObject("Error: " + e.getMessage());
                                out.flush();
                            }
                            break;
                        case "reactivateGame":
                            Game gameToReactivate = (Game) in.readObject();
                            System.out.println("[Worker " + workerId + "] Reactivating game: " + gameToReactivate.getGameName());
                            String reactResponse = reactivateGame(gameToReactivate);
                            out.writeObject(reactResponse);
                            out.flush();
                            break;
                        default:
                            try {
                                in.readObject();
                            } catch (Exception ignored) {
                            }
                            break;
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println("[Worker " + workerId + "] Η σύνδεση τερματίστηκε από τον Master.");
        } catch (IOException | ClassNotFoundException e) { 
            System.err.println("[Worker " + workerId + "] Σφάλμα Handler: " + e.getMessage());
        } finally {
            try { 
                if (in != null) {
                    in.close();	
                }
                if(out != null) {
                    out.close();
                }
                try {
                    masterSocket.close();
                    System.out.println("[Worker " + workerId + "] Connection closed");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private synchronized String addGame(Game game) {
        try {
            synchronized(gameCache){
                if (game != null && !gameCache.containsKey(game.getGameName())){
                    gameCache.put(game.getGameName(), game);

                    // αρχικοποίηση των ratings όταν μπαίνει το παιχνίδι, stars * votes
                    gameRatingSum.put(game.getGameName(), game.getStars() * game.getNoOfVotes());
                    System.out.println("Worker (Id: " + workerId + "): Προστέθηκε το παιχνίδι -> " + game.getGameName());
                    return "Game added";
                }
                return "Game already exists or game not found";
            }
        } catch (Exception e) {
            System.out.println("failed to add game " + e.getMessage());
            return "Failed to add game";
        }
    }

    private synchronized String DeactivateGame(Game gameToRemove) {
        try {
            synchronized(gameCache){
                System.out.println("[Worker "+ workerId + "] Hiding game "+ gameToRemove.getGameName());
                if (!gameCache.containsKey(gameToRemove.getGameName())){
                    System.out.println("[Worker "+workerId + "Game not in cache "+gameToRemove.getGameName());
                    return "Game not found";
                }
                Game game = gameCache.get(gameToRemove.getGameName());
                game.setIsActive(false);
                System.out.println("Worker (Id: " + workerId + "): Απενεργοποιήθηκε το παιχνίδι -> " + gameToRemove.getGameName());
                return "success";
            }
        } catch (Exception e) {
            System.out.println("failed to deactivate game");
            return "Failed to remove game";
        }
    }
	
    //κάνει reactivate ένα παιχνίδι που έχει γίνει removed
    private synchronized String reactivateGame(Game gameToReactivate) {
        try {
            synchronized(gameCache){
                if (!gameCache.containsKey(gameToReactivate.getGameName())){
                    return "Game not found in cache";
                }
                Game game = gameCache.get(gameToReactivate.getGameName());
                game.setIsActive(true);
                System.out.println("Worker (Id: " + workerId + "): Ενεργοποιήθηκε ξανά το παιχνίδι -> " + gameToReactivate.getGameName());
                return "Game reactivated successfully.";
            }
        } catch (Exception e) {
            System.out.println("failed to reactivate game");
            return "Failed to reactivate game";
        }
    }

    private synchronized String modifyGame(Game modifiedGame) {
        //edw kanonika prin ananewsei prepei na ananewnei kai ta statistika.
        try {
            synchronized(gameCache){
                if (!gameCache.containsKey(modifiedGame.getGameName())){
                    System.out.println("game does not exist");
                    return "Game does not exist";
                }
                Game game = gameCache.get(modifiedGame.getGameName());
                game.setRiskLevel(modifiedGame.getRiskLevel());
                game.setMinBet(modifiedGame.getMinBet());
                System.out.println("Worker (Id: " + workerId + "): Επεξεργάστηκε το παιχνίδι -> " + modifiedGame.getGameName());
                return "successfully modified game";
            }
        } catch (Exception e) {
            System.out.println("failed to modify game " + e.getMessage());
            return "Failed to modify game";
        }
    }

    /*
    Get Available Games (μόνο active)
    */
    private synchronized List<Game> getAvailableGames() {
        List<Game> availableGames = new ArrayList<>();
        synchronized (gameCache){
            for (Game game : gameCache.values()) {
                if (game.getIsActive() != null && game.getIsActive()) {
                    availableGames.add(game);
                }
            }
        }
        return availableGames;
    }

    /*
    Get All Games (active και όχι)
    */
    private synchronized List<Game> getAllGames() {
        synchronized (gameCache){
            return new ArrayList<>(gameCache.values());
        }
    }

    /*
    Play
    Ροή:
    validation -> εύρεση παιχνιδιού ->
    έλεγχος active/ min bet/ max bet ->
    αίτημα στον SRG για number και hash ->
    επαλήθευση hash -> υπολογισμός jackpot/κέρδους ->
    ενημέρωση αποτελέσματος -> επιστροφή PlayResult στον Master
    */
    private Object handlePlay(PlayRequest request) throws IOException{
        try {
            // ωασικοί έλεγχοι
            if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                return "Invalid Player ID.";
            }

            if (request.getGameName() == null || request.getGameName().trim().isEmpty()) {
                return "Invalid game name.";
            }

            if (request.getBet() <= 0) {
                return "Bet must be higher than 0.";
            }

            // βρίσκουμε το παιχνίδι
            String gameName = request.getGameName();
            String playerId = request.getPlayerId();
            double betAmount = request.getBet();
            
            // Αναζήτηση του παιχνιδιού στην cache του Worker
            Game game = gameCache.get(gameName);
            if (game == null) {
                return "Game not found.";
            }

            if (game.getIsActive() == null || !game.getIsActive()) {
                return "Game is not active.";
            }

            // min/max bet έλεγχος
            if (betAmount < game.getMinBet() || betAmount > game.getMaxBet()) {
                return "Bet amount must be between Min Bet and Max Bet.";
            }

            // ζητάμε random αριθμό από srg
            SrgResponse response = requestSRG(gameName);

            if (response == null) {
                return "SRG failed.";
            }

            int srgNumber = response.getRandomNumber();
            String receivedHash = response.getHash();

            // έλεγχος local hash
            // o worker υπολογίζει πάλι sha256 και συγκρίνει
            String localHash = sha256(srgNumber + game.getHashKey());

            if (!localHash.equals(receivedHash)) {
                return "SRG verification failed.";
            }
            if (srgNumber < 0) {
                return "Failed to get random number from SRG.";
            }

            // υπολογισμός αποτελέσματος
            double returnAmount = 0.0;
            boolean jackpotWin =  false;

            // αν αριθμός % 100 = 0 -> JACKPOT
            // αλλιώς έχουμε multiplier (%10) -> index από riskTable
            if (srgNumber % 100 == 0) {
                returnAmount = betAmount * game.getJackpot();
                jackpotWin = true;
            } else {
                int outcomeIndex = srgNumber % 10;
                double multiplier = game.getRiskTable()[outcomeIndex];
                returnAmount = betAmount * multiplier;
            }

            // net result = καθαρό κέρδος = επιστροφή - bet
            double netResult = returnAmount - betAmount;

            worker.updateLocalStats(gameName, playerId, betAmount, netResult);

            // synchronized για να μην έχουμε πρόβλημα σε παράλληλα play
            // ενημέρωση στατιστικών - αποστολή εσόδων/εξόδων στον reducer
            synchronized (reducerLock) {
                Map<String, Object> statsUpdate = new HashMap<>();
                statsUpdate.put("ProviderName", game.getProviderName());
                statsUpdate.put("GameName", game.getGameName());
                statsUpdate.put("PlayerID", playerId);
                statsUpdate.put("ProfitLoss", netResult);

                try (Socket tempSocket = new Socket("10.26.6.202", 5070);
                    ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream())) {
                    tempOut.writeObject("mapResult");
                    tempOut.writeObject(statsUpdate);
                    tempOut.flush();
                } catch (IOException e) {
                    System.err.println("Reducer error during play stats");
                }
            }

            // μήνυμα για τον παίκτη
            String message;
            if (jackpotWin) {
                message = "Jackpot! You won " + returnAmount;
            } else if (netResult > 0) {
                message = "You won " + returnAmount;
            } else if (netResult == 0) {
                message = "You got your bet back.";
            } else {
                message = "You lost. Return amount: " + returnAmount;
            }

            // επιστροφή αποτελέσματος στον Master
            return new PlayResult(
                playerId,
                gameName,
                betAmount,
                srgNumber,
                returnAmount,
                netResult,
                jackpotWin,
                message
            );
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while processing play request.";
        }
    }

    /*
    Rate
    Ενημερώνει stars και votes για το game
    */
    private synchronized String handleRate(RateRequest request) {
        try {
            if (request.getPlayerId() == null || request.getPlayerId().trim().isEmpty()) {
                return "Invalid Player ID.";
            }

            if (request.getGameName() == null || request.getGameName().trim().isEmpty()) {
                return "Invalid game name.";
            }

            if (request.getRating() < 1 || request.getRating() > 5) {
                return "Rating must be between 1 and 5.";
            }

            synchronized (gameCache) {
                Game game = gameCache.get(request.getGameName());

                if (game == null) {
                return "Game not found.";
                }

                if (game.getIsActive() == null || !game.getIsActive()) {
                    return "Game is not active.";
                }

                String gameName = game.getGameName();
                int currentVotes = game.getNoOfVotes();

                if (!gameRatingSum.containsKey(gameName)) {
                    gameRatingSum.put(gameName, game.getStars() * currentVotes);
                }

                int oldSum = gameRatingSum.get(gameName);
                int newSum = oldSum + request.getRating();
                int newVotes = currentVotes + 1;

                double average = newSum / (double) newVotes;
                int roundedaverage = (int) Math.round(average);

                gameRatingSum.put(gameName, newSum);
                game.setNoOfVotes(newVotes);
                game.setStars(roundedaverage);

                return "Rating submitted successfully. New Stars: " + game.getStars() + "\nNew Votes: " + game.getNoOfVotes();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while rating game.";
        }
    }

    /*
    Search
    */
    private synchronized List<Game> handleSearch(SearchRequest request) {
        List<Game> filteredGames = new ArrayList<>();
        try {
            synchronized (gameCache){
                for (Game game : gameCache.values()) {
                    // ο παίκτης δεν βλέπει τα inactive παιχνίδια.
                    if (game.getIsActive() == null || !game.getIsActive()) {
                        continue;
                    }

                    boolean match = true;

                    // filter by stars
                    if (request.getStars() != null) {
                        if (game.getStars() < request.getStars()) {
                            match = false;
                        }
                    }

                    // filter by bet type
                    if (request.getBetType() != null) {
                        if (game.getBetType() == null || !game.getBetType().equalsIgnoreCase(request.getBetType())) {
                            match = false;
                        }
                    }

                    // filter by risk level
                    if (request.getRiskLevel() != null) {
                        if (game.getRiskLevel() == null || !game.getRiskLevel().equalsIgnoreCase(request.getRiskLevel())) {
                            match = false;
                        }
                    }

                    if (match) {
                        filteredGames.add(game);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filteredGames;
    }

    /*
    Επικοινωνία με SRG
    o Worker ανοίγει TCP σύνδεση προς SRG
    ζητάει τυχαίο αριθμό για το συγκεκριμένο παιχνίδι
    */
    private SrgResponse requestSRG(String gameName) {
        try {
            Socket socket = new Socket(SRG_HOST, SRG_PORT);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            out.flush();
            
            out.writeObject("generate");
            out.writeObject(gameName);
            out.flush();

            Object response = in.readObject();
            if (response instanceof SrgResponse) {
                return (SrgResponse) response;
            }
        } catch (Exception e) {
            System.out.println("SRG Error.");
        }
        return null;
    }

    /*
    Hash
    Χρησιμοποιείται στην επαλήθευση της SRG απάντησης
    */
    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Exception.";
        }
    }
}