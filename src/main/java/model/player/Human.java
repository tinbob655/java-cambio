package model.player;

import engine.GameEngine;
import javafx.util.Pair;
import model.card.Hand;
import model.card.Rank;
import model.state.GameState;
import model.state.Move;
import ui.UIProxy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Human extends Player {

    public Human(String name, Hand startingHand) {
        super(name, startingHand);
    }
    private final UIProxy UIHandler = new UIProxy();

    @Override
    public Move turn(GameState state) {

        Optional<Move> mv = this.UIHandler.promptMove(state);

        //not getting a move means calling cambio
        return mv.orElseGet(() -> new Move(this, this.getHand(), false, false, -1, true));
    }

    @Override
    public void giveInformation(Information inf) {

        //show the user this card
        this.UIHandler.peekAtCard(inf.owner(), inf.index());
    }

    @Override
    public Pair<Player, Integer> otherPlayerCardTarget() {
        List<Player> candidates = GameEngine.getInstance().getPlayers().stream()
                .filter(p -> !p.equals(this))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }
        return this.UIHandler.promptCardTarget("Choose a player to target", candidates, true);
    }

    @Override
    public Pair<Player, Integer> selfCardTarget() {
        Integer index = this.UIHandler.promptCardIndex("Choose one of your cards", true);
        return index == null ? null : new Pair<>(this, index);
    }

    @Override
    public Pair<Player, Integer> anyPlayerCardTarget() {
        List<Player> candidates = GameEngine.getInstance().getPlayers();
        if (candidates.isEmpty()) {
            return null;
        }
        return this.UIHandler.promptCardTarget("Choose any player to target", candidates, false);
    }

    @Override
    public boolean wantsToSwap(Rank rank) {
        String message = rank == Rank.JACK ? "Use the Jack to swap two cards?" : "Use the Queen to swap two cards?";
        return this.UIHandler.promptConfirm(message);
    }
}
