package ui;

import javafx.util.Pair;
import model.player.Player;
import model.state.GameState;
import model.state.Move;

import java.util.List;
import java.util.Optional;

public interface UI_API {

    void setPlayers(List<Player> players);
    void displayState(GameState state);
    void displayMessage(String message);
    void displayBigMessage(String message);
    Optional<Move> promptMove(GameState state);
    boolean promptConfirm(String question);
    Pair<Player, Integer> promptCardTarget(String prompt, List<Player> candidates, boolean allowCancel);
    Integer promptCardIndex(String prompt, boolean allowCancel);
    void waitForAcknowledgement();
    void peekAtCard(Player player, int index);
    void displayEndGame(List<Player> players, Player winner);
}
