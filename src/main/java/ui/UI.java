package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import model.card.Card;
import model.card.Hand;
import model.card.Suit;
import model.player.Player;
import model.state.GameState;
import model.state.Move;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

// SINGLETON
public final class UI {

    // ══════════════════════════════════════════════════════════════════════
    //  COLOURS & FONTS  (single source of truth — change here, not inline)
    // ══════════════════════════════════════════════════════════════════════

    private static final String FELT_DARK   = "#0b3320";
    private static final String FELT_LIGHT  = "#145232";
    private static final String GOLD        = "#d4af37";
    private static final String GOLD_BRIGHT = "#f0cc55";
    private static final String CARD_RED    = "#c0392b";
    private static final String CARD_BLACK  = "#1a1a1a";
    private static final String CAMBIO_RED  = "#7b0000";
    private static final String TEXT_DIM    = "#8fad99";

    private static final Font FONT_TITLE   = Font.font("Georgia", FontWeight.BOLD,    38);
    private static final Font FONT_LABEL   = Font.font("Georgia", FontWeight.BOLD,    14);
    private static final Font FONT_SMALL   = Font.font("Georgia", FontPosture.ITALIC, 13);
    private static final Font FONT_MSG     = Font.font("Georgia", FontPosture.ITALIC, 15);
    private static final Font FONT_BTN     = Font.font("Georgia", FontWeight.BOLD,    15);
    private static final Font FONT_RANK    = Font.font("Georgia", FontWeight.BOLD,    17);
    private static final Font FONT_SUIT_SM = Font.font("Georgia", 13);
    private static final Font FONT_SUIT_LG = Font.font("Georgia", 30);

    // ══════════════════════════════════════════════════════════════════════
    //  SINGLETON & JAVAFX STARTUP
    // ══════════════════════════════════════════════════════════════════════

    private static UI instance;
    private static final CountDownLatch START_LATCH = new CountDownLatch(1);

    private Stage     stage;
    private BorderPane root;
    private HBox      pilesRow;
    private HBox      handRow;
    private FlowPane  buttonRow;
    private Label     messageLabel;
    private Label     turnLabel;

    private UI() {}

