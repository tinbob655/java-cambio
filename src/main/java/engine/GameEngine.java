package engine;


import javafx.util.Pair;
import model.card.Card;
import model.card.Deck;
import model.card.Discard;
import model.card.Rank;
import model.player.Bot;
import model.player.Information;
import model.player.Human;
import model.player.Player;
import model.state.GameState;
import model.state.Move;
import ui.UI;

import java.util.*;

//SINGLETON
public final class GameEngine {

    private static GameEngine instance;
    private final List<Player> players = new ArrayList<>();
    private int turnIndex;
    private Player cambioCalledBy;
    private GameState state;
    private Move lastMove;
    private Optional<Card> lastDrawnCard = Optional.empty();

    private GameEngine() {
        this.turnIndex = 0;
        this.cambioCalledBy = null;
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
        if (this.cambioCalledBy != null) {

            //someone has called cambio, need to work out how many turns until it is their turn again
            int callerIndex = this.players.indexOf(this.cambioCalledBy);
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

    public Optional<Player> getWinner() {

        //only can have a winner if the state says game over
        if (!this.getState().isGameOver()) {
            return Optional.empty();
        }

        //game is confirmed over, find the player with the best hand
        TreeMap<Integer, List<Player>> playerScores = new TreeMap<>();
        for (Player p : this.players) {
            int score = p.getHand().value();
            playerScores.computeIfAbsent(score, k -> new ArrayList<>()).add(p);
        }
        List<Player> winners = playerScores.firstEntry().getValue();
        switch (winners.size()) {
            case 0 -> {
                return Optional.empty();
            }
            case 1 -> {
                return Optional.of(winners.get(0));
            }
            default -> {

                //we have a tiebreaker situation
                //a player who called cambio cannot win a tiebreaker
                winners.removeIf(p -> p.equals(this.cambioCalledBy));

                //the player with the largest hand size wins the tiebreaker
                int maxHandSize = winners.stream()
                        .mapToInt(p -> p.getHand().size())
                        .max()
                        .orElseThrow();

                winners.removeIf(p -> p.getHand().size() != maxHandSize);

                //hopefully we now have a definite winner
                if (winners.size() > 1) {
                    System.out.println("Could not resolve tiebreaker...");
                }
                return Optional.of(winners.get(0));
            }
        }
    }

    public void turn() {

        //TODO: BOTS AREN'T PLAYING WHEN HUMAN GETS A SPECIAL CARD

        //get the move we want to perform
        Player currentPlayer = this.players.get(this.turnIndex);
        Move mv = currentPlayer.turn(this.getState());

        Optional<Card> drawnCard = mv.drawFromDeck()
                ? Deck.getInstance().peek()
                : Discard.getInstance().peek();

        //do the move
        this.lastMove = mv;
        this.lastDrawnCard = drawnCard;
        this.turnIndex = (this.turnIndex + 1) % this.players.size();
        this.state = this.advance(mv);
    }

    private GameState advance(Move mv) {

        //need to decode and execute a move
        Player owner = mv.commencedBy();

        //the player may have called cambio
        if (mv.cambioCalled()) {
            this.cambioCalledBy = owner;
            return this.recomputeState();
        }

        //do drawing from deck / discard
        Card drawnCard = mv.drawFromDeck() ? Deck.getInstance().poll().orElseThrow() : Discard.getInstance().poll().orElseThrow();

        boolean shouldSwap = mv.swap();
        if (mv.finalHand() != null) {
            boolean handChanges = !mv.finalHand().equals(owner.getHand());
            if (shouldSwap != handChanges) {
                shouldSwap = handChanges;
            }
        }

        //use our card
        if (shouldSwap) {

            //we want to swap the drawn card with the card in the swap index
            Card oldCard = owner.getHand().getCardAt(mv.swapIndex()).orElseThrow();
            owner.getHand().setCardAt(mv.swapIndex(), drawnCard);
            Discard.getInstance().add(oldCard);
        }
        else {

            //special effects only apply if we discard the card
            this.doSpecialCard(drawnCard.rank(), owner);

            //we are not swapping so just discard the card we picked up
            Discard.getInstance().add(drawnCard);
        }

        return this.recomputeState();
    }

    private void doSpecialCard(Rank rank, Player currentTurn) {

        switch (rank) {

            case SEVEN, EIGHT -> {

                //a seven or eight lets us look at another player's card
                this.showCard(currentTurn, TargetType.OTHER);
            }

            case NINE, TEN -> {

                //a nine or ten lets us look at one of our own cards
                this.showCard(currentTurn, TargetType.SELF);
            }

            case JACK -> {

                //a jack lets us swap any two cards
                doSwap(rank, currentTurn);
            }

            case QUEEN -> {

                //a queen lets us look at any card and then optionally do a swap
                this.showCard(currentTurn, TargetType.ANY);
                doSwap(rank, currentTurn);
            }
        }
    }

    private void doSwap(Rank rank, Player currentTurn) {

        if (!currentTurn.wantsToSwap(rank)) {
            return;
        }

        currentTurn.beginSwapTargeting();

        Pair<Player, Integer> target1;
        Pair<Player, Integer> target2;
        try {
            target1 = currentTurn.anyPlayerCardTarget();
            if (target1 == null) {
                return;
            }
            target2 = currentTurn.anyPlayerCardTarget();
            if (target2 == null) {
                return;
            }
        }
        finally {
            currentTurn.endTargeting();
        }

        this.swapTwoCards(target1.getKey(), target1.getValue(), target2.getKey(), target2.getValue());
        currentTurn.recordSwap(target1, target2);
    }

    private void swapTwoCards(Player player1, int index1, Player player2, int index2) {

        Card oldPlayer1Card = player1.getHand().getCardAt(index1).orElseThrow();
        Card oldPlayer2Card = player2.getHand().getCardAt(index2).orElseThrow();
        player1.getHand().setCardAt(index1, oldPlayer2Card);
        player2.getHand().setCardAt(index2, oldPlayer1Card);
    }

    private enum TargetType {SELF, OTHER, ANY};

    private void showCard(Player currentTurn, TargetType type) {

        currentTurn.beginPeekTargeting();

        //get our target information
        Pair<Player, Integer> target;
        try {
            switch (type) {
                case SELF -> target = currentTurn.selfCardTarget();
                case OTHER -> target = currentTurn.otherPlayerCardTarget();
                case ANY -> target = currentTurn.anyPlayerCardTarget();
                default -> throw new IllegalArgumentException("TargetType must be SELF, OTHER or ANY");
            }
        }
        finally {
            currentTurn.endTargeting();
        }

        //the player may decline our offer
        if (target == null) {
            return;
        }

        //show the current player the target card
        Card showCard = target.getKey().getHand().getCardAt(target.getValue()).orElseThrow();
        currentTurn.giveInformation(new Information(target.getKey(), showCard, target.getValue()));
    }

}
