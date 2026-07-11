import java.io.*;
import java.net.*;
import java.util.*;

/*
Dummy Console App

Συνδέεται με τον Master
Στέλνει requests
Εμφανίζει τα αποτελέσματα στον Player

Λειτουργίες:
προβολή balance
προσθήκη balance
εμφάνιση διαθέσιμων παιχνιδιών
search με φίλτρα
play game
rate game
*/

public class Player {
	
	// σύνδεση με master
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 5055;

    // socket και streams
    private Socket masterSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String playerId;
    private double balance = 0.0;

    // εκκίνηση dummy app
    public static void main(String[] args){
        new Player().start();
    }
  
	// σύνδεση με master server
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
			System.exit(1);
		}
	}

    // αποσύνδεση από master
    private void disconnectFromMaster(){
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if(masterSocket != null) masterSocket.close();
            System.out.println("Disconnected from Master server");
        } catch (Exception e) {
            System.err.println("Error disconnecting from Master server: " + e.getMessage());
        }
    }

	/*
    Main menu for dummy app:
    Ροή:
    Σύνδεση με master
    Αρχικοποίηση playerId + register
    Εμφάνιση αρχικού μενού
    */
	public void start() {
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Player started.");
			// σύνδεση με τον Master
			connectToMaster();

			// ζητάμε playerId και κάνουμε register μέσω Master
			initializePlayer(scanner);
			try {
				while (true) {
					System.out.println("MENU:");
					System.out.println("1. Balance");
					System.out.println("2. Games");
					System.out.println("3. Exit");
					System.out.println("Enter Action: ");

					String action = scanner.nextLine().trim();

					switch (action) {
						case "1":
							balance(scanner);
							break;
						case "2":
							gamesMenu(scanner);
							break;
						case "3":
							System.out.println("Exiting...");
							return;
						default:
							System.out.println("Unknown action. Please try again.");
					}
				}
			} finally {
				disconnectFromMaster();
			}
		} catch (Exception e) {
			System.err.println("Error in Player: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	Αρχικά ζητάμε playerId και κάνουμε register
	Αν το playerid είναι ήδη active, ζητάμε άλλο.
	*/
    private void initializePlayer(Scanner scanner) {
        while (true) {
            System.out.println("Enter your Player ID: ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.println("Player ID cannot be empty.");
                continue;
            }
            Object response = sendCommand("registerPlayer", input);

            if (response instanceof String) {
                String message = (String) response;

                if (message.equals("REGISTER_OK")) {
                    this.playerId = input;
                    System.out.println("Welcome: " + this.playerId);
                    break;
                } else {
                    System.out.println(message);
                }
            } else {
                System.out.println("Could not register Player. Try again.");
            }
        }
    }

    /*
    Balance menu
    εμφανίζει current balance
    επιτρέπει προσθήκη balance
    */
    private void balance(Scanner scanner) {
        try {
            // ζητάμε από τον Master το τελευταίο υπόλοιπο
            refreshBalance();

            System.out.println("Current Balance: " + balance);
            System.out.println("1. Add Balance");
            System.out.println("2. Back");
            System.out.println("Enter Action: ");

            String action = scanner.nextLine().trim();

            switch (action) {
                case "1":
                    addBalance(scanner);
                    break;
                case "2":
                    return;
                default:
                    System.out.println("Unknown action. Please enter action.");
            }
        } catch (Exception e) {
            System.out.println("Error in balance menu: " + e.getMessage());
        }
    }

    /*
    Add Balance
    Διαβάζει το ποσό από τον χρήστη και δημιουργεί
    BalanceRequest που στέλνει στον Master
    */
    private void addBalance(Scanner scanner) {
        try {
            System.out.println("Enter amount to add: ");
            double amount = Double.parseDouble(scanner.nextLine().trim());

            if (amount <= 0) {
                System.out.println("Amount must be higher than 0.");
                return;
            }

            // request object για τον Master
            BalanceRequest request = new BalanceRequest(playerId, amount);
            // αποστολή στον Master
            Object response = sendCommand("addBalance", request);

            // αν η απάντηση είναι double -> νέο balance
            // αν είναι string -> μάλλον error
            if (response instanceof Double) {
                balance = (Double) response;
                System.out.println("Sum Added: " + amount);
                System.out.println("Current Balance: " + balance);
            } else if (response instanceof String) {
                System.out.println("Server Response: " + response);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid Amount.");
        } catch (Exception e) {
            System.out.println("Error adding balance: " + e.getMessage());
        }
    }

    /*
    Games menu
    */
    private void gamesMenu(Scanner scanner) {
        try {
            while (true) {
                System.out.println("Games Menu: ");
                System.out.println("1. Show Available Games");
                System.out.println("2. Search Games");
                System.out.println("3. Rate Game");
                System.out.println("4. Back");
                System.out.println("Enter Action: ");

                String action = scanner.nextLine().trim();

                switch (action) {
                    case "1":
                        showAvailableGames(scanner);
                        break;
                    case "2":
                        search(scanner);
                        break;
                    case "3":
                        rate(scanner);
                        break;
                    case "4":
                        return;
                    default:
                        System.out.println("Unknown Action. Please try again.");
                }
            }
        } catch (Exception e) {
            System.out.println("Error in games menu: " + e.getMessage());
        }
    }

    /*
    Show Available Games
    Εμφανιση όλων των διαθέσιμων (active) παιχνιδιών
    */
    private void showAvailableGames(Scanner scanner) {
        try {
            Object response = sendCommand("getAvailableGames", null);
            displayGames(response, "Available Games:", scanner);
        } catch (Exception e) {
            System.out.println("Error in showing available games: " + e.getMessage());
        }
    }

	/*
	Search
	Ο χρήστης μπορεί να επιλέξει φίλτρα stars, bet type, risk level
	*/
	private void search(Scanner scanner) {
		try {
			Integer stars = null;
			String betType = null;
			String riskLevel = null;

			System.out.println("Search Games:");
      
			// filter by stars
			System.out.println("Enter Stars (1-5). Press enter to skip.");
			String instars = scanner.nextLine().trim();

			if (!instars.isEmpty()) {
				int parsedStars = Integer.parseInt(instars);
				if (parsedStars < 1 || parsedStars > 5) {
					System.out.println("Stars must be from 1 to 5.");
					return;
				}
				stars = parsedStars;
			}

			// filter by bet type
			System.out.println("Enter Bet Type ( $ / $$ / $$$ ). Press enter to skip.");
			String inbetType = scanner.nextLine().trim();

			if (!inbetType.isEmpty()) {
				if (!inbetType.equals("$") && !inbetType.equals("$$") && !inbetType.equals("$$$")) {
					System.out.println("Invalid Bet Type.");
					return;
				}
				betType = inbetType;
			}

			// filter by risk level
			System.out.println("Enter Risk Level (low / medium / high). Press enter to skip.");
			String inriskLevel = scanner.nextLine().trim().toLowerCase();

			if (!inriskLevel.isEmpty()) {
				if (!inriskLevel.equals("low") && !inriskLevel.equals("medium") && !inriskLevel.equals("high")) {
					System.out.println("Invalid Risk Level.");
					return;
				}
				riskLevel = inriskLevel;
			}

			// αν ο χρήστης δεν έβαλε φίλτρα, δείχνουμε όλα τα διαθέσιμα παιχνίδια
			if (stars == null && betType == null && riskLevel == null) {
				System.out.println("No filters applied. Showing all available games.");
				showAvailableGames(scanner);
				return;
			}

			// μοναδικό searchId ανά SearchRequest.
			String searchId = playerId + "_" + System.currentTimeMillis();

			SearchRequest request = new SearchRequest(playerId, searchId, stars, betType, riskLevel);
			Object response = sendCommand("search", request);

			if (response instanceof String) {
				System.out.println("Server Response: " + response);
				return;
			}

			displayGames(response, "Search Results:", scanner);
		} catch (NumberFormatException e) {
			System.out.println("Invalid number entered.");
		} catch (Exception e) {
			System.out.println("Error in filter menu: " + e.getMessage());
		}
	}
  
    /*
    Display Games
    */
    private void displayGames(Object response, String title, Scanner scanner) {
        try {
            if (!(response instanceof List<?>)) {
                System.out.println("No games found.");
                return;
            }

            List<?> games = (List<?>) response;

            if (games.isEmpty()) {
                System.out.println("No games found.");
                return;
            }

            List<Game> availableGames = new ArrayList<>();

            System.out.println("\n" + title);
            int index = 1;
            for (Object obj : games) {
                if (obj instanceof Game) {
                    Game game = (Game) obj;
                    availableGames.add(game);
                    printGame(game, index++);
                }
            }

            if (availableGames.isEmpty()) {
                System.out.println("No games found.");
                return;
            }

            // μετά την εμφάνιση έχουμε επιλογή για play
            System.out.println("Enter the number of the game you would like to play. 0 to go back.");
            int choice = Integer.parseInt(scanner.nextLine().trim());

            if (choice == 0) {
                return;
            }

            choice = choice - 1;
            if (choice < 0 || choice >= availableGames.size()) {
                System.out.println("Invalid number.");
                return;
            }

            Game selectedGame = availableGames.get(choice);
            play(scanner, selectedGame);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }catch (Exception e) {
            System.out.println("Error in displaying games: " + e.getMessage());
        }
    }

    /*
    Print Game
    Τυπώνει αναλυτικά ένα Game.
    provider, stars, number of votes,
    logo, min bet, max bet, risk level,
    bet type, jackpot
    */
    private void printGame(Game game, int index) {
        System.out.println(index + "." + game.getGameName());
        System.out.println("Provider Name: " + game.getProviderName());
        System.out.println("Stars: " + game.getStars());
        System.out.println("Number of Votes: " + game.getNoOfVotes());
        System.out.println("Logo: " + game.getGameLogo());
        System.out.println("Min Bet: " + game.getMinBet());
        System.out.println("Max Bet: " + game.getMaxBet());
        System.out.println("Risk Level: " + game.getRiskLevel());
        System.out.println("Bet Type: " + game.getBetType());
        System.out.println("Jackpot: " + game.getJackpot());
        System.out.println();
    }

    /*
    Play Game
    Βασική ροή πονταρίσματος:
    refresh balance -> εμφάνιση βασικών πληροφοριών παιχνιδιού ->
    εισαγωγή bet -> έλεγχοι -> αποστολή PlayRequest στον Master
    */
    private void play(Scanner scanner, Game selectedGame) {
        try {
            while (true) {
                // ζητάμε τρέχον balance
                refreshBalance();

                System.out.println("\nSelected Game: " + selectedGame.getGameName());
                System.out.println("Current Balance: " + balance);
                System.out.println("Min Bet: " + selectedGame.getMinBet());
                System.out.println("Max Bet: " + selectedGame.getMaxBet());
                System.out.println("Enter bet amount: ");

                double bet = Double.parseDouble(scanner.nextLine().trim());

                // έλεγχοι πριν κάνουμε request.
                if (bet <= 0) {
                    System.out.println("Bet amount must be higher than 0.");
                    return;
                }

                if (bet < selectedGame.getMinBet() || bet > selectedGame.getMaxBet()){
                    System.out.println("Bet amount must be between Min Bet and Max Bet.");
                    return;
                }

                if (bet > balance) {
                    System.out.println("Bet higher than balance.");
                    return;
                }

                PlayRequest request = new PlayRequest(playerId, selectedGame.getGameName(), bet);
                Object response = sendCommand("play", request);

                if (response instanceof String) {
                    System.out.println("Server response: " + response);
                } else {
                    System.out.println("Unexpected response from server.");
                }

                // ξαναπαίρνουμε το balance
                refreshBalance();
                System.out.println("Updated Balance: " + balance);

                // play again
                System.out.println("\nWant to play again? yes/no: ");
                String again = scanner.nextLine().trim().toLowerCase();

                if (!again.equals("yes")){
                    return;
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid Number");
        } catch (Exception e) {
            System.out.println("Error in play game: " + e.getMessage());
        }
    }

    /*
    Rate
    Εμφανίζει όλα τα διαθέσιμα παιχνίδια
    Ο χρήστης επιλέγει ένα παιχνίδι και rating
    Στέλνουμε RateRequest
    */
    private void rate(Scanner scanner) {
        try {
            Object response = sendCommand("getAvailableGames", null);

            if (!(response instanceof List<?>)) {
                System.out.println("No games available for rating");
                return;
            }

            List<?> games = (List<?>) response;

            if (games.isEmpty()) {
                System.out.println("No games available for rating.");
                return;
            }

            System.out.println("Available Games for Rating:");
            int index = 1;
            List<Game> availableGames = new ArrayList<>();

            for (Object obj : games) {
                if (obj instanceof Game) {
                    Game game = (Game) obj;
                    availableGames.add(game);
                    System.out.println(index + ":" + game.getGameName() +
                                        ", Current Stars: " + game.getStars() + 
                                        ", Votes: " + game.getNoOfVotes());
                    index++;
                }
            }

            if (availableGames.isEmpty()) {
                System.out.println("No games found.");
                return;
            }

            System.out.println("Select Game Number: ");
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

            if (choice < 0 || choice >= availableGames.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            Game selectedGame = availableGames.get(choice);

            System.out.println("Enter Rating (1-5): ");
            int rating = Integer.parseInt(scanner.nextLine().trim());

            if (rating < 1 || rating > 5) {
                System.out.println("Rating must be from 1 to 5");
                return;
            }

            RateRequest request = new RateRequest(playerId, selectedGame.getGameName(), rating);
            Object rateResponse = sendCommand("rate", request);

            if (rateResponse != null) {
                System.out.println("Server Response: " + rateResponse);
            } else {
                System.out.println("Rating submitted successfully.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        } catch (Exception e) {
            System.out.println("Error in rating game: " + e.getMessage());
        }
    }

    /*
    Refresh Balance
    Ζητάει από τον Master το current balance του playerId
    και ενημερώνει το balance
    */
    private void refreshBalance() {
        Object response = sendCommand("getBalance", playerId);
        if (response instanceof Double) {
            balance = (Double) response;
        }
    }

    /*
    Send Command
    Στέλνει εντολή και δεδομένα στον Master 
    Επιστρέφει απάντηση του Master
    */
    private synchronized Object sendCommand(String command, Object data) {
        try {
            out.writeObject(command);
            out.writeObject(data);
            out.flush();

            return in.readObject();
        } catch (Exception e) {
            System.out.println("Error in sending command " + command + ": " + e.getMessage());
            return null;
        }
    }
}