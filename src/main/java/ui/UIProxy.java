package ui;

import javafx.util.Pair;
import model.player.Player;
import model.state.GameState;
import model.state.Move;

import java.util.List;
import java.util.Optional;

public final class UIProxy implements UI_API {

    private static final UI ui = UI.getInstance();

    @Override
    public void setPlayers(List<Player> players) {
        ui.setPlayers(players);
    }

    @Override
    public void displayState(GameState state) {
        ui.displayState(state);
    }

    @Override
    public void displayMessage(String message) {
        ui.displayMessage(message);
    }

    @Override
    public void displayBigMessage(String message) {
        ui.displayBigMessage(message);
    }

    @Override
    public Optional<Move> promptMove(GameState state) {
        return ui.promptMove(state);
    }

    @Override
    public boolean promptConfirm(String question) {
        return ui.promptConfirm(question);
    }

    @Override
    public Pair<Player, Integer> promptCardTarget(String prompt, List<Player> candidates, boolean allowCancel) {
        return ui.promptCardTarget(prompt, candidates, allowCancel);
    }

    @Override
    public Integer promptCardIndex(String prompt, boolean allowCancel) {
        return ui.promptCardIndex(prompt, allowCancel);
    }

    @Override
    public void waitForAcknowledgement() {
        ui.waitForAcknowledgement();
    }

    @Override
    public void peekAtCard(Player player, int index) {
        ui.peekAtCard(player, index);
    }

    @Override
    public void displayEndGame(List<Player> players, Player winner) {
        ui.displayEndGame(players, winner);
    }
}
