
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/*
Πολυνηματικός TCP server

Ένα buffer per game.
o worker ζητάει έναν random αριθμό για το game.
ο srg επιστρέφει τον random αριθμό και το hash sha256.

o worker κάνει και local verification για να βεβαιωθεί
οτι ο αριθμός που πήρε είναι valid.
*/

public class Srg {
    
    private static final int SRG_PORT = 5060;   // srg port
    
    /*
    buffers: maps για την αποθήκευση των δεδομένων των παιχνιδιών
    gameBuffers: key = gameName, value = gameBuffer του παιχνιδιού
    gameSecrets: key = gameName, value = το secret
    */
	private static final Map<String, GameBuffer> gameBuffers = new HashMap<>();
    private static final Map<String, String> gameSecrets = new HashMap<>();
    
    public static void main(String[] args) {
        new Srg().start();   // εκκίνηση του srg server
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(SRG_PORT)) {
            System.out.println("SRG has started on port: " + SRG_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // κάθε σύνδεση σε νέο thread
                new SrgHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("SRG server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
    Srg Handler
    χειρίζεται μία σύνδεση worker -> srg
    περιμένει:
    command = "generate", gameName, secret
    */
    static class SrgHandler extends Thread {
        private Socket socket;

        public SrgHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            ObjectOutputStream out = null;
            ObjectInputStream in = null;

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Object commandObj = in.readObject();

                    if (!(commandObj instanceof String)) {
                        out.writeObject("Invalid command.");
                        out.flush();
                        continue;
                    }

                    String command = (String) commandObj;

					// εγγραφή νέου παιχνιδιού (από Master)
                    if (command.equals("registerGame")) {
                        String gameName = (String) in.readObject();   // όνομα παιχνιδιού
                        String secret = (String) in.readObject();   // hashkey του παιχνιδιού

                        if (gameName == null || gameName.trim().isEmpty()) {
                            out.writeObject("Invalid game name.");
                            out.flush();
                            continue;
                        }

                        if (secret == null || secret.trim().isEmpty()) {
                            out.writeObject("Invalid secret.");
                            out.flush();
                            continue;
                        }

                        synchronized (gameBuffers) {
                            if (!gameBuffers.containsKey(gameName)) {
                                gameBuffers.put(gameName, new GameBuffer(gameName));
                            }
                            gameSecrets.put(gameName, secret);
                        }
                        out.writeObject("registerGame success.");
                        out.flush();
                    }
					// παραγωγή τυχαίου αριθμού (από Worker)
                    else if (command.equals("generate")) {
                        String gameName = (String) in.readObject();

                        if (gameName == null || gameName.trim().isEmpty()) {
                            out.writeObject("Invalid Game Name.");
                            out.flush();
                            continue;
                        }

                        GameBuffer buffer;
                        String secret;

                        // synchronized γιατί πολλά threads μπορεί να ζητήσουν
                        // buffer για το ίδιο ή για διαφορετικά παιχνίδια
                        synchronized (gameBuffers) {
                            buffer = gameBuffers.get(gameName);
                            secret = gameSecrets.get(gameName);
                        }

                        if (buffer == null) {
                            out.writeObject("Game not registered in SRG");
                            out.flush();
                            continue;
                        }

                        if (secret == null) {
                            out.writeObject("Missing secret for game");
                            out.flush();
                            continue;
                        }

                        // παίρνουμε αριθμό από buffer
                        int number = buffer.consume();

                        // hash sha256(number + secret)
                        String hash = sha256(number + secret);

                        out.writeObject(new SrgResponse(number, hash));
                        out.flush();
                    }
                }
            } catch (SocketException e) {
                // κλείσιμο σύνδεσης
            } catch (Exception e) {
                System.err.println("SRG handler error: " + e.getMessage());
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
    Hash
    επιστρέφει το hash σε μορφή hex string
    */

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.getBytes());

            StringBuilder sb = new StringBuilder();

            for (byte b : hashBytes) {
                // 2-digit hex formatting
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception e) {
            return "Exception.";
        }
    }
}