package model.player;

import engine.GameEngine;
import javafx.util.Pair;
import model.card.Card;
import model.card.Hand;
import model.card.Rank;
import model.card.Suit;
import model.state.GameState;
import model.state.Move;

import java.util.*;
import java.util.stream.Collectors;

public class Bot extends Player {

    //this is how many moves we look ahead when doing minimax
    private static final int MINIMAX_DEPTH = 4;

    //if we think we have a score <= this then we will call cambio
    private static final int CAMBIO_THRESHOLD = 8;

    //at what point the bot thinks it knows enough about its own hand
    private static final int OWN_HAND_KNOWLEDGE_THRESHOLD = 2;


    private static final List<Card> FULL_DECK = new ArrayList<>();
    private final Set<Information> knowledge = new HashSet<>();

    public Bot(String name, Hand startingHand) {
        super(name, startingHand);

        //create a deck of all cards to use in minimax
        for (Rank r : Rank.values())
            for (Suit s : Suit.values())
                if (r != Rank.JOKER) {
                    FULL_DECK.add(new Card(r, s));
                }

        //add jokers
        FULL_DECK.add(new Card(Rank.JOKER, Suit.SPADES));
        FULL_DECK.add(new Card(Rank.JOKER, Suit.HEARTS));
    }


    @Override
    public void giveInformation(Information inf) {

        //remove information if we are replacing it
        knowledge.removeIf(i -> i.owner().equals(inf.owner()) && i.index() == inf.index());

        knowledge.add(inf);
    }

    @Override
    public Pair<Player, Integer> otherPlayerCardTarget() {

        //if we know nothing about someone then ask about them
        for (Player p : GameEngine.getInstance().getPlayers()) {
            if (this.knowledge.stream().noneMatch(inf -> inf.owner().equals(p)) && !p.equals(this)) {

                //we know nothing about player 'p'. Ask about them
                int randomIndex = new Random().nextInt(4);
                return new Pair<>(p, randomIndex);
            }
        }

        //it is now given that we know about all players
        //find our the player we know the least about
        Pair<Player, int[]> minPair = this.knowledge.stream()

                //get rid our ourselves
                .filter(inf -> !inf.owner().equals(this))

                //group knowledge by each player
                .collect(Collectors.groupingBy(
                        Information::owner,
                        Collectors.mapping(Information::index, Collectors.toList())
                ))
                .entrySet().stream()

                //extract player we know the least about
                .min(Comparator.comparingInt(entry -> entry.getValue().size()))

                //create the array of indexes we know
                .map(entry -> new Pair<>(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(Integer::intValue).toArray()
                ))
                .orElse(null);

        //now request our information
        assert minPair != null;
        int randomIndex = new Random().nextInt(minPair.getValue().length);
        return new Pair<>(minPair.getKey(), minPair.getValue()[randomIndex]);
    }

    @Override
    public Pair<Player, Integer> selfCardTarget() {

        //choose a random card in our own hand which we don't know yet
        List<Integer> knownCardIndexes = this.knowledge.parallelStream()
                .filter(inf -> inf.owner().equals(this))
                .map(Information::index)
                .toList();

        if (knownCardIndexes.size() >= 4) {

            //we already know everything in our hand, decline the offer
            return null;
        }

        for (int i = 0; i < 4; i++) {
            if (!knownCardIndexes.contains(i)) {

                //we found a card we didn't know in our hand
                return new Pair<>(this, i);
            }
        }

        return null;
    }

    @Override
    public Pair<Player, Integer> anyPlayerCardTarget() {

        //we want to know a threshold amount of our own cards
        long knownCardsCount = this.knowledge.parallelStream()
                .filter(inf -> inf.owner().equals(this))
                .count();
        if (knownCardsCount >= OWN_HAND_KNOWLEDGE_THRESHOLD) {

            //we know enough about our own hand
            return this.otherPlayerCardTarget();
        }
        else {

            //we do not know enough about our own hand
            return this.selfCardTarget();
        }
    }

