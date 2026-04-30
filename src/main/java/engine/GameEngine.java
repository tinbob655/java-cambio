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
        this.lastMove = mv;
        this.lastDrawnCard = drawnCard;
        this.turnIndex = (this.turnIndex + 1) % this.players.size();
        this.state = this.advance(mv);
    }

    private GameState advance(Move mv) {

        //need to decode and execute a move
        Player owner = mv.commencedBy();

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
                if (!shouldPerformOptionalSwap(currentTurn, Rank.JACK)) {
                    return;
                }

                if (currentTurn instanceof Bot bot) {
                    bot.beginSwapTargeting();
                }

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
                } finally {
                    if (currentTurn instanceof Bot bot) {
                        bot.endTargeting();
                    }
                }

                this.swapTwoCards(target1.getKey(), target1.getValue(), target2.getKey(), target2.getValue());
                if (currentTurn instanceof Bot bot) {
                    bot.recordSwap(target1, target2);
                }
            }

            case QUEEN -> {

                //a queen lets us look at any card and then optionally do a swap
                this.showCard(currentTurn, TargetType.ANY);
                if (!shouldPerformOptionalSwap(currentTurn, Rank.QUEEN)) {
                    return;
                }

                if (currentTurn instanceof Bot bot) {
                    bot.beginSwapTargeting();
                }

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
                } finally {
                    if (currentTurn instanceof Bot bot) {
                        bot.endTargeting();
                    }
                }

                this.swapTwoCards(target1.getKey(), target1.getValue(), target2.getKey(), target2.getValue());
                if (currentTurn instanceof Bot bot) {
                    bot.recordSwap(target1, target2);
                }
            }
        }
    }

    private boolean shouldPerformOptionalSwap(Player currentTurn, Rank rank) {
        if (currentTurn instanceof Human) {
            String message = (rank == Rank.JACK)
                    ? "Use the Jack to swap two cards?"
                    : "Use the Queen to swap two cards?";
            return UI.getInstance().promptConfirm(message);
        }
        if (currentTurn instanceof Bot bot) {
            return bot.wantsToSwap();
        }
        return true;
    }

    private void swapTwoCards(Player player1, int index1, Player player2, int index2) {

        Card oldPlayer1Card = player1.getHand().getCardAt(index1).orElseThrow();
        Card oldPlayer2Card = player2.getHand().getCardAt(index2).orElseThrow();
        player1.getHand().setCardAt(index1, oldPlayer2Card);
        player2.getHand().setCardAt(index2, oldPlayer1Card);
    }

    private enum TargetType {SELF, OTHER, ANY};

    private void showCard(Player currentTurn, TargetType type) {

        if (currentTurn instanceof Bot bot) {
            bot.beginPeekTargeting();
        }

        //get our target information
        Pair<Player, Integer> target;
        try {
            switch (type) {
                case SELF -> target = currentTurn.selfCardTarget();
                case OTHER -> target = currentTurn.otherPlayerCardTarget();
                case ANY -> target = currentTurn.anyPlayerCardTarget();
                default -> throw new IllegalArgumentException("TargetType must be SELF, OTHER or ANY");
            }
        } finally {
            if (currentTurn instanceof Bot bot) {
                bot.endTargeting();
            }
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
