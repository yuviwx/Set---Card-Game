package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Vector;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /*
     * Random field to determine the place order of cards
    */
    private final List<Integer> cardsOrder;
    
    /*
     * flag to notifiay the playrs in addcardtotable after removingallcards 
     */
    boolean removeAllCardsFromTable;

    /*
     * An array of the player threads
     */
    Thread[] playersThreads;

    /*
     * The start time for turnTimeoutMillis = 0
    */
    private long startTimer;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        Collections.shuffle(deck);
        cardsOrder = IntStream.range(0, env.config.rows * env.config.columns).boxed().collect(Collectors.toList());
        Collections.shuffle(cardsOrder);
        removeAllCardsFromTable = true;
        playersThreads = new Thread[env.config.players];
        startTimer = 0;       
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     * after finishing to check the waiting players add new cards
     * after removing cards from table - 
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        // Creates Thread for each player and call start()
        for(int i = 0; i < playersThreads.length; i++) {
            playersThreads[i] = new Thread(players[i]);            
            playersThreads[i].start();
            // Make the ai thread start after his player's thread
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }
        // Game versions
            // No timer Game
        if(env.config.turnTimeoutMillis < 0) { 
            while(!shouldFinish()) {
                beforePlaceOnTable();
                placeCardsOnTable();
                timerLoopForNoTime();
                removeAllCardsFromTable();
                }
            }
            // No limited time
        else if (env.config.turnTimeoutMillis == 0) {
             while(!shouldFinish()) {
                beforePlaceOnTable();
                placeCardsOnTable();
                timerLoopForZero();
                removeAllCardsFromTable();
             }
        }
            // Regular Game
        else {
            while (!shouldFinish()) {
                placeCardsOnTable();
                timerLoop();
                updateTimerDisplay(false);
                removeAllCardsFromTable();
            }
        }
        // Same for all versions
        announceWinners();
        terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");   
    }
    // Regular Game:
    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        // Update the timer before sleep
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        updateTimerDisplay(false);
        while (!terminate && System.currentTimeMillis() < reshuffleTime ) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public synchronized void terminate() {
        terminate = true;
        removeAllCardsFromTable();
        for(int i=env.config.players - 1; i>=0; i--){
            players[i].terminate();
        } 
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     * @PRE: none
     * @POST: Update the tokens & notify the players respectively 
     */
    private synchronized void removeCardsFromTable() {
        while(!table.waitingForDealer.isEmpty()){
            int playerId = table.waitingForDealer.remove();
            // Saves the slots on which the player put token
            int[] playerTokens = vectorToArray(table.tokens.get(playerId)); 
            // Saves the cards on which the player put token on
            int[] playerCards = new int[env.config.featureSize];
            // Inserting Cards to cards array 
            for(int i=0; i<playerCards.length; i++) playerCards[i] = table.slotToCard[playerTokens[i]]; 

                synchronized(table.tokens.get(playerId)){
                    // Check if set and update player           
                    boolean result = (env.util.testSet(playerCards)) ? true : false;
                    players[playerId].setCheckSet(result);

                    // Update board - true => remove card : false => remove tokens;            
                    for(int token : playerTokens){
                        if(result) table.removeCard(token);
                        else table.removeToken(playerId, token);
                    }                                 

                    // Reset timer
                    if(result) resetTime();
                    
                    // Notify the player
                    table.tokens.get(playerId).notify();
                }

                    // Update waitingForDealer - if a card with your token was removed, you're no longer waiting for response
                    for(int id : table.waitingForDealer) {
                        if(table.tokens.get(id).size() < env.config.featureSize) {
                            table.waitingForDealer.remove(id);
                            synchronized(table.tokens.get(id)) {table.tokens.get(id).notify();}                        
                        }
                    }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * 
     */
    private synchronized void placeCardsOnTable() {
        //for each slot checks if its null and if it is, adds a new card from the deck
        for(int i =0; i<cardsOrder.size(); i++) {
        if(table.slotToCard[cardsOrder.get(i)]==null && !deck.isEmpty()) table.placeCard(deck.remove(0),cardsOrder.get(i));
        }
        if(removeAllCardsFromTable){
            // Notify the players
            for(Player p : players) {
                synchronized(table.tokens.get(p.id)) {
                    table.tokens.get(p.id).notify();
                    p.setRemoveAllCardFromTable(false);
                }      
            }   
            removeAllCardsFromTable = false;
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     * wake up for: 
     * to update the timer: every second, when the timer runsout
     * if one plyaer has 3 tokens - listen to table waiting for dealer list - point/penalty, replace cards, remove tokens, update lists...
     * to end the game: 
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(table.waitingForDealer){
            if((reshuffleTime - System.currentTimeMillis()) > env.config.turnTimeoutWarningMillis){
                try {
                    table.waitingForDealer.wait(100);
                }catch(InterruptedException ignored){} 
            } 
        } 
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (!reset) {
            // Recalculating the remaining time
            long remainingTime = reshuffleTime - System.currentTimeMillis();
            // Making sure the display isn't negetive - updatetimer is after of the timerloop(in the run) can cost miliseconds
            if(remainingTime < 0) remainingTime = 0;
            // If warning -> timer red
            if(remainingTime < env.config.turnTimeoutWarningMillis){
                env.ui.setCountdown(remainingTime,true);
            }
            else env.ui.setCountdown(remainingTime,false);
        }
        // Reset
        else {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis,false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     * @PRE: none
     * @POST: for 0 < i < 11, SlotToCard[i] == null;
     */
    private synchronized void removeAllCardsFromTable() {
        // Put the player thread's on wait
        removeAllCardsFromTable = true;
        for(Player p : players) p.setRemoveAllCardFromTable(true);
        // return the cards to the deck and shuffle
        for(int i=0; i <cardsOrder.size(); i++)  {
            Integer card = table.slotToCard[cardsOrder.get(i)];
            if(card != null)deck.add(card);
            table.removeCard(cardsOrder.get(i));
        }
        Collections.shuffle(deck); // Shuffle the returned cards
        table.clearWaitingQueue(); // Clear waiting list
    }
    
    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        String winner = "" + players[0].id;
        int maxScore = players[0].score();
        
        for(int i = 1; i < env.config.players; i++) {
            if(players[i].score() == maxScore) winner += " " + players[i].id;
            else if (players[i].score() > maxScore) {
                winner = "" + (players[i].id); 
                maxScore = players[i].score();
                }
        }
        int[] winners = Arrays.stream(winner.split(" ")).mapToInt(Integer::parseInt).toArray();
        env.ui.announceWinner(winners);
    }

    // Help methods:
    private int[] vectorToArray(Vector <Integer> vec) {
        int[] output = new int[vec.size()];
        int i = 0;
        for(Integer num : vec) {
            output[i] = num;
            i++;
        }
        return output;
    }

    public void resetTime() {
        if(env.config.turnTimeoutMillis > 0)
            updateTimerDisplay(true);
        else if(env.config.turnTimeoutMillis == 0)
            updateTimerDisplayForZero(true);
    }

// No time game version:
    
    // Timer loop for turnTimeoutMillis < 0
    private void timerLoopForNoTime() {
        while (!terminate && isThereASet()) {
            sleepUntilWokenOrTimeout();
            removeCardsFromTable();
            placeCardsOnTable();
        }
    } 

    private boolean isThereASet () {
        List<Integer> deck = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        return !env.util.findSets(deck, 1).isEmpty();
    }

    //NO LIMITED TIME VERSION
    private void timerLoopForZero() {
        // Reset timer
        startTimer = System.currentTimeMillis();
        env.ui.setElapsed(0);
        // Timer loop
        while (!terminate && isThereASet()) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplayForZero(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }


    private void updateTimerDisplayForZero(boolean reset) {
        long currentTime = System.currentTimeMillis() - startTimer;
        if (!reset) {
            env.ui.setElapsed(currentTime);
        }
        else {
            startTimer = System.currentTimeMillis();
            env.ui.setElapsed(0);
        }
    }

    private void beforePlaceOnTable(){
        if(deck.size() >= 12){
            List<Integer> toPlace = new ArrayList<Integer>();
            do{ // Do until there is a set in the first 12 cards
                Collections.shuffle(deck);
                // Put the first 12 cards from the deck
                for(int i = 0; i<12; i++){
                    toPlace.add(deck.get(i));
                }  
                // Check if there is a set in them
            }while(env.util.findSets(toPlace, 1).size() == 0); 
        }
    }
}
