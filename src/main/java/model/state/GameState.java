package model.state;

import model.card.Card;
import model.card.Deck;
import model.card.Discard;
import model.card.Hand;
import model.player.Player;

import java.util.*;

public class GameState {

    private final Deck drawPile;
    private final Discard discardPile;
    private final Player currentTurn;
    private final int turnsTillGameOver;

    public GameState(Deck drawPile, Discard discardPile, Player currentTurn, int turnsTillGameOver) {
        this.drawPile = drawPile;
        this.discardPile = discardPile;
        this.currentTurn = currentTurn;
        this.turnsTillGameOver = turnsTillGameOver;
    }

    public Deck getDrawPile() {
        return this.drawPile;
    }
    public Discard getDiscardPile() {
        return this.discardPile;
    }
    public Player getCurrentTurn() {
        return this.currentTurn;
    }
    public boolean isGameOver() {
        return this.turnsTillGameOver == 0;
    }

    public Set<Move> legalMoves() {

        Set<Move> res = new HashSet<>();
        Hand currentHand = currentTurn.getHand();

        //repeat for drawing from deck and discard pile
        for (boolean drawFromDeck : new boolean[]{true, false}) {

            //attempt to get the card
            Optional<Card> drawn = drawFromDeck ? drawPile.peek() : discardPile.peek();
            if (drawn.isEmpty()) {
                continue;
            }
            Card drawnCard = drawn.get();

            //we might choose to discard the card
            res.add(new Move(currentTurn, currentHand, drawFromDeck, false, -1, false));

            //we might choose swap the card. We can swap with any card in our hand
            Hand oldHand = this.currentTurn.getHand();
            for (int i = 0; i < 4; i++) {

                int finalI = i;
                oldHand.getCardAt(i).ifPresent(c -> {

                    //a card exists here which means we can swap it
                    Hand newHand = oldHand.newHandWithSwapAt(finalI, drawnCard);
                    res.add(new Move(currentTurn, newHand, drawFromDeck, true, finalI, false));
                });
            }
        }

        return res;
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof GameState g)) {
            return false;
        }
        return (
                (g.getDrawPile().equals(this.drawPile))
                && (g.getDiscardPile().equals(this.discardPile))
                && (g.getCurrentTurn().equals(this.currentTurn))
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.drawPile, this.discardPile, this.currentTurn);
    }
}
