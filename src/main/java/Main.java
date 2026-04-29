import engine.GameEngine;
import model.card.Deck;
import model.player.Bot;
import model.player.Human;
import ui.UI;

public class Main {

    private static final int BOT_COUNT = 2;

    public static void main(String[] args) {

        //make the engine
        buildEngine();
        GameEngine engine = GameEngine.getInstance();
        UI ui = UI.getInstance();
        ui.setPlayers(engine.getPlayers());
        ui.displayState(engine.getState());

        //the game starts by showing each player their first two cards

        //game loop
        while (!GameEngine.getInstance().getState().isGameOver()) {

            //do a turn
            engine.turn();

            ui.queueDrawAnimation(engine.getLastMove(), engine.getLastDrawnCard());

            //update UI
            ui.displayState(engine.getState());
        }
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
}
