package engine;

import model.card.Card;
import model.player.Player;
import model.state.GameState;
import model.state.Move;

import java.util.List;
import java.util.Optional;

public interface EngineAPI {

    GameState getState();
    void addPlayer(Player p);
    Move getLastMove();
    Optional<Card> getLastDrawnCard();
    Optional<Player> getWinner();
    void turn();
    List<Player> getPlayers();
}
