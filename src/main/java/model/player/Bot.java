package model.player;

import model.card.Hand;
import model.state.GameState;
import model.state.Move;

public class Bot extends Player {


    public Bot(String name, Hand startingHand) {
        super(name, startingHand);
    }

    @Override
    public Move turn(GameState state) {
        return null;
    }
}
