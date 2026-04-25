package model.card;

import java.util.Collections;
import java.util.Optional;
import java.util.Stack;

//SINGLETON
public class Deck implements Pile {

    private final Stack<Card> cards = new Stack<>();
    private static Deck instance = null;

    private Deck() {

        //create a deck of all cards
        for (Rank r : Rank.values()) {
            for (Suit s : Suit.values()) {
                if (r != Rank.JOKER) {
                    this.cards.add(new Card(r, s));
                }
            }
        }

        //add jokers
        this.cards.add(new Card(Rank.JOKER, Suit.SPADES));
        this.cards.add(new Card(Rank.JOKER, Suit.HEARTS));

        Collections.shuffle(this.cards);
    }

    public static Deck getInstance() {

        if (instance == null) {
            instance = new Deck();
        }
        return instance;
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
    public Optional<Card> peek() {

        if (this.cards.isEmpty()) {
            return Optional.empty();
        }
        else {
            return Optional.of(this.cards.peek());
        }
    }

    @Override
    public void add(Card c) {
        this.cards.add(c);
    }

}
