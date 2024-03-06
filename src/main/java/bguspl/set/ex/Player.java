package bguspl.set.ex;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;
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

    //flags indicating:
    // Point/Penalty
    public boolean point;
    public boolean penalty;
    // If the board is being reorgenized 
    public boolean removeAllCardsFromTable;

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
        this.incomingActions = new ArrayBlockingQueue<Integer>(env.config.featureSize);
        this.point = false;
        this.penalty = false;
        removeAllCardsFromTable = true;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence(); // Generate presskeys for ai
        // Run loop

        while (!terminate) {
            // Press for ai
            if(!human){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                if(!incomingActions.isEmpty()) keyPressed(incomingActions.remove());      
            }
            // For tests
            /*if(id==0){
                try {
                    Thread.sleep((long)(2000));
                    automatePresses();
                } catch (Exception e) {}
            } */  

            // Wait for dealer when (tokens.size == featureSize)
            synchronized(table.tokens.get(id)) {
                while (table.tokens.get(id).size() == env.config.featureSize || removeAllCardsFromTable) { 
                    try {
                        table.tokens.get(id).wait();
                    } catch (InterruptedException ignored) {}
                }
            }

            // Award/penalize the player
            if(point) point();
            if(penalty) penalty();

        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     * @PRE: (!human)
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rand = new Random();
            Integer nextPress;
            
            // Run loop
            while (!terminate) {
                //if the computer agent has genereated 'FeatureSize' key-presses we tell the thread to wait
                synchronized(aiThread){
                    if(incomingActions.size() < env.config.featureSize ){
                        //generating random keypress for the computeragenet
                        nextPress = rand.nextInt(env.config.rows * env.config.columns);
                        incomingActions.add(nextPress);  
                    } else {
                        try{ 
                            aiThread.wait();
                        }
                        catch (InterruptedException ignored) {}
                    } 
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
        removeAllCardsFromTable = false;
        terminate = true;
       
        // Stop and wait for ai
        if(aiThread != null){
            aiThread.interrupt();
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {}
        }
        
        // Same for player
        if(playerThread != null){
             playerThread.interrupt();
             try {
                playerThread.join();

             } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
             }
    
        }
    }

    /**
     * This method is called when a key is pressed.
     * 
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot)  {
        // Don't allow more then feature size tokens &&  don't allow the input manager to access tokenPress
        if(table.tokens.get(id).size() != env.config.featureSize && !point && !penalty && !removeAllCardsFromTable){
            if(table.isPlaced(id, slot)) {
                table.removeToken(id, slot);
            }
            else {
                table.placeToken(id, slot);
            }
            // Notify the ai to continue generate keypresses 
            if(!human) synchronized(aiThread) { aiThread.notify();}
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        setClockFreeze(true);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        point = false;
        //dealer.resetTime();
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
            setClockFreeze(false);
            penalty = false;
    }

    public int score() {
        return score;
    }

    public void setCheckSet(boolean result) {
        if(result) point = true;
        else penalty = true;
    }
    
    // Incharge of freezing the player for penalty/score
    public void setClockFreeze (boolean success) {
        long counter = success ? env.config.pointFreezeMillis : env.config.penaltyFreezeMillis;
        while(counter > 0) {
            env.ui.setFreeze(id,counter);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            counter -= 1000;
        }
        env.ui.setFreeze(id,counter);
    }

    public void setRemoveAllCardFromTable(boolean set) {
        removeAllCardsFromTable = set;
    }
   
    public void automatePresses() {
        List<Integer> deck = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, 1).forEach(set -> {
            keyPressed(table.cardToSlot[set[0]]);
            keyPressed(table.cardToSlot[set[1]]);
            keyPressed(table.cardToSlot[set[2]]);
        });
    }
}
