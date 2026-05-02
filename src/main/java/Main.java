import engine.GameEngine;
import model.card.Card;
import model.card.Deck;
import model.player.Bot;
import model.player.Human;
import model.player.Information;
import model.player.Player;
import ui.UI;
import ui.UIProxy;

import java.util.List;

public class Main {

    private static final int BOT_COUNT = 2;
    private static final UIProxy UIHandler = new UIProxy();

    public static void main(String[] args) {

        //make the engine
        buildEngine();
        GameEngine engine = GameEngine.getInstance();
        UI ui = UI.getInstance();
        UIHandler.setPlayers(engine.getPlayers());
        UIHandler.displayState(engine.getState());

        //the game starts by showing each player their edge two cards
        doPeekPhase();

        //peek phase is followed by main gameplay
        doGameLoop();


        //after cambio is called and each other player gets another turn, the game is over
        doGameOver();

        ui.waitUntilClosed();
    }

    private static void buildEngine() {
        GameEngine engine = GameEngine.getInstance();
        Deck deck = Deck.getInstance();

        //add the human player
        engine.addPlayer(new Human("You", deck.makeHand()));

        //add bots
        for (int i = 0; i < BOT_COUNT; i++) {
            engine.addPlayer(new Bot("Bot " + i, deck.makeHand()));
        }
    }

    private static void doPeekPhase() {

        GameEngine engine = GameEngine.getInstance();
        List<Player> players = engine.getPlayers();
        int[] peekIndexes = {0, 3};
        for (Player p : players) {
            for (int index : peekIndexes) {

                Card c = p.getHand().getCardAt(index).orElseThrow();
                Information inf = new Information(p, c, index);

                p.giveInformation(inf);
            }
        }
    }

    private static void doGameLoop() {

        GameEngine engine = GameEngine.getInstance();
        UI ui = UI.getInstance();

        while (!engine.getState().isGameOver()) {

            Player currentTurn = engine.getState().getCurrentTurn();

            //do a turn
            engine.turn();

            //update UI
            ui.queueDrawAnimation(engine.getLastMove(), engine.getLastDrawnCard());
            UIHandler.displayState(engine.getState());

            //bots cannot do anything while a human is playing
            if (currentTurn instanceof Human) {
                UIHandler.waitForAcknowledgement();
            }
        }
    }

    private static void doGameOver() {

        GameEngine engine = GameEngine.getInstance();

        Player winner = engine.getWinner().orElseThrow();
        UIHandler.displayEndGame(engine.getPlayers(), winner);
    }
}
