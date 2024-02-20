package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;  
import java.util.Scanner;
import bguspl.set.Env;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /*
     * The dealer
     */
    private final Dealer dealer;
    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /*
     * This queue contains the keys to be pressed
     */
    private ArrayBlockingQueue<Integer> incomingActions;

    /*
     * Contains tokens
     */
    private int tokenCount;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.incomingActions = new ArrayBlockingQueue<Integer>(3);
        this.tokenCount = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence(); // Generate presskeys for ai
        Scanner scan = new Scanner(System.in); //For human player
        char key;
        // Run loop
        while (!terminate) {
            if(human) incomingActions.add((int)scan.nextLine().charAt(0)); // Get action from the user
            keyPressed(keyToSlot(incomingActions.peek()));
            // wake dealer up                                        

        }    
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private synchronized void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rand = new Random();
            Integer nextPress;
            int[] playerKeys = env.config.playerKeys(id);
            while (!terminate) {
                //if the computer agent has genereated 3 key-presses we tell the thread to wait
                if(incomingActions.size() < env.config.featureCount){
                    //generating random keypress for the computeragenet
                    nextPress = rand.nextInt(12);
                    incomingActions.add(playerKeys[nextPress]);
                } else {
                    try{ aiThread.wait();}
                    catch (InterruptedException ignored) {}
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     * 
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot)  {
           if(table.isPlaced(id, slot)) {
                table.removeToken(id, slot);
                tokenCount--;
           }
           else {
                table.placeToken(id, slot);
                tokenCount++;
            }
           incomingActions.remove();
           if(tokenCount < env.config.featureCount && !human)  aiThread.notify(); // Notify the ai to continue generate keypresses   
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        try {
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException ignored) {} 
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
         try {
            Thread.sleep(env.config.penaltyFreezeMillis);
        } catch (InterruptedException ignored) {} 
    }

    public int score() {
        return score;
    }

    private int keyToSlot(int key) {
    int[] playerKeys = env.config.playerKeys(id);    
    for(int i=0; i<playerKeys.length; i++)
        if(playerKeys[i] == key) return i;
    return -1;
    } 
}