    @Override
    public Move turn(GameState state) {

        //quick bit of card counting
        int unkEst = estimateUnknownCards(state);

        int knownTotal = knowledge.stream().filter(i -> i.owner().equals(this)).mapToInt(i -> i.card().getValue()).sum();
        long knownSlots = knowledge.stream().filter(i -> i.owner().equals(this)).count();

        //check if we want to call cambio
        if (knownTotal + (4 - knownSlots) * unkEst <= CAMBIO_THRESHOLD) {
            return new Move(this, this.getHand(), false, false, -1);
        }

        //do minimax on each possible move
        Move best = null;
        int  bestScore = Integer.MIN_VALUE;
        for (Move mv : state.legalMoves()) {

            //update our knowledge
            Information added = null, displaced = null;
            if (mv.swap()) {

                Optional<Card> drawn = mv.finalHand().getCardAt(mv.swapIndex());
                if (drawn.isPresent()) {

                    int idx = mv.swapIndex();
                    displaced = knowledge.stream()
                            .filter(i -> i.owner().equals(this) && i.index() == idx)
                            .findFirst().orElse(null);
                    knowledge.removeIf(i -> i.owner().equals(this) && i.index() == idx);
                    added = new Information(this, drawn.get(), idx);
                    knowledge.add(added);
                }
            }

            //rev up minimax
            int s = minimax(state, MINIMAX_DEPTH - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);

            //remove temp knowledge
            if (added != null) {
                knowledge.remove(added);
            }
            if (displaced != null) {
                knowledge.add(displaced);
            }

            if (s > bestScore) {
                bestScore = s; best = mv;
            }
        }

        return best != null ? best : state.legalMoves().iterator().next();
    }

    //tries to work out the average of all the cards we have not seen
    private int estimateUnknownCards(GameState state) {

        Set<Card> seen = new HashSet<>(state.getDiscardPile().getHistory());
        knowledge.forEach(i -> seen.add(i.card()));

        int unseenSum = FULL_DECK.stream().filter(c -> !seen.contains(c)).mapToInt(Card::getValue).sum();
        int unseenCount = (int) FULL_DECK.stream().filter(c -> !seen.contains(c)).count();
        return unseenCount > 0 ? (unseenSum / unseenCount) : 5;
    }

    private int score(GameState state) {

        //do some card counting
        int unkEst = estimateUnknownCards(state);

        int  res = 0;
        int knownOwnSlots = 0;

        for (Information inf : knowledge) {
            if (inf.owner().equals(this)) {

                //our hand being lower is good for us
                res -= inf.card().getValue();
                knownOwnSlots++;
            }
            else {

                //a higher opponent hand is a good thing for us
                res += inf.card().getValue();
            }
        }

        //punish the bot for not knowing its own cards
        res -= (4 - knownOwnSlots) * unkEst;

        //reward the bot for knowing more cards
        res += knowledge.size() * 5;

        return res;
    }

    private int minimax(GameState state, int depth, boolean maximising, int alpha, int beta) {

        //we might be done
        if (depth == 0 || state.isGameOver()) {
            return score(state);
        }

        if (maximising) {

            int best = Integer.MIN_VALUE;

            for (Move mv : state.legalMoves()) {

                //update our knowledge
                Information added = null, displaced = null;
                if (mv.swap()) {

                    Optional<Card> drawn = mv.finalHand().getCardAt(mv.swapIndex());
                    if (drawn.isPresent()) {

                        int idx   = mv.swapIndex();
                        displaced = knowledge.stream()
                                .filter(i -> i.owner().equals(this) && i.index() == idx)
                                .findFirst().orElse(null);
                        knowledge.removeIf(i -> i.owner().equals(this) && i.index() == idx);
                        added = new Information(this, drawn.get(), idx);
                        knowledge.add(added);
                    }
                }

                int val = minimax(state, depth - 1, false, alpha, beta);

                //remove temp knowledge
                if (added != null) {
                    knowledge.remove(added);
                }
                if (displaced != null) {
                    knowledge.add(displaced);
                }

                //do alpha-beta
                best  = Math.max(best, val);
                alpha = Math.max(alpha, best);

                if (beta <= alpha) break;
            }

            return best;

        }
        else {

            //we can't work out what our opponent will do so just assume they will improve their hand. -5 penalty
            return minimax(state, depth - 1, true, alpha, beta) - 5;
        }
    }
}