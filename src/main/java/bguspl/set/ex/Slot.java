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

    public void addToken(int id){
        this.tokens.add(id);
    }

    public void removeToken(Integer id) {
        this.tokens.remove(id);
    }
}