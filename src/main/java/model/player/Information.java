package model.player;

import model.card.Card;

public record Information(Player owner, Card card, int index) {
}