    public static UI getInstance() {
        if (instance == null) {
            synchronized (UI.class) {
                if (instance == null) {
                    instance = new UI();
                    // JavaFX must run on its own thread.
                    // Daemon = true so it does not block JVM exit when the game ends.
                    Thread fxThread = new Thread(() -> Application.launch(FxBootstrapApp.class));
                    fxThread.setDaemon(true);
                    fxThread.start();
                    try {
                        START_LATCH.await();   // block until buildScene() finishes
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return instance;
    }

    // Called by JavaFX launchers to hand the Stage back to the singleton
    public static void onFXReady(Stage s) {
        if (instance == null) {
            instance = new UI();
        }
        instance.stage = s;
        instance.buildScene();
        START_LATCH.countDown();
    }

    /**
     * Internal launcher for code paths that call UI.getInstance() directly.
     */
    public static final class FxBootstrapApp extends Application {
        @Override
        public void start(Stage primaryStage) {
            UI.onFXReady(primaryStage);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SCENE CONSTRUCTION  (runs once on the FX thread at startup)
    // ══════════════════════════════════════════════════════════════════════

    private void buildScene() {

        // ── Root pane ──────────────────────────────────────────────────────
        root = new BorderPane();
        root.setStyle("-fx-background-color: " + FELT_DARK + ";");
        root.setPadding(new Insets(24, 32, 24, 32));

        // Vignette: dark inner shadow on all edges to evoke a felt table
        InnerShadow vignette = new InnerShadow(60, Color.BLACK);
        vignette.setChoke(0.05);
        root.setEffect(vignette);

        // ── Top: title + current-player label ─────────────────────────────
        VBox topArea = new VBox(6);
        topArea.setAlignment(Pos.CENTER);

        Label title = new Label("♠   C A M B I O   ♠");
        title.setFont(FONT_TITLE);
        title.setTextFill(Color.web(GOLD));
        DropShadow titleGlow = new DropShadow(28, Color.web(GOLD));
        titleGlow.setSpread(0.15);
        title.setEffect(titleGlow);

        // Thin gold rule beneath the title
        Region rule = new Region();
        rule.setPrefHeight(1);
        rule.setMaxWidth(320);
        rule.setStyle("-fx-background-color: " + GOLD + "; -fx-opacity: 0.35;");

        turnLabel = new Label("");
        turnLabel.setFont(FONT_LABEL);
        turnLabel.setTextFill(Color.web(GOLD_BRIGHT));

        topArea.getChildren().addAll(title, rule, turnLabel);
        BorderPane.setMargin(topArea, new Insets(0, 0, 20, 0));
        root.setTop(topArea);

        // ── Centre: piles + status message ────────────────────────────────
        VBox center = new VBox(20);
        center.setAlignment(Pos.CENTER);

        pilesRow = new HBox(60);
        pilesRow.setAlignment(Pos.CENTER);

        messageLabel = new Label("");
        messageLabel.setFont(FONT_MSG);
        messageLabel.setTextFill(Color.web(TEXT_DIM));

        center.getChildren().addAll(pilesRow, messageLabel);
        root.setCenter(center);

        // ── Bottom: hand + action buttons ─────────────────────────────────
        VBox bottom = new VBox(18);
        bottom.setAlignment(Pos.CENTER);

        handRow = new HBox(14);
        handRow.setAlignment(Pos.CENTER);

        // FlowPane wraps gracefully when there are four swap buttons + discard
        buttonRow = new FlowPane(12, 10);
        buttonRow.setAlignment(Pos.CENTER);

        bottom.getChildren().addAll(handRow, buttonRow);
        BorderPane.setMargin(bottom, new Insets(16, 0, 0, 0));
        root.setBottom(bottom);

        // ── Stage ──────────────────────────────────────────────────────────
        Scene scene = new Scene(root, 980, 660);
        stage.setTitle("Cambio");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API  (identical signatures to the text UI)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Redraws the board at the start of a turn:
     *   - updates both piles
     *   - renders the current player's hand (all cards face-down)
     *   - clears buttons and the message label
     *
     * Blocks until the FX thread finishes rendering so the game loop never
     * races ahead of what the player can see.
     */
    public void displayState(GameState state) {
        CompletableFuture<Void> rendered = new CompletableFuture<>();
        Platform.runLater(() -> {
            turnLabel.setText(state.getCurrentTurn().getName() + "'s turn");
            updatePiles(state);
            updateHand(state.getCurrentTurn().getHand(), -1);  // -1 = all face-down
            buttonRow.getChildren().clear();
            messageLabel.setText("");
            rendered.complete(null);
        });
        await(rendered);
    }

    /**
     * Updates the italic status line beneath the piles.
     * e.g. "Alice drew K♠" or "Draw pile reshuffled."
     * Non-blocking — fire and forget.
     */
    public void displayMessage(String message) {
        Platform.runLater(() -> messageLabel.setText("  ›  " + message));
    }

    /**
     * Two-step turn prompt. Blocks the game thread while the player decides.
     *
     * Step 1 — "Draw from Deck", "Draw from Discard [K♠]", "Call Cambio!"
     * Step 2 — "Discard drawn card", "Swap [0] 7♣", "Swap [1] …", …
     *
     * Returns Optional.empty() if the player calls Cambio.
     */
    public Optional<Move> promptMove(GameState state) {

        Set<Move> legal = state.legalMoves();
        boolean deckAvailable    = legal.stream().anyMatch(Move::drawFromDeck);
        boolean discardAvailable = legal.stream().anyMatch(m -> !m.drawFromDeck());

        // ── Step 1: draw source ───────────────────────────────────────────
        CompletableFuture<DrawChoice> drawFuture = new CompletableFuture<>();
        Platform.runLater(() ->
                showDrawSourceButtons(state, deckAvailable, discardAvailable, drawFuture));

        DrawChoice choice = await(drawFuture);
        if (choice == null || choice == DrawChoice.CAMBIO) {
            return Optional.empty();
        }

        boolean drawFromDeck = (choice == DrawChoice.DECK);

        // Show what was drawn (peek is non-destructive after the Deck bug is fixed)
        Optional<Card> drawnCard = drawFromDeck
                ? state.getDrawPile().peek()
                : state.getDiscardPile().peek();
        drawnCard.ifPresent(c -> displayMessage("You drew: " + cardString(c)));

        // ── Step 2: discard or swap ───────────────────────────────────────
        List<Move> candidates = legal.stream()
                .filter(m -> m.drawFromDeck() == drawFromDeck)
                .sorted(Comparator.comparingInt(m -> m.swap() ? m.swapIndex() : -1))
                .collect(Collectors.toList());

        CompletableFuture<Move> actionFuture = new CompletableFuture<>();
        Platform.runLater(() ->
                showActionButtons(candidates, state.getCurrentTurn().getHand(), actionFuture));

        Move chosen = await(actionFuture);
        return chosen == null ? Optional.empty() : Optional.of(chosen);
    }

    /**
     * Shows a question with gold "Yes" and red "No" buttons.
     * Blocks until one is clicked.
     */
    public boolean promptConfirm(String question) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            messageLabel.setText("  ›  " + question);
            buttonRow.getChildren().clear();
            buttonRow.getChildren().addAll(
                    goldButton("Yes", () -> future.complete(true)),
                    redButton ("No",  () -> future.complete(false))
            );
        });
        Boolean result = await(future);
        return result != null && result;
    }

    /**
     * Reveals only the card at {@code index} — all other positions stay face-down.
     * Blocks until the player clicks "Hide".
     * Used at game start when players peek at their two bottom cards.
     */
    public void peekAtCard(Player player, int index) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            messageLabel.setText(
                    "  ›  " + player.getName() + " — peeking at position " + index);
            updateHand(player.getHand(), index);   // only slot `index` revealed
            buttonRow.getChildren().clear();
            buttonRow.getChildren().add(
                    goldButton("Hide", () -> {
                        updateHand(player.getHand(), -1);
                        buttonRow.getChildren().clear();
                        future.complete(null);
                    })
            );
        });
        await(future);
    }

    /**
     * Replaces the centre of the board with a final results screen:
     * each player's full hand and score, with the winner highlighted in gold.
     */
    public void displayEndGame(List<Player> players, Player winner) {
        CompletableFuture<Void> rendered = new CompletableFuture<>();
        Platform.runLater(() -> {

            VBox endPane = new VBox(20);
            endPane.setAlignment(Pos.CENTER);
            endPane.setPadding(new Insets(10));

            Label winnerBanner = new Label("★   " + winner.getName() + " wins!   ★");
            winnerBanner.setFont(Font.font("Georgia", FontWeight.BOLD, 30));
            winnerBanner.setTextFill(Color.web(GOLD));
            DropShadow bannerGlow = new DropShadow(24, Color.web(GOLD));
            winnerBanner.setEffect(bannerGlow);
            endPane.getChildren().add(winnerBanner);

            for (Player p : players) {
                int score     = handScore(p.getHand());
                boolean isWin = p.equals(winner);

                VBox block = new VBox(8);
                block.setAlignment(Pos.CENTER);
                block.setPadding(new Insets(10, 20, 10, 20));
                block.setStyle(
                        "-fx-background-color: " + (isWin ? "#1a5c35" : FELT_LIGHT) + ";" +
                                "-fx-background-radius: 10;" +
                                "-fx-effect: dropshadow(gaussian, black, 8, 0, 0, 2);"
                );

                Label nameScore = new Label(p.getName() + "   —   score: " + score);
                nameScore.setFont(FONT_LABEL);
                nameScore.setTextFill(isWin ? Color.web(GOLD) : Color.WHITE);

                HBox cards = new HBox(10);
                cards.setAlignment(Pos.CENTER);
                for (int i = 0; i < 4; i++) {
                    cards.getChildren().add(buildCardNode(p.getHand().getCardAt(i), false));
                }

                block.getChildren().addAll(nameScore, cards);
                endPane.getChildren().add(block);
            }

            turnLabel.setText("Game over");
            root.setCenter(endPane);
            buttonRow.getChildren().clear();
            rendered.complete(null);
        });
        await(rendered);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE — BUTTON ROWS
    // ══════════════════════════════════════════════════════════════════════

    private void showDrawSourceButtons(GameState state,
                                       boolean deckOk, boolean discardOk,
                                       CompletableFuture<DrawChoice> future) {
        buttonRow.getChildren().clear();

        if (deckOk) {
            buttonRow.getChildren().add(
                    goldButton("Draw from Deck",
                            () -> future.complete(DrawChoice.DECK)));
        }
        if (discardOk) {
            String label = state.getDiscardPile().peek()
                    .map(c -> "Draw from Discard  [" + cardString(c) + "]")
                    .orElse("Draw from Discard");
            buttonRow.getChildren().add(
                    goldButton(label, () -> future.complete(DrawChoice.DISCARD)));
        }

        buttonRow.getChildren().add(
                redButton("Call Cambio!", () -> future.complete(DrawChoice.CAMBIO)));
    }

    private void showActionButtons(List<Move> candidates, Hand hand,
                                   CompletableFuture<Move> future) {
        buttonRow.getChildren().clear();

        for (Move m : candidates) {
            String label = !m.swap()
                    ? "Discard drawn card"
                    : "Swap  [" + m.swapIndex() + "]  "
                    + hand.getCardAt(m.swapIndex()).map(this::cardString).orElse("—");

            Move captured = m;   // effectively final for the lambda
            buttonRow.getChildren().add(
                    goldButton(label, () -> {
                        buttonRow.getChildren().clear();
                        future.complete(captured);
                    })
            );
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE — COMPONENT BUILDERS
    // ══════════════════════════════════════════════════════════════════════

    private void updatePiles(GameState state) {
        pilesRow.getChildren().clear();
        pilesRow.getChildren().addAll(
                buildPileBox("Draw Pile", Optional.empty(),              true),
                buildPileBox("Discard",   state.getDiscardPile().peek(), false)
        );
    }

    private VBox buildPileBox(String title, Optional<Card> card, boolean faceDown) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);

        Label lbl = new Label(title);
        lbl.setFont(FONT_SMALL);
        lbl.setTextFill(Color.web(TEXT_DIM));

        box.getChildren().addAll(lbl, buildCardNode(card, faceDown));
        return box;
    }

    /**
     * Renders the player's four-card hand.
     *
     * @param revealIndex  Which position to show face-up. Pass -1 for all face-down.
     */
    private void updateHand(Hand hand, int revealIndex) {
        handRow.getChildren().clear();

        Label lbl = new Label("Your hand");
        lbl.setFont(FONT_SMALL);
        lbl.setTextFill(Color.web(TEXT_DIM));
        HBox.setMargin(lbl, new Insets(0, 18, 0, 0));
        handRow.getChildren().add(lbl);

        for (int i = 0; i < 4; i++) {
            VBox slot = new VBox(5);
            slot.setAlignment(Pos.CENTER);

            Label idx = new Label("[" + i + "]");
            idx.setFont(FONT_SMALL);
            idx.setTextFill(Color.web(TEXT_DIM));

            boolean faceDown = (i != revealIndex);
            slot.getChildren().addAll(idx, buildCardNode(hand.getCardAt(i), faceDown));
            handRow.getChildren().add(slot);
        }
    }

    /**
     * Builds one playing-card node.
     *
     * Face-up : white rounded rect, rank top-left, large suit centred, red/black.
     * Face-down: navy blue with a ◆ back pattern.
     * Empty slot: same navy back (an Optional.empty() with faceDown=true shows as back).
     */
    private StackPane buildCardNode(Optional<Card> card, boolean faceDown) {

        StackPane pane = new StackPane();
        pane.setPrefSize(72, 104);
        pane.setMinSize(72, 104);

        if (faceDown || card.isEmpty()) {
            pane.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, #1a3a6b, #0d2247);" +
                            "-fx-background-radius: 8;" +
                            "-fx-border-color: #3a5a9b;" +
                            "-fx-border-width: 1.5;" +
                            "-fx-border-radius: 8;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 8, 0, 2, 3);"
            );
            Label back = new Label("◆ ◆\n◆ ◆");
            back.setFont(Font.font("Georgia", 14));
            back.setTextFill(Color.web("#2a4a8b"));
            back.setAlignment(Pos.CENTER);
            pane.getChildren().add(back);

        } else {
            Card c   = card.get();
            boolean red = c.suit() == Suit.HEARTS || c.suit() == Suit.DIAMONDS;
            String suitColor = red ? CARD_RED : CARD_BLACK;

            pane.setStyle(
                    "-fx-background-color: white;" +
                            "-fx-background-radius: 8;" +
                            "-fx-border-color: #cccccc;" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 8;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 10, 0, 2, 3);"
            );

            // Top-left corner: rank over small suit
            VBox corner = new VBox(1);
            corner.setPadding(new Insets(5, 0, 0, 6));
            corner.setAlignment(Pos.TOP_LEFT);

            Label rankLbl = new Label(rankShort(c));
            rankLbl.setFont(FONT_RANK);
            rankLbl.setTextFill(Color.web(suitColor));

            Label suitSmall = new Label(suitSymbol(c.suit()));
            suitSmall.setFont(FONT_SUIT_SM);
            suitSmall.setTextFill(Color.web(suitColor));

            corner.getChildren().addAll(rankLbl, suitSmall);

            // Centre: large suit symbol
            Label suitLarge = new Label(suitSymbol(c.suit()));
            suitLarge.setFont(FONT_SUIT_LG);
            suitLarge.setTextFill(Color.web(suitColor));

            StackPane.setAlignment(corner,    Pos.TOP_LEFT);
            StackPane.setAlignment(suitLarge, Pos.CENTER);
            pane.getChildren().addAll(suitLarge, corner);
        }

        return pane;
    }

