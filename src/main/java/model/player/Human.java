package model.player;

import engine.GameEngine;
import model.card.Hand;
import model.state.GameState;
import model.state.Move;
import ui.UI;

import java.util.Optional;

public class Human extends Player {

    public Human(String name, Hand startingHand) {
        super(name, startingHand);
    }

    @Override
    public Move turn(GameState state) {

        UI ui = UI.getInstance();
        Optional<Move> mv = ui.promptMove(state);

        //if we didn't get a move then return a move which does nothing
        return mv.orElseGet(() -> new Move(this, this.getHand(), false, false, -1));
    }

    @Override
    public void giveInformation(Information inf) {

        //show the user this card
        UI.getInstance().peekAtCard(inf.owner(), inf.index());
    }
}
