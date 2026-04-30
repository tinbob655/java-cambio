package model.player;

import javafx.util.Pair;
import model.card.Hand;
import model.state.GameState;
import model.state.Move;

import java.util.Objects;

public abstract class Player {

    private final String name;
    private final Hand hand;

    public Player(String name, Hand startingHand) {
        this.name = name;
        this.hand = startingHand;
    }

    public String getName() {
        return this.name;
    }
    public Hand getHand() {
        return this.hand;
    }

    public abstract Move turn(GameState state);
    public abstract void giveInformation(Information inf);
    public abstract Pair<Player, Integer> otherPlayerCardTarget();
    public abstract Pair<Player, Integer> selfCardTarget();
    public abstract Pair<Player, Integer> anyPlayerCardTarget();

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Player p)) {
            return false;
        }
        return (
                (p.getName().equals(this.name))
                && (p.getHand().equals(this.hand))
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.hand);
    }
}
