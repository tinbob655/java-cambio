package ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.card.Card;
import model.card.Discard;
import model.card.Hand;
import model.card.Rank;
import model.card.Suit;
import model.player.Human;
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
    private static final String SWAP_GLOW   = "#f0cc55";
    private static final String TURN_BANNER_BG = "#123b26";

    private static final Font FONT_TITLE   = Font.font("Georgia", FontWeight.BOLD,    38);
    private static final Font FONT_LABEL   = Font.font("Georgia", FontWeight.BOLD,    14);
    private static final Font FONT_SMALL   = Font.font("Georgia", FontPosture.ITALIC, 13);
    private static final Font FONT_MSG     = Font.font("Georgia", FontPosture.ITALIC, 15);
    private static final Font FONT_BTN     = Font.font("Georgia", FontWeight.BOLD,    15);
    private static final Font FONT_RANK    = Font.font("Georgia", FontWeight.BOLD,    17);
    private static final Font FONT_SUIT_SM = Font.font("Georgia", 13);
    private static final Font FONT_SUIT_LG = Font.font("Georgia", 30);
    private static final Font FONT_TURN    = Font.font("Georgia", FontWeight.BOLD,    22);

    private static final Duration SWAP_POP_TIME     = Duration.millis(260);
    private static final Duration SWAP_SETTLE_TIME  = Duration.millis(320);
    private static final Duration SWAP_HOLD_TIME    = Duration.millis(780);
    private static final Duration LINE_FADE_IN_TIME = Duration.millis(220);
    private static final Duration LINE_HOLD_TIME    = Duration.millis(520);
    private static final Duration LINE_FADE_OUT_TIME = Duration.millis(260);
    private static final double ARROW_HEAD_LENGTH = 12.0;
    private static final double ARROW_HEAD_WIDTH  = 8.0;

    // ══════════════════════════════════════════════════════════════════════
    //  SINGLETON & JAVAFX STARTUP
    // ══════════════════════════════════════════════════════════════════════

    private static UI instance;
    private static final CountDownLatch START_LATCH = new CountDownLatch(1);

    private Stage     stage;
    private BorderPane root;
    private StackPane sceneRoot;
    private HBox      pilesRow;
    private HBox      handRow;
    private FlowPane  opponentsRow;
    private HBox      drawnCardRow;
    private VBox      buttonRow;
    private Label     messageLabel;
    private Label     turnLabel;
    private Label     thinkingLabel;
    private Label     drawnCardLabel;
    private StackPane drawnCardSlot;
    private StackPane drawPileCardNode;
    private StackPane discardPileCardNode;
    private Pane      animationLayer;

    private final List<Player> players = new ArrayList<>();
    private Player perspectivePlayer;
    private final Map<Player, List<Optional<Card>>> lastHands = new IdentityHashMap<>();
    private PendingSwap pendingSwap;

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

        // ── Top: turn banner + title ─────────────────────────────────────
        turnLabel = new Label("");
        turnLabel.setFont(FONT_TURN);
        turnLabel.setTextFill(Color.web(GOLD_BRIGHT));
        turnLabel.setPadding(new Insets(6, 14, 6, 14));
        turnLabel.setStyle(
            "-fx-background-color: " + TURN_BANNER_BG + ";" +
                "-fx-background-radius: 6;" +
                "-fx-border-color: " + GOLD + ";" +
                "-fx-border-radius: 6;"
        );

        thinkingLabel = new Label("");
        thinkingLabel.setFont(FONT_SMALL);
        thinkingLabel.setTextFill(Color.web(TEXT_DIM));

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

        topArea.getChildren().addAll(title, rule);

        VBox statusBox = new VBox(6, turnLabel, thinkingLabel);
        statusBox.setAlignment(Pos.TOP_LEFT);

        BorderPane topPane = new BorderPane();
        topPane.setLeft(statusBox);
        topPane.setCenter(topArea);
        BorderPane.setMargin(topPane, new Insets(0, 0, 20, 0));
        BorderPane.setMargin(statusBox, new Insets(4, 0, 0, 0));
        root.setTop(topPane);

        // ── Centre: piles + status message ────────────────────────────────
        VBox centerContent = new VBox(20);
        centerContent.setAlignment(Pos.CENTER);

        opponentsRow = new FlowPane(24, 12);
        opponentsRow.setAlignment(Pos.CENTER);
        opponentsRow.setPrefWrapLength(980);

        pilesRow = new HBox(60);
        pilesRow.setAlignment(Pos.CENTER);

        drawnCardRow = new HBox(12);
        drawnCardRow.setAlignment(Pos.CENTER);
        drawnCardRow.setVisible(false);
        drawnCardRow.setManaged(false);

        drawnCardLabel = new Label("Drawn card");
        drawnCardLabel.setFont(FONT_SMALL);
        drawnCardLabel.setTextFill(Color.web(TEXT_DIM));
        drawnCardSlot = buildCardNode(Optional.empty(), true);
        drawnCardRow.getChildren().addAll(drawnCardLabel, drawnCardSlot);

        messageLabel = new Label("");
        messageLabel.setFont(FONT_MSG);
        messageLabel.setTextFill(Color.web(TEXT_DIM));

        centerContent.getChildren().addAll(opponentsRow, pilesRow, drawnCardRow, messageLabel);

        animationLayer = new Pane();
        animationLayer.setMouseTransparent(true);
        animationLayer.setPickOnBounds(false);

        root.setCenter(centerContent);

        // ── Bottom: hand ─────────────────────────────────────────────────
        VBox bottom = new VBox(18);
        bottom.setAlignment(Pos.CENTER);

        handRow = new HBox(14);
        handRow.setAlignment(Pos.CENTER);

        bottom.getChildren().add(handRow);
        BorderPane.setMargin(bottom, new Insets(16, 0, 0, 0));
        root.setBottom(bottom);

        // ── Right: action buttons ────────────────────────────────────────
        Label actionsLabel = new Label("Actions");
        actionsLabel.setFont(FONT_SMALL);
        actionsLabel.setTextFill(Color.web(TEXT_DIM));

        buttonRow = new VBox(10);
        buttonRow.setAlignment(Pos.TOP_CENTER);
        buttonRow.setFillWidth(true);

        VBox actionPanel = new VBox(12, actionsLabel, buttonRow);
        actionPanel.setAlignment(Pos.TOP_CENTER);
        actionPanel.setPadding(new Insets(10, 12, 10, 12));
        actionPanel.setPrefWidth(220);
        root.setRight(actionPanel);

        // ── Stage ──────────────────────────────────────────────────────────
        sceneRoot = new StackPane(root, animationLayer);
        StackPane.setAlignment(animationLayer, Pos.TOP_LEFT);
        animationLayer.prefWidthProperty().bind(sceneRoot.widthProperty());
        animationLayer.prefHeightProperty().bind(sceneRoot.heightProperty());
        Scene scene = new Scene(sceneRoot, 1280, 780);
        stage.setTitle("Cambio");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API  (identical signatures to the text UI)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registers the full player list so the UI can render opponents and
     * animate swaps for any player.
     */
    public void setPlayers(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        this.players.clear();
        this.players.addAll(players);

        this.perspectivePlayer = players.stream()
                .filter(p -> p instanceof Human)
                .findFirst()
                .orElse(players.get(0));

        lastHands.clear();
        lastHands.putAll(snapshotHands(this.players));
    }

    /**
     * Redraws the board at the start of a turn:
      *   - updates both piles
      *   - renders opponents face-down and your hand as hidden slots
      *   - clears buttons and the message label
     *
     * Blocks until the FX thread finishes rendering so the game loop never
     * races ahead of what the player can see.
     */
    public void displayState(GameState state) {
        List<Player> renderPlayers = resolvePlayers(state);
        Player viewer = resolvePerspective(renderPlayers, state.getCurrentTurn());
        Map<Player, List<Optional<Card>>> currentHands = snapshotHands(renderPlayers);
        List<SwapEvent> swapEvents = detectSwaps(lastHands, currentHands);
        List<SwapLineEvent> swapLineEvents = detectSwapLineEvents(lastHands, currentHands);

        CompletableFuture<Void> rendered = new CompletableFuture<>();
        Platform.runLater(() -> {
            clearAnimationLayer();
            turnLabel.setText(state.getCurrentTurn().getName() + "'s turn");
            updateThinkingLabel(state.getCurrentTurn());
            updatePiles(state);
            updateDrawnCard(Optional.empty());

            Map<Player, List<StackPane>> slotMap = new IdentityHashMap<>();
            renderOpponents(renderPlayers, viewer, slotMap);
            updateHand(viewer.getHand(), -1, viewer, slotMap);  // -1 = all face-down

            buttonRow.getChildren().clear();
            String swapSummary = formatSwapSummary(swapEvents, viewer);
            messageLabel.setText(swapSummary == null ? "" : "  ›  " + swapSummary);

            lastHands.clear();
            lastHands.putAll(currentHands);

            layoutNow();

            Animation swapLineAnimation = playPendingSwapLineAnimation(slotMap);
            Animation handSwapLineAnimation = playSwapLineAnimations(swapLineEvents, slotMap);
            Animation swapLinesCombined = combineParallelAnimations(swapLineAnimation, handSwapLineAnimation);
            Animation swapAnimation = playSwapAnimations(swapEvents, slotMap, viewer);
            Animation swapCombo = combineParallelAnimations(swapLinesCombined, swapAnimation);

            if (swapCombo == null) {
                rendered.complete(null);
                return;
            }

            swapCombo.setOnFinished(e -> rendered.complete(null));
            swapCombo.play();
        });
        await(rendered);
    }

    /**
     * Updates the italic status line beneath the piles.
      * e.g. "Alice drew ♠K" or "Draw pile reshuffled."
     * Non-blocking — fire and forget.
     */
    public void displayMessage(String message) {
        Platform.runLater(() -> messageLabel.setText("  ›  " + message));
    }

    /**
     * Two-step turn prompt. Blocks the game thread while the player decides.
     *
      * Step 1 — "Draw from Deck", "Draw from Discard [♠K]", "Call Cambio!"
      * Step 2 — "Discard drawn card", "Swap into slot [0]", "Swap into slot [1]", …
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

        CompletableFuture<Void> drawnShown = new CompletableFuture<>();
        Platform.runLater(() -> {
            updateDrawnCard(drawnCard, true);
            if (!drawFromDeck) {
                updateDiscardPreviewAfterDraw(state.getDiscardPile());
            }
            drawnShown.complete(null);
        });
        await(drawnShown);

        // ── Step 2: discard or swap ───────────────────────────────────────
        List<Move> candidates = legal.stream()
                .filter(m -> m.drawFromDeck() == drawFromDeck)
                .sorted(Comparator.comparingInt(m -> m.swap() ? m.swapIndex() : -1))
                .collect(Collectors.toList());

        CompletableFuture<Move> actionFuture = new CompletableFuture<>();
        Platform.runLater(() -> showActionButtons(candidates, actionFuture));

        Move chosen = await(actionFuture);
        Platform.runLater(() -> updateDrawnCard(Optional.empty()));
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
        String label = state.getDiscardPile().peek()
            .map(c -> "Draw from Discard  [" + cardString(c) + "]")
            .orElse("Draw from Discard");
        Button discardButton = goldButton(label, () -> future.complete(DrawChoice.DISCARD));
        if (!discardOk) {
            discardButton.setDisable(true);
            discardButton.setOpacity(0.45);
            discardButton.setEffect(null);
        }
        buttonRow.getChildren().add(discardButton);

        buttonRow.getChildren().add(
                redButton("Call Cambio!", () -> future.complete(DrawChoice.CAMBIO)));
    }

    private void showActionButtons(List<Move> candidates, CompletableFuture<Move> future) {
        buttonRow.getChildren().clear();

        for (Move m : candidates) {
            String label = !m.swap()
                    ? "Discard drawn card"
                    : "Swap into slot [" + m.swapIndex() + "]";

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
        drawPileCardNode = buildCardNode(Optional.empty(), true);
        discardPileCardNode = buildCardNode(state.getDiscardPile().peek(), false);
        pilesRow.getChildren().addAll(
            buildPileBox("Draw Pile", drawPileCardNode),
            buildPileBox("Discard",   discardPileCardNode)
        );
    }

    private void updateDiscardPreviewAfterDraw(model.card.Discard discardPile) {
        if (discardPileCardNode == null || discardPile == null) {
            return;
        }

        List<Card> history = discardPile.getHistory();
        Optional<Card> next = Optional.empty();
        if (history.size() >= 2) {
            next = Optional.of(history.get(history.size() - 2));
        }
        renderCardNode(discardPileCardNode, next, !next.isPresent());
    }

    private void updateDrawnCard(Optional<Card> card, boolean revealFace) {
        if (drawnCardRow == null || drawnCardSlot == null) {
            return;
        }

        if (card != null && card.isPresent()) {
            if (revealFace) {
                renderCardNode(drawnCardSlot, card, false);
            } else {
                renderCardNode(drawnCardSlot, Optional.empty(), true);
            }
            drawnCardRow.setVisible(true);
            drawnCardRow.setManaged(true);
        } else {
            renderCardNode(drawnCardSlot, Optional.empty(), true);
            drawnCardRow.setVisible(false);
            drawnCardRow.setManaged(false);
        }
    }

    private void updateDrawnCard(Optional<Card> card) {
        updateDrawnCard(card, true);
    }

    private void renderOpponents(List<Player> renderPlayers, Player viewer,
                                 Map<Player, List<StackPane>> slotMap) {
        opponentsRow.getChildren().clear();

        boolean hasOpponents = false;
        for (Player p : renderPlayers) {
            if (p == viewer) {
                continue;
            }
            opponentsRow.getChildren().add(buildOpponentBox(p, slotMap));
            hasOpponents = true;
        }

        opponentsRow.setVisible(hasOpponents);
        opponentsRow.setManaged(hasOpponents);
    }

    private VBox buildOpponentBox(Player opponent, Map<Player, List<StackPane>> slotMap) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle(
                "-fx-background-color: " + FELT_LIGHT + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 8, 0, 0, 2);"
        );

        Label name = new Label(opponent.getName());
        name.setFont(FONT_LABEL);
        name.setTextFill(Color.WHITE);

        HBox cards = new HBox(10);
        cards.setAlignment(Pos.CENTER);
        List<StackPane> slots = renderHandRow(cards, null, opponent.getHand(), -1, false);
        slotMap.put(opponent, slots);

        box.getChildren().addAll(name, cards);
        return box;
    }

    private VBox buildPileBox(String title, StackPane cardNode) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);

        Label lbl = new Label(title);
        lbl.setFont(FONT_SMALL);
        lbl.setTextFill(Color.web(TEXT_DIM));

        box.getChildren().addAll(lbl, cardNode);
        return box;
    }

    /**
     * Renders the player's four-card hand.
     *
     * @param revealIndex  Which position to show face-up. Pass -1 for all face-down.
     */
    private List<StackPane> updateHand(Hand hand, int revealIndex,
                                       Player owner, Map<Player, List<StackPane>> slotMap) {
        List<StackPane> slots = renderHandRow(handRow, "Your hand", hand, revealIndex, true);
        if (owner != null && slotMap != null) {
            slotMap.put(owner, slots);
        }
        return slots;
    }

    private void updateHand(Hand hand, int revealIndex) {
        updateHand(hand, revealIndex, null, null);
    }

    private List<StackPane> renderHandRow(HBox targetRow, String labelText,
                                          Hand hand, int revealIndex, boolean showIndices) {
        targetRow.getChildren().clear();

        if (labelText != null && !labelText.isBlank()) {
            Label lbl = new Label(labelText);
            lbl.setFont(FONT_SMALL);
            lbl.setTextFill(Color.web(TEXT_DIM));
            HBox.setMargin(lbl, new Insets(0, 18, 0, 0));
            targetRow.getChildren().add(lbl);
        }

        List<StackPane> slots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            VBox slot = new VBox(5);
            slot.setAlignment(Pos.CENTER);

            if (showIndices) {
                Label idx = new Label("[" + i + "]");
                idx.setFont(FONT_SMALL);
                idx.setTextFill(Color.web(TEXT_DIM));
                slot.getChildren().add(idx);
            }

            boolean faceDown = (i != revealIndex);
            StackPane cardNode = buildCardNode(hand.getCardAt(i), faceDown);
            slot.getChildren().add(cardNode);
            targetRow.getChildren().add(slot);
            slots.add(cardNode);
        }
        return slots;
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
        renderCardNode(pane, card, faceDown);
        return pane;
    }

    private void renderCardNode(StackPane pane, Optional<Card> card, boolean faceDown) {
        pane.getChildren().clear();

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
        btn.setMaxWidth(Double.MAX_VALUE);
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

    private static final class SwapEvent {
        private final Player player;
        private final int index;

        private SwapEvent(Player player, int index) {
            this.player = player;
            this.index = index;
        }
    }

    private static final class CardPosition {
        private final Player player;
        private final int index;

        private CardPosition(Player player, int index) {
            this.player = player;
            this.index = index;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CardPosition other)) {
                return false;
            }
            return this.player == other.player && this.index == other.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(this.player), this.index);
        }
    }

    private static final class SwapLineEvent {
        private final CardPosition from;
        private final CardPosition to;

        private SwapLineEvent(CardPosition from, CardPosition to) {
            this.from = from;
            this.to = to;
        }
    }

    private static final class PendingSwap {
        private final Player player;
        private final int index;
        private final boolean fromDeck;

        private PendingSwap(Player player, int index, boolean fromDeck) {
            this.player = player;
            this.index = index;
            this.fromDeck = fromDeck;
        }
    }

    /**
     * Queues a draw animation for the next render pass (typically for bots).
     */
    public void queueDrawAnimation(Move move, Optional<Card> drawnCard) {
        if (move == null || move.commencedBy() == null) {
            return;
        }
        if (move.swap()) {
            this.pendingSwap = new PendingSwap(move.commencedBy(), move.swapIndex(), move.drawFromDeck());
        }
    }

    private List<Player> resolvePlayers(GameState state) {
        if (!players.isEmpty()) {
            return new ArrayList<>(players);
        }
        return Collections.singletonList(state.getCurrentTurn());
    }

    private Player resolvePerspective(List<Player> renderPlayers, Player fallback) {
        if (perspectivePlayer != null) {
            for (Player p : renderPlayers) {
                if (p == perspectivePlayer) {
                    return perspectivePlayer;
                }
            }
        }

        for (Player p : renderPlayers) {
            if (p instanceof Human) {
                return p;
            }
        }
        return renderPlayers.isEmpty() ? fallback : renderPlayers.get(0);
    }

    private Map<Player, List<Optional<Card>>> snapshotHands(List<Player> renderPlayers) {
        Map<Player, List<Optional<Card>>> snapshot = new IdentityHashMap<>();
        for (Player p : renderPlayers) {
            snapshot.put(p, new ArrayList<>(p.getHand().getCards()));
        }
        return snapshot;
    }

    private List<SwapEvent> detectSwaps(Map<Player, List<Optional<Card>>> previous,
                                        Map<Player, List<Optional<Card>>> current) {
        List<SwapEvent> swaps = new ArrayList<>();
        for (Map.Entry<Player, List<Optional<Card>>> entry : current.entrySet()) {
            Player player = entry.getKey();
            List<Optional<Card>> curr = entry.getValue();
            List<Optional<Card>> prev = previous.get(player);
            if (prev == null || prev.size() != curr.size()) {
                continue;
            }

            int changedIndex = -1;
            for (int i = 0; i < curr.size(); i++) {
                if (!Objects.equals(curr.get(i), prev.get(i))) {
                    if (changedIndex != -1) {
                        changedIndex = -2;
                        break;
                    }
                    changedIndex = i;
                }
            }

            if (changedIndex >= 0) {
                swaps.add(new SwapEvent(player, changedIndex));
            }
        }
        return swaps;
    }

    private List<SwapLineEvent> detectSwapLineEvents(Map<Player, List<Optional<Card>>> previous,
                                                     Map<Player, List<Optional<Card>>> current) {
        Map<Card, CardPosition> previousPositions = mapCardPositions(previous);
        Map<Card, CardPosition> currentPositions = mapCardPositions(current);

        Map<CardPosition, CardPosition> moves = new HashMap<>();
        for (Map.Entry<Card, CardPosition> entry : previousPositions.entrySet()) {
            CardPosition currentPos = currentPositions.get(entry.getKey());
            if (currentPos == null) {
                continue;
            }

            CardPosition previousPos = entry.getValue();
            if (!previousPos.equals(currentPos)) {
                moves.put(previousPos, currentPos);
            }
        }

        List<SwapLineEvent> swapLines = new ArrayList<>();
        Set<CardPosition> used = new HashSet<>();
        for (Map.Entry<CardPosition, CardPosition> entry : moves.entrySet()) {
            CardPosition from = entry.getKey();
            CardPosition to = entry.getValue();
            if (used.contains(from) || used.contains(to)) {
                continue;
            }

            CardPosition reciprocal = moves.get(to);
            if (reciprocal != null && reciprocal.equals(from)) {
                swapLines.add(new SwapLineEvent(from, to));
                used.add(from);
                used.add(to);
            }
        }

        return swapLines;
    }

    private Map<Card, CardPosition> mapCardPositions(Map<Player, List<Optional<Card>>> snapshot) {
        Map<Card, CardPosition> positions = new HashMap<>();
        for (Map.Entry<Player, List<Optional<Card>>> entry : snapshot.entrySet()) {
            Player player = entry.getKey();
            List<Optional<Card>> cards = entry.getValue();
            if (cards == null) {
                continue;
            }

            for (int i = 0; i < cards.size(); i++) {
                Optional<Card> card = cards.get(i);
                if (card != null && card.isPresent()) {
                    positions.put(card.get(), new CardPosition(player, i));
                }
            }
        }
        return positions;
    }

    private Animation playSwapAnimations(List<SwapEvent> swapEvents,
                                         Map<Player, List<StackPane>> slotMap,
                                         Player viewer) {
        if (swapEvents.isEmpty()) {
            return null;
        }

        List<Animation> animations = new ArrayList<>();
        for (SwapEvent event : swapEvents) {
            List<StackPane> slots = slotMap.get(event.player);
            if (slots == null || event.index < 0 || event.index >= slots.size()) {
                continue;
            }

            animations.add(buildSwapAnimation(slots.get(event.index)));
        }

        if (animations.isEmpty()) {
            return null;
        }

        ParallelTransition group = new ParallelTransition();
        group.getChildren().addAll(animations);
        return group;
    }

    private Animation playSwapLineAnimations(List<SwapLineEvent> swapLineEvents,
                                             Map<Player, List<StackPane>> slotMap) {
        if (swapLineEvents == null || swapLineEvents.isEmpty()) {
            return null;
        }

        List<Animation> animations = new ArrayList<>();
        for (SwapLineEvent event : swapLineEvents) {
            StackPane from = resolveSlot(slotMap, event.from);
            StackPane to = resolveSlot(slotMap, event.to);
            if (from == null || to == null) {
                continue;
            }

            Animation line = buildMoveLineAnimation(from, to);
            if (line != null) {
                animations.add(line);
            }
        }

        if (animations.isEmpty()) {
            return null;
        }

        ParallelTransition group = new ParallelTransition();
        group.getChildren().addAll(animations);
        return group;
    }

    private StackPane resolveSlot(Map<Player, List<StackPane>> slotMap, CardPosition position) {
        if (slotMap == null || position == null) {
            return null;
        }
        List<StackPane> slots = slotMap.get(position.player);
        if (slots == null || position.index < 0 || position.index >= slots.size()) {
            return null;
        }
        return slots.get(position.index);
    }

    private Animation playPendingSwapLineAnimation(Map<Player, List<StackPane>> slotMap) {
        if (pendingSwap == null) {
            return null;
        }

        PendingSwap swap = pendingSwap;
        pendingSwap = null;

        StackPane source = swap.fromDeck ? drawPileCardNode : discardPileCardNode;
        List<StackPane> slots = slotMap.get(swap.player);
        if (source == null || slots == null || swap.index < 0 || swap.index >= slots.size()) {
            return null;
        }

        return buildMoveLineAnimation(source, slots.get(swap.index));
    }

    private Animation combineAnimations(Animation first, Animation second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new SequentialTransition(first, second);
    }

    private Animation combineParallelAnimations(Animation first, Animation second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new ParallelTransition(first, second);
    }

    private Animation buildMoveLineAnimation(Node source, Node target) {
        if (animationLayer == null || source == null || target == null) {
            return null;
        }

        Point2D sourcePoint = nodeCenterInLayer(source);
        Point2D targetPoint = nodeCenterInLayer(target);
        if (sourcePoint == null || targetPoint == null) {
            return null;
        }

        javafx.scene.shape.Line line = new javafx.scene.shape.Line(
                sourcePoint.getX(), sourcePoint.getY(),
                targetPoint.getX(), targetPoint.getY()
        );
        line.setStroke(Color.web(SWAP_GLOW));
        line.setStrokeWidth(3.0);
        line.setOpacity(0.0);
        line.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        Polygon startArrow = buildArrowHead(sourcePoint, targetPoint);
        Polygon endArrow = buildArrowHead(targetPoint, sourcePoint);

        animationLayer.getChildren().addAll(line, startArrow, endArrow);

        FadeTransition fadeInLine = new FadeTransition(LINE_FADE_IN_TIME, line);
        fadeInLine.setFromValue(0.0);
        fadeInLine.setToValue(1.0);

        FadeTransition fadeInStart = new FadeTransition(LINE_FADE_IN_TIME, startArrow);
        fadeInStart.setFromValue(0.0);
        fadeInStart.setToValue(1.0);

        FadeTransition fadeInEnd = new FadeTransition(LINE_FADE_IN_TIME, endArrow);
        fadeInEnd.setFromValue(0.0);
        fadeInEnd.setToValue(1.0);

        ParallelTransition fadeIn = new ParallelTransition(fadeInLine, fadeInStart, fadeInEnd);
        PauseTransition hold = new PauseTransition(LINE_HOLD_TIME);

        FadeTransition fadeOutLine = new FadeTransition(LINE_FADE_OUT_TIME, line);
        fadeOutLine.setFromValue(1.0);
        fadeOutLine.setToValue(0.0);

        FadeTransition fadeOutStart = new FadeTransition(LINE_FADE_OUT_TIME, startArrow);
        fadeOutStart.setFromValue(1.0);
        fadeOutStart.setToValue(0.0);

        FadeTransition fadeOutEnd = new FadeTransition(LINE_FADE_OUT_TIME, endArrow);
        fadeOutEnd.setFromValue(1.0);
        fadeOutEnd.setToValue(0.0);

        ParallelTransition fadeOut = new ParallelTransition(fadeOutLine, fadeOutStart, fadeOutEnd);
        SequentialTransition seq = new SequentialTransition(fadeIn, hold, fadeOut);
        seq.setOnFinished(e -> animationLayer.getChildren().removeAll(line, startArrow, endArrow));
        return seq;
    }

    private Polygon buildArrowHead(Point2D tip, Point2D from) {
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        double backX = tip.getX() - ARROW_HEAD_LENGTH * cos;
        double backY = tip.getY() - ARROW_HEAD_LENGTH * sin;
        double leftX = backX + (ARROW_HEAD_WIDTH / 2.0) * sin;
        double leftY = backY - (ARROW_HEAD_WIDTH / 2.0) * cos;
        double rightX = backX - (ARROW_HEAD_WIDTH / 2.0) * sin;
        double rightY = backY + (ARROW_HEAD_WIDTH / 2.0) * cos;

        Polygon arrow = new Polygon(
                tip.getX(), tip.getY(),
                leftX, leftY,
                rightX, rightY
        );
        arrow.setFill(Color.web(SWAP_GLOW));
        arrow.setOpacity(0.0);
        return arrow;
    }

    private Point2D nodeCenterInLayer(Node node) {
        if (node.getScene() == null) {
            return null;
        }
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        double centerX = bounds.getMinX() + bounds.getWidth() / 2.0;
        double centerY = bounds.getMinY() + bounds.getHeight() / 2.0;
        return animationLayer.sceneToLocal(centerX, centerY);
    }

    private void clearAnimationLayer() {
        if (animationLayer != null) {
            animationLayer.getChildren().clear();
        }
    }

    private void layoutNow() {
        if (sceneRoot != null) {
            sceneRoot.applyCss();
            sceneRoot.layout();
            return;
        }
        if (root != null) {
            root.applyCss();
            root.layout();
        }
    }

    private Animation buildSwapAnimation(StackPane slot) {
        Region glow = new Region();
        glow.setPrefSize(72, 104);
        glow.setMinSize(72, 104);
        glow.setStyle(
                "-fx-background-color: rgba(240, 204, 85, 0.25);" +
                        "-fx-border-color: " + SWAP_GLOW + ";" +
                        "-fx-border-width: 2;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;"
        );
        glow.setOpacity(0.0);

        Label tag = new Label("SWAP");
        tag.setFont(FONT_SMALL);
        tag.setTextFill(Color.web(SWAP_GLOW));
        tag.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                        "-fx-background-radius: 4;" +
                        "-fx-padding: 2 6;"
        );
        tag.setOpacity(0.0);
        StackPane.setAlignment(tag, Pos.TOP_RIGHT);
        StackPane.setMargin(tag, new Insets(4, 4, 0, 0));

        slot.getChildren().addAll(glow, tag);

        ScaleTransition pop = new ScaleTransition(SWAP_POP_TIME, slot);
        pop.setFromX(0.92);
        pop.setFromY(0.92);
        pop.setToX(1.08);
        pop.setToY(1.08);

        ScaleTransition settle = new ScaleTransition(SWAP_SETTLE_TIME, slot);
        settle.setFromX(1.08);
        settle.setFromY(1.08);
        settle.setToX(1.0);
        settle.setToY(1.0);

        FadeTransition glowIn = new FadeTransition(Duration.millis(140), glow);
        glowIn.setFromValue(0.0);
        glowIn.setToValue(0.9);

        FadeTransition tagIn = new FadeTransition(Duration.millis(140), tag);
        tagIn.setFromValue(0.0);
        tagIn.setToValue(1.0);

        FadeTransition glowOut = new FadeTransition(Duration.millis(220), glow);
        glowOut.setFromValue(0.9);
        glowOut.setToValue(0.0);

        FadeTransition tagOut = new FadeTransition(Duration.millis(220), tag);
        tagOut.setFromValue(1.0);
        tagOut.setToValue(0.0);

        ParallelTransition popGroup = new ParallelTransition(pop, glowIn, tagIn);
        PauseTransition hold = new PauseTransition(SWAP_HOLD_TIME);
        ParallelTransition fadeGroup = new ParallelTransition(glowOut, tagOut);
        SequentialTransition sequence = new SequentialTransition(popGroup, settle, hold, fadeGroup);
        sequence.setOnFinished(e -> {
            slot.getChildren().removeAll(glow, tag);
        });

        return sequence;
    }

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
        if (c.rank() == Rank.JOKER) {
            return "★";
        }
        return suitSymbol(c.suit()) + rankShort(c);
    }

    private void updateThinkingLabel(Player currentTurn) {
        if (thinkingLabel == null) {
            return;
        }
        if (currentTurn instanceof Human) {
            thinkingLabel.setText("");
        } else {
            thinkingLabel.setText(currentTurn.getName() + " is thinking...");
        }
    }

    private String formatSwapSummary(List<SwapEvent> events, Player viewer) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        if (events.size() == 1) {
            SwapEvent event = events.get(0);
            String who = event.player == viewer ? "You" : event.player.getName();
            return who + " swapped slot [" + event.index + "]";
        }

        return "Multiple swaps happened";
    }

    private int handScore(Hand hand) {
        int total = 0;
        for (int i = 0; i < 4; i++) {
            total += hand.getCardAt(i).map(Card::getValue).orElse(0);
        }
        return total;
    }
}