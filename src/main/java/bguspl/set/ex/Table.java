package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */

    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */

    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */

    protected final Integer[] cardToSlot; // slot per card (if any)

    /*
     * The data stracture for the game 
     */

    final Vector<Vector<Integer>> tokens;
    final ArrayBlockingQueue<Integer> waitingForDealer;
    
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */

    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokens = new Vector<Vector<Integer>>(env.config.players);
        initializeTokens();
        this.waitingForDealer = new ArrayBlockingQueue<Integer>(env.config.players);
    }

    private void initializeTokens (){
        for (int i =0; i< env.config.players; i++)tokens.add(new Vector<Integer>(env.config.featureSize));  
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @PRE: cardToSlot[card] == slotToCard[slot] == null;
     * @POST: cardToSlot[card] == slot; slotToCard[slot] == card;
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            if(slotToCard[slot] == null){
                cardToSlot[card] = slot;
                slotToCard[slot] = card;
                env.ui.placeCard(card, slot);
            }
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     * @PRE: none
     * @POST: cardToSlot[card] == slotToCard[slot] == null;
     */
    public synchronized void removeCard(int slot) {
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            if(slotToCard[slot] != null){
                for(int i =0; i<env.config.players; i++) removeToken(i, slot);
                int card = slotToCard[slot];
                cardToSlot[card] = null;
                slotToCard[slot] = null;
                env.ui.removeTokens(slot);
                env.ui.removeCard(slot);
            }
        
    }
    

    /**
     * Places a player's token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @PRE: !tokens.get(player).contains(slotToCard[slot]);
     * @POST: tokens.get(player).contains(slotToCard[slot]);
     */
    public synchronized void placeToken(int player, int slot) {
        if(slotToCard[slot] != null) {
            tokens.get(player).add(slot);
            env.ui.placeToken(player, slot);
            if(tokens.get(player).size() == env.config.featureSize) {
                waitingForDealer.add(player);
                synchronized(waitingForDealer){waitingForDealer.notify();}
            }
            //System.out.println(tokens.get(player).toString());
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @PRE tokens.get(player).contains(slotToCard[slot])
     * @POST !tokens.get(player).contains(slotToCard[slot])
     * @return - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        if(tokens.get(player).contains(slot)) {
            tokens.get(player).remove((Integer)slot);
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
        
    }

    public boolean isPlaced(int player, int slot){
        return tokens.get(player).contains(slot);
    }

    public void clearWaitingQueue() {
        waitingForDealer.clear();
    }
}
