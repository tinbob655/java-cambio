package model.player;

import model.card.Hand;
import model.state.GameState;
import model.state.Move;

public class Human extends Player {

    public Human(String name, Hand startingHand) {
        super(name, startingHand);
    }

    @Override
    public Move turn(GameState state) {
        return null;
    }
}
