package model.state;

import model.card.Hand;
import model.player.Player;

public record Move(Player commencedBy, Hand finalHand, boolean drawFromDeck, boolean swap, int swapIndex, boolean cambioCalled) {
}
