package model.card;

import java.util.Arrays;
import java.util.Optional;

public final class Hand {

    private final Optional<Card>[] cards;

    public Hand(Optional<Card>[] startingCards) {
        this.cards = startingCards;
    }

    public Optional<Card>[] getCards() {
        return this.cards;
    }
    public Optional<Card> getCardAt(int index) {

        if (index < 0 || index > 3) {
            throw new ArrayIndexOutOfBoundsException("Cannot access a card outside index range 0-3");
        }
        return this.cards[index];
    }

    public void setCardAt(Card newCard, int index) {

        if (index < 0 || index > 3) {
            throw new ArrayIndexOutOfBoundsException("Cannot set a card outside index range 0-3");
        }
        this.cards[index] = Optional.of(newCard);
    }

    public void removeCard(int index) {

        if (index < 0 || index > 3) {
            throw new ArrayIndexOutOfBoundsException("Cannot remove a card outside index range 0-3");
        }
        this.cards[index] = Optional.empty();
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Hand h)) {
            return false;
        }
        return Arrays.equals(this.cards, h.getCards());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.cards);
    }
}
