package model.state;

import model.card.Hand;

public record Move(Hand finalHand, boolean drawFromDeck, boolean swap, int swapIndex) {
}