    // ── Button factories ──────────────────────────────────────────────────

    /** Gold button — all normal game actions. */
    private Button goldButton(String text, Runnable onClick) {
        return styledButton(text, GOLD, "#1a0a00", onClick);
    }

    /** Deep-red button — Cambio call and negative confirmations. */
    private Button redButton(String text, Runnable onClick) {
        return styledButton(text, CAMBIO_RED, "#ffffff", onClick);
    }

    private Button styledButton(String text, String bgHex, String fgHex, Runnable onClick) {
        Button btn = new Button(text);
        btn.setFont(FONT_BTN);
        btn.setPadding(new Insets(10, 22, 10, 22));
        btn.setStyle(
                "-fx-background-color: " + bgHex + ";" +
                        "-fx-text-fill: " + fgHex + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;"
        );

        // Glowing drop shadow that expands on hover
        DropShadow glow = new DropShadow(14, Color.web(bgHex));
        glow.setSpread(0.2);
        btn.setEffect(glow);

        btn.setOnMouseEntered(e -> glow.setRadius(28));
        btn.setOnMouseExited (e -> glow.setRadius(14));
        btn.setOnAction      (e -> onClick.run());
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE — UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private enum DrawChoice { DECK, DISCARD, CAMBIO }

    /**
     * Blocks the calling (game) thread until the CompletableFuture resolves.
     * The future is always resolved by a button click on the FX thread.
     */
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String suitSymbol(Suit suit) {
        return switch (suit) {
            case SPADES   -> "♠";
            case CLUBS    -> "♣";
            case HEARTS   -> "♥";
            case DIAMONDS -> "♦";
        };
    }

    private String rankShort(Card c) {
        return switch (c.rank()) {
            case JOKER -> "★";
            case ACE   -> "A";
            case JACK  -> "J";
            case QUEEN -> "Q";
            case KING  -> "K";
            default    -> String.valueOf(c.rank().getValue());
        };
    }

    private String cardString(Card c) {
        return rankShort(c) + suitSymbol(c.suit());
    }

    private int handScore(Hand hand) {
        int total = 0;
        for (int i = 0; i < 4; i++) {
            total += hand.getCardAt(i).map(Card::getValue).orElse(0);
        }
        return total;
    }
}