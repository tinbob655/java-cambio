package engine;


import model.card.Card;
import model.card.Deck;
import model.card.Discard;
import model.player.Player;
import model.state.GameState;
import model.state.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

//SINGLETON
public final class GameEngine {

    private static GameEngine instance;
    private final List<Player> players = new ArrayList<>();
    private int turnIndex;
    private Player cambiocalledBy;
    private GameState state;
    private Move lastMove;
    private Optional<Card> lastDrawnCard = Optional.empty();

    private GameEngine() {
        this.turnIndex = 0;
        this.cambiocalledBy = null;
        this.state = null;
    };

    public static GameEngine getInstance() {

        if (instance == null) {
            instance = new GameEngine();
        }
        return instance;
    }

    public GameState getState() {

        //state requires players
        if (this.players.isEmpty()) {
            throw new IllegalStateException("Cannot get a state before we have players");
        }

        //we may need to build a game state
        if (this.state == null) {
            this.state = this.recomputeState();
        }

        return this.state;
    }

    private GameState recomputeState() {

        //need to build a game state
        Deck deck = Deck.getInstance();
        Discard discard = Discard.getInstance();
        Player currentTurn = this.players.get(this.turnIndex);

        int turnsTillGameOver;
        if (this.cambiocalledBy != null) {

            //someone has called cambio, need to work out how many turns until it is their turn again
            int callerIndex = this.players.indexOf(this.cambiocalledBy);
            int playerCount = this.players.size();
            turnsTillGameOver = (callerIndex - this.turnIndex + playerCount) % playerCount;
        }
        else {
            turnsTillGameOver = -1;
        }
        return new GameState(deck, discard, currentTurn, turnsTillGameOver);
    }

    public void addPlayer(Player p) {
        this.players.add(p);
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(this.players);
    }

    public Move getLastMove() {
        return this.lastMove;
    }

    public Optional<Card> getLastDrawnCard() {
        return this.lastDrawnCard;
    }

    public void turn() {

        //get the move we want to perform
        Player currentPlayer = this.players.get(this.turnIndex);
        Move mv = currentPlayer.turn(this.getState());

        Optional<Card> drawnCard = mv.drawFromDeck()
                ? Deck.getInstance().peek()
                : Discard.getInstance().peek();

        //do the move
        this.state = this.advance(mv);

        this.lastMove = mv;
        this.lastDrawnCard = drawnCard;

        //increment the turn index
        this.turnIndex = (this.turnIndex + 1) % this.players.size();
    }

    private GameState advance(Move mv) {

        //need to decode and execute a move
        Player owner = mv.commencedBy();

        //do drawing from deck / discard
        Optional<Card> drawn = mv.drawFromDeck()
                ? Deck.getInstance().poll()
                : Discard.getInstance().poll();

        //resolve discards
        if (mv.swap()) {
            owner.getHand().getCardAt(mv.swapIndex())
                    .ifPresent(c -> Discard.getInstance().add(c));
        } else {
            drawn.ifPresent(c -> Discard.getInstance().add(c));
        }

        //update the owner's hand
        owner.getHand().setCards(mv.finalHand().getCards());

        return this.recomputeState();
    }
}
