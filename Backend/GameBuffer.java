import java.util.LinkedList;
import java.util.Random;

/*
Producer - Consumer
Producer: o SRG γεμίζει τον buffer
Consumer: o worker ζητάει αριθμό όταν γίνεται play
*/

public class GameBuffer {
	// λίστα με τους διαθέσιμους τυχαίους αριθμούς
	private LinkedList<Integer> buffer = new LinkedList<>();
  
	// μέγεθος buffer
	private final int maxSize = 50;
	private String gameName;

	public GameBuffer(String gameName) {
		this.gameName = gameName;

		// Producer thread για το game
		// Ένα producer thread ανα game
		Thread producerThread = new Thread(new Producer(), "SRG-Producer-" + gameName);
		// αυτό σημαίνει πως κλείνει μαζί με το πρόγραμμα
		producerThread.setDaemon(true);
		producerThread.start();
	}


	/*
	Producer.
	Παράγει τυχαίους αριθμούς και τους βάζει στον buffer
	*/
	private class Producer implements Runnable {
		private Random rand = new Random();

		@Override
		public void run() {
			while (true) {
				try {
					// παράγουμε random αριθμό από 1-9999
					produce(rand.nextInt(10000));
				} catch (InterruptedException e) {
					// αν διακοπεί το thread, στααματάμε
					Thread.currentThread().interrupt();
					break;
				} catch (Exception e) {
					break;
				}
			}
		}
	}

	/*
	Produce
	synchronized γιατί producer και consumer μοιράζονται τον ίδιο buffer.
	*/
	public synchronized void produce(int number) throws InterruptedException {
		// αν o buffer είναι γεμάτος o producer περιμένει
		while (buffer.size() == maxSize) {
			wait();
		}
  
		// βάζουμε τον αριθμό στο τέλος της ουράς
		buffer.addLast(number);

		//notify τους consumers
		notifyAll();
	}

	/*
	Consume
	επιστρέφει και διαγράφει τον πρώτο διαθέσιμο αριθμό από τον buffer.
	*/

	public synchronized int consume() throws InterruptedException {
		// αν o buffer είναι άδειος o consumer περιμένει
		while (buffer.isEmpty()) {
			wait();
		}

		int number = buffer.removeFirst();

		// notify τους producers
		notifyAll();

		return number;
	}

	public String getGameName() {
		return gameName;
	}
}

