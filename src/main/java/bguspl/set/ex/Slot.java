package bguspl.set.ex;
import java.util.Vector;


public class Slot {
    private Vector<Integer> tokens;
    private Integer card;

    public Slot(Integer card) {
        this.tokens = new Vector<Integer>();
        this.card = card;
    }

    public Vector<Integer> getTokens() {return this.tokens;}

    public Integer getCard() {return this.card;}

    public void addToken(Integer player){
        this.tokens.add(player);
    }

    public void removeToken(Integer player){
        this.tokens.remove(player);
    }

    public boolean isPlaced(int player){
        return tokens.contains(player);
    }
}