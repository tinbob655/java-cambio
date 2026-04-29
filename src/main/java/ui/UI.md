# UI — Interaction Guide

`UI` is a singleton. Always access it via `UI.getInstance()`, never construct it directly.

```java
UI ui = UI.getInstance();
```

---

## Methods

### `displayState(GameState state)`
Clears the screen and renders the current board: opponent hands (face-down), the discard pile's top card, the draw pile placeholder, and your hand as face-down slots with position indices. Call this at the start of every turn before prompting for a move.

```java
ui.displayState(state);
```

---

### `setPlayers(List<Player> players)`
Registers the full player list so the UI can render opponents and animate swaps for any player. Call once after you build the engine and add players.

```java
ui.setPlayers(engine.getPlayers());
```

---

### `promptMove(GameState state)` → `Optional<Move>`
The main turn prompt. Walks the current player through two steps:

1. **Draw source** — deck, discard pile (shows top card inline), or call Cambio
2. **Action** — discard the drawn card, or swap it into a hand position

Returns `Optional.empty()` if the player calls Cambio. Returns `Optional.of(move)` otherwise. The `GameEngine` is responsible for acting on the result.

```java
Optional<Move> move = ui.promptMove(state);

if (move.isEmpty()) {
    // player called Cambio — handle end-of-round logic here
} else {
    // apply move.get() to the game state
}
```

---

### `displayMessage(String message)`
Prints a single status line prefixed with `→`. Use this for feedback after a move is applied — e.g. confirming what card was drawn, or flagging an illegal state.

```java
ui.displayMessage("Alice drew ♠K");
ui.displayMessage("Draw pile is empty — reshuffling discards");
```

---

### `displayEndGame(List<Player> players, Player winner)`
Clears the screen and shows the final summary: every player's hand, their score, and the winner. Call once the game is over.

```java
ui.displayEndGame(players, winner);
```

---

### `peekAtCard(Player player, int index)`
Shows the card at a given hand position to the player, then waits for them to press Enter before clearing the screen. Used during setup when Cambio rules allow players to look at their two bottom cards before play begins.

```java
ui.displayMessage(player.getName() + ", look at your two bottom cards.");
ui.peekAtCard(player, 2);
ui.peekAtCard(player, 3);
```

---

### `promptConfirm(String question)` → `boolean`
Asks a yes/no question and returns `true` for yes, `false` for no. Useful for confirming actions or asking setup questions.

```java
boolean goFirst = ui.promptConfirm("Would you like to go first?");
```

---

## Typical turn loop

```java
UI ui = UI.getInstance();

while (!gameOver) {

    GameState state = engine.getState();
    ui.displayState(state);

    Optional<Move> move = ui.promptMove(state);

    if (move.isEmpty()) {
        // Cambio called — finish the round
        gameOver = true;
    } else {
        engine.applyMove(move.get());
        engine.advanceTurn();
    }
}

ui.displayEndGame(engine.getPlayers(), engine.getWinner());
```

---

## Typical setup sequence

```java
UI ui = UI.getInstance();
GameEngine engine = GameEngine.getInstance();

// Add players
engine.addPlayer(new HumanPlayer("Alice", startingHand));
engine.addPlayer(new HumanPlayer("Bob",   startingHand));

// Register players for UI rendering
ui.setPlayers(engine.getPlayers());

// Each player peeks at their bottom two cards
for (Player p : engine.getPlayers()) {
    ui.displayMessage(p.getName() + ", it's your turn to peek.");
    ui.peekAtCard(p, 2);
    ui.peekAtCard(p, 3);
}

// Begin the game loop
```