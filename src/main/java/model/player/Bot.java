package model.player;

import model.card.Hand;
import model.state.GameState;
import model.state.Move;

import java.util.HashSet;
import java.util.Set;

public class Bot extends Player {

    private final Set<Information> knowledge = new HashSet<>();


    public Bot(String name, Hand startingHand) {
        super(name, startingHand);
    }

    @Override
    public Move turn(GameState state) {
        return null;
    }

    @Override
    public void giveInformation(Information inf) {

        //add this information to our knowledge
        this.knowledge.add(inf);
    }
}
