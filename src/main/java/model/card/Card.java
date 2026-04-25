package model.card;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {

    public int getValue() {
        return this.rank.getValue();
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Card c)) {
            return false;
        }
        return (
                (c.rank().equals(this.rank))
                && (c.suit().equals(this.suit))
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.rank, this.suit);
    }
}
