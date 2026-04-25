package model.card;

import java.util.List;
import java.util.Optional;
import java.util.Stack;

//SINGLETON
public class Discard implements Pile {

    private final Stack<Card> cards = new Stack<>();
    private static Discard instance = null;

    public static Discard getInstance() {

        if (instance == null) {
            instance = new Discard();
        }
        return instance;
    }

    @Override
    public Optional<Card> peek() {

        if (this.cards.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(this.cards.peek());
        }
    }

    @Override
    public Optional<Card> poll() {

        if (this.cards.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(this.cards.pop());
        }
    }

    @Override
    public void add(Card c) {
        this.cards.add(c);
    }

    public List<Card> getHistory() {
        return this.cards;
    }
}
