package bguspl.set.ex;

import bguspl.set.Env;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        Collections.shuffle(deck);
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
        for(Player player : players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        // Main loop
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
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
    private void removeCardsFromTable() {
        while(!table.waitingForDealer.isEmpty()){
            int playerId = table.waitingForDealer.remove();
            int[] playerTokens = vectorToArray(table.tokens.get(playerId));
            
            // Check if set and update player
            boolean result = (env.util.testSet(playerTokens)) ? true : false;
            players[playerId].setCheckSet(result);
            
            // Update board - true => remove card : false => remove tokens;            
            for(int token : playerTokens){
                if(result) table.removeCard(token);
                else table.removeToken(playerId, token);
            }

            // Notify the player
            table.tokens.get(playerId).notify();

            // Update waitingForDealer - if a card with your token was removed, you're no longer waiting for response
            for(int id : table.waitingForDealer)
                if(table.tokens.get(id).size() < env.config.featureCount) {
                    table.waitingForDealer.remove(id);
                    table.tokens.get(id).notify();
                }                                  
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * 
     */
    private void placeCardsOnTable() {
        //for each slot checks if its null and if it is, adds a new card from the deck
        for(int i =0; i<12; i++) if(table.slotToCard[i]==null) table.placeCard(deck.remove(0),i);     
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     * @PRE: none
     * @POST: for 0 < i < 11, SlotToCard[i] == null;
     */
    private void removeAllCardsFromTable() {
       for(int i=0; i <12; i++)  {
        deck.add(table.slotToCard[i]);
        table.removeCard(i);
        }
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        String winner = "";
        int maxScore = 0;
        for(int i = 0; i < env.config.players; i++) {
            if(players[i].score() == maxScore) winner += " " + players[i].id;
            else if (players[i].score() > maxScore) {winner = "" + (players[i].id); maxScore = players[i].score();}
        }
        env.ui.announceWinner(Arrays.stream(winner.split(" ")).mapToInt(Integer::parseInt).toArray());
    }

    private int[] vectorToArray(Vector <Integer> vec) {
        int[] output = new int[vec.size()];
        int i = 0;
        for(Integer num : vec) {
            output[i] = num;
            i++;
        }
        return output;
    }
}
