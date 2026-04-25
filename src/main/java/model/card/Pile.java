package model.card;

import java.util.Optional;

public interface Pile {

    Optional<Card> poll();
    Optional<Card> peek();
    void add(Card c);
}
