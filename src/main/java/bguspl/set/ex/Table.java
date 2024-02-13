package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Vector;

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
    final Vector<Slot> board;
    
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
        this.board = new Vector<Slot>(12);
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
     * @PRE: board.get(slot) == null;
     * @POST: board.get(slot).getCard() == card;
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        if(board.get(slot) == null){
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            board.set(slot,new Slot(card)); //adding a card to its slot
        }
        else {
            System.out.println("This slot is already occupied");
        }
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     * @PRE: board.get(slot) != null;
     * @POST: board.get(slot) == null;
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        if(board.get(slot) != null){
            int card = slotToCard[slot];
            slotToCard[slot] = null;
            cardToSlot[card] = null;
            board.set(slot, null);
        }
        else {
            System.out.println("the slot is already empty you idiot");
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @PRE: !board.get(slot).getTokens().contains(player)
     * @POST: board.get(slot).getTokens().contains(player)
     */
    public void placeToken(int player, int slot) {
        if(!board.get(slot).getTokens().contains(player)) {
            board.get(slot).addToken(player);
        }
        else {
            System.out.println("the token aleady exist");
        }                
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @PRE board.get(slot).getTokens().contains(player)
     * @POST !board.get(slot).getTokens().contains(player)
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if(board.get(slot).getTokens().contains(player)) {
            board.get(slot).removeToken(player);
            return true;
        }
        else {
            System.out.println("there isnt a token on the slot");
        }
        return false;
    }
}
