package model.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class Hand {

    private final List<Optional<Card>> cards;

    public Hand(List<Optional<Card>> startingCards) {
        this.cards = startingCards;
    }

    public List<Optional<Card>> getCards() {
        return this.cards;
    }
    public Optional<Card> getCardAt(int index) {

        if (index < 0 || index > 3) {
            throw new ArrayIndexOutOfBoundsException("Cannot access a card outside index range 0-3");
        }
        return this.cards.get(index);
    }

    public void setCardAt(int index, Card newCard) {

        if (index < 0 || index > 3) {
            throw new ArrayIndexOutOfBoundsException("Cannot set a card outside index range 0-3");
        }
        this.cards.set(index, Optional.of(newCard));
    }

    public Hand newHandWithSwapAt(int index, Card newCard) {
        Hand newHand = new Hand(new ArrayList<>(this.cards));
        newHand.setCardAt(index, newCard);
        return newHand;
    }

    public int value() {
        return this.cards.stream()
                .filter(Optional::isPresent)
                .mapToInt(c -> c.get().getValue())
                .sum();
    }

    public int size() {
        long size = this.cards.stream()
                .filter(Optional::isPresent)
                .count();
        if (size > 4) {

            //should never have a hand greater than 4 cards large
            throw new IllegalStateException("Hand larger than 4 detected");
        }
        else return (int) size;
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Hand h)) {
            return false;
        }
        return this.cards.equals(h.getCards());
    }

    @Override
    public int hashCode() {
        return this.getCards().hashCode();
    }
}
