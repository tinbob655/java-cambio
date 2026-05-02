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

    //how many monte-carlo simulations we do per turn
    private static final int MONTE_CARLO_ITERATIONS = 1000;

    //we call cambio if we think our hand is <= this number
    private static final int CAMBIO_THRESHOLD = 5;

    private static final List<Card> FULL_DECK = new ArrayList<>();
    static {

        //we need to create our deck
        for (Rank r : Rank.values()) {
            if (r != Rank.JOKER) {
                for (Suit s : Suit.values()) {
                    FULL_DECK.add(new Card(r, s));
                }
            }
        }
        FULL_DECK.add(new Card(Rank.JOKER, Suit.SPADES));
        FULL_DECK.add(new Card(Rank.JOKER, Suit.HEARTS));
    }

    private final Set<Information> knowledge = new HashSet<>();

    /* there are three targeting moves:
        1. NONE: we are not trying to do anything.
        2. SWAP: we are prioritising getting rid of our own high cards
        3. PEEK: we are prioritising getting more information
     */
    private enum TargetingMode { NONE, SWAP, PEEK }
    private TargetingMode targetingMode = TargetingMode.NONE;

    private boolean swapFirstTargetPicked = false;
    private double lastUnseenAverage = 5.0;

    public Bot(String name, Hand startingHand) {
        super(name, startingHand);
    }


    //methods to change our targeting modes
    public void beginSwapTargeting() {
        this.targetingMode = TargetingMode.SWAP;
        this.swapFirstTargetPicked = false;
    }
    public void beginPeekTargeting() {
        this.targetingMode = TargetingMode.PEEK;
    }
    public void endTargeting() {
        this.targetingMode = TargetingMode.NONE;
        this.swapFirstTargetPicked = false;
    }


    //returns true if we can swap a worse card for a better one
    @Override
    public boolean wantsToSwap(Rank rank) {

        Pair<Player, Integer> myWorst  = getHighestValuedCard(this);
        Pair<Player, Integer> theirBest = getLowestValuedCard(getOpponents());

        if (myWorst != null && theirBest != null) {
            double myWorstVal   = getExpectedValue(myWorst.getKey(),   myWorst.getValue());
            double theirBestVal = getExpectedValue(theirBest.getKey(), theirBest.getValue());
            return myWorstVal > theirBestVal;
        }
        return false;
    }

    //if we do a swap we need to update our knowledge
    public void recordSwap(Pair<Player, Integer> t1, Pair<Player, Integer> t2) {

        Information inf1 = getKnowledgeAt(t1.getKey(), t1.getValue());
        Information inf2 = getKnowledgeAt(t2.getKey(), t2.getValue());

        knowledge.removeIf(i ->
                (i.owner().equals(t1.getKey()) && i.index() == t1.getValue()) ||
                        (i.owner().equals(t2.getKey()) && i.index() == t2.getValue())
        );

        if (inf1 != null) {
            knowledge.add(new Information(t2.getKey(), inf1.card(), t2.getValue()));
        }
        if (inf2 != null) {
            knowledge.add(new Information(t1.getKey(), inf2.card(), t1.getValue()));
        }
    }

    @Override
    public void giveInformation(Information inf) {
        knowledge.removeIf(i -> i.owner().equals(inf.owner()) && i.index() == inf.index());
        knowledge.add(inf);
    }

    @Override
    public Pair<Player, Integer> selfCardTarget() {
        if (targetingMode == TargetingMode.SWAP) {

            //always give away our highest card if we are trying to swap
            return getHighestValuedCard(this);
        }
        else {

            //if we want information, find a card we don't know about yet
            Pair<Player, Integer> unk = getUnknownCard(this);
            return unk != null ? unk : getHighestValuedCard(this);
        }
    }

    @Override
    public Pair<Player, Integer> otherPlayerCardTarget() {
        if (targetingMode == TargetingMode.SWAP) {

            //if we are swapping then always take the best possible card
            return getLowestValuedCard(getOpponents());
        }
        else {

            //if we are trying to get information then get a card we don't know about
            Pair<Player, Integer> unk = getUnknownCard(getOpponents());
            return unk != null ? unk : getLowestValuedCard(getOpponents());
        }
    }

    @Override
    public Pair<Player, Integer> anyPlayerCardTarget() {
        if (targetingMode == TargetingMode.SWAP) {

            //we are swapping
            //alternate picking our bad card then an opponent's good card
            if (!swapFirstTargetPicked) {
                swapFirstTargetPicked = true;
                return getHighestValuedCard(this);
            }
            else {
                swapFirstTargetPicked = false;
                return getLowestValuedCard(getOpponents());
            }
        }

        //we are not swapping
        else {

            //always try to find out about ourselves before other players
            Pair<Player, Integer> unkSelf = getUnknownCard(this);
            if (unkSelf != null) {
                return unkSelf;
            }
            Pair<Player, Integer> unkOther = getUnknownCard(getOpponents());
            return unkOther != null ? unkOther : getHighestValuedCard(this);
        }
    }

    @Override
    public Move turn(GameState state) {

        //turn uses a monte-carlo tree search

        //setup
        updateKnowledgeFromDiscard(state);
        List<Card> unseen = getUnseenCards(state);
        lastUnseenAverage = unseen.isEmpty() ? 5.0 : unseen.stream().mapToInt(Card::getValue).average().orElse(5.0);

        //we might want to call cambio
        double currentExpectedHandValue = 0;
        for (int i = 0; i < 4; i++) {
            currentExpectedHandValue += getExpectedValue(this, i);
        }
        if (currentExpectedHandValue <= CAMBIO_THRESHOLD) {

            //CAMBIO!
            return new Move(this, this.getHand(), false, false, -1, true);
        }

        //rev up monte-carlo
        Set<Move> legalMoves = state.legalMoves();
        MCTSNode root = new MCTSNode(null, null);
        root.expand(legalMoves);

        //simulate many iterations and take average to get most likely next cards
        for (int i = 0; i < MONTE_CARLO_ITERATIONS; i++) {

            MCTSNode selected = root.selectBestUCT();
            List<Card> simDeck = new ArrayList<>(unseen);
            Collections.shuffle(simDeck);
            double simulatedScore = simulatePlayout(selected.move, simDeck, state);
            selected.backpropagate(simulatedScore);
        }

        //after simulation return the most likely best move
        MCTSNode bestChild = null;
        double bestAvg = -Double.MAX_VALUE;
        for (MCTSNode child : root.children) {
            double avg = child.visits > 0 ? child.scoreSum / child.visits : -Double.MAX_VALUE;
            if (avg > bestAvg) {
                bestAvg = avg;
                bestChild = child;
            }
        }
        return bestChild != null ? bestChild.move : legalMoves.iterator().next();
    }

    //pretends we are at a certain point in the game and plays
    private double simulatePlayout(Move mv, List<Card> simDeck, GameState state) {

        //get a simulated hand
        Card[] mySimHand = new Card[4];
        for(int i = 0; i < 4; i++) {
            Information inf = getKnowledgeAt(this, i);
            mySimHand[i] = (inf != null) ? inf.card() : getRandomCard(simDeck);
        }

        //pretend we are drawing from the deck / discard pile
        Card drawnCard;
        if (mv.drawFromDeck()) {
            if (simDeck.isEmpty()) {
                return -1000;
            }
            drawnCard = simDeck.remove(0);
        }
        else {
            drawnCard = state.getDiscardPile().peek().orElse(null);
            if (drawnCard == null) {
                return -1000;
            }
        }

        //give our simulated state a score
        double utilityScore = 0;
        if (mv.swap() && mv.swapIndex() >= 0 && mv.swapIndex() < 4) {
            mySimHand[mv.swapIndex()] = drawnCard;
        }
        else {
            Rank r = drawnCard.rank();

            switch (r) {

                //reward the bot for peeking
                case SEVEN, EIGHT -> utilityScore += 2;

                //reward the bot for self-peeking
                case NINE, TEN -> utilityScore += 4;

                //deal with swapping
                case JACK, QUEEN -> {
                    if (wantsToSwap(r)) {

                        Pair<Player, Integer> myWorst = getHighestValuedCard(this);
                        Pair<Player, Integer> theirBest = getLowestValuedCard(getOpponents());
                        if (theirBest != null) {

                            //work out if a swap would benefit us
                            double valDiff = getExpectedValue(myWorst.getKey(), myWorst.getValue())
                                    - getExpectedValue(theirBest.getKey(), theirBest.getValue());
                            if (valDiff > 0) {
                                utilityScore += valDiff;
                            }
                        }
                    }
                }
            }
        }

        //calculate a value for the simulated play
        double finalHandValue = 0;
        for(Card c : mySimHand) {
            finalHandValue += c.getValue();
        }

        //NOTE: we are trying to minimise the value of our hand so give it a negative score
        return -finalHandValue + utilityScore;
    }

    //class for a single node in the monte-carlo tree
    private static final class MCTSNode {

        Move move;
        MCTSNode parent;
        List<MCTSNode> children = new ArrayList<>();
        int visits = 0;
        double scoreSum = 0;

        MCTSNode(Move move, MCTSNode parent) {
            this.move = move;
            this.parent = parent;
        }

        //create the children of this node
        private void expand(Set<Move> legalMoves) {
            for (Move mv : legalMoves) {
                children.add(new MCTSNode(mv, this));
            }
        }

        //returns the best child from this node
        private MCTSNode selectBestUCT() {
            MCTSNode best = null;
            double bestValue = -Double.MAX_VALUE;
            for (MCTSNode child : children) {

                //prioritize an unvisited node
                if (child.visits == 0) {
                    return child;
                }

                //give the child a score
                double uct = (child.scoreSum / child.visits) + 10.0 * Math.sqrt(2 * Math.log(this.visits) / child.visits);
                if (uct > bestValue) {
                    bestValue = uct;
                    best = child;
                }
            }
            return best;
        }

        //goes back up the tree to get a total score sum
        private void backpropagate(double score) {
            this.visits++;
            this.scoreSum += score;
            if (this.parent != null) {
                this.parent.backpropagate(score);
            }
        }
    }

    //each time someone discards a card we need to card count
    private void updateKnowledgeFromDiscard(GameState state) {

        List<Card> discardHistory = state.getDiscardPile().getHistory();
        if (!discardHistory.isEmpty()) {
            Card lastDiscard = discardHistory.get(discardHistory.size() - 1);
            knowledge.removeIf(inf -> inf.card().equals(lastDiscard));
        }
    }

    //helper to get all the cards we haven't seen yet
    private List<Card> getUnseenCards(GameState state) {
        List<Card> unseen = new ArrayList<>(FULL_DECK);
        Set<Card> seen = new HashSet<>(state.getDiscardPile().getHistory());
        knowledge.forEach(inf -> seen.add(inf.card()));
        unseen.removeIf(seen::contains);
        return unseen;
    }

    //tries to get the value of a card in the game. If we don't know, return an average of unknown cards
    private double getExpectedValue(Player p, int index) {
        Information inf = getKnowledgeAt(p, index);
        if (inf != null) {
            return inf.card().getValue();
        }
        return lastUnseenAverage;
    }

    //tries to get information about a card in the game
    private Information getKnowledgeAt(Player p, int index) {
        return knowledge.stream()
                .filter(i -> i.owner().equals(p) && i.index() == index)
                .findFirst().orElse(null);
    }

    //finds the worst card a player has based on our information
    private Pair<Player, Integer> getHighestValuedCard(Player p) {

        int bestIdx = 0;
        double highestVal = -Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            double val = getExpectedValue(p, i);

            if (val > highestVal) {
                highestVal = val;
                bestIdx = i;
            }
        }
        return new Pair<>(p, bestIdx);
    }

    //finds the best card a player has based on our information
    private Pair<Player, Integer> getLowestValuedCard(List<Player> players) {

        //make sure the player exists
        if (players.isEmpty()) {
            return null;
        }

        Player bestP = null;
        int bestIdx = -1;
        double lowestVal = Double.MAX_VALUE;

        for (Player p : players) {
            for (int i = 0; i < 4; i++) {
                double val = getExpectedValue(p, i);
                if (val < lowestVal) {
                    lowestVal = val;
                    bestP = p;
                    bestIdx = i;
                }
            }
        }

        return new Pair<>(bestP, bestIdx);
    }

    //finds any card in a player's hand which we don't know yet
    private Pair<Player, Integer> getUnknownCard(Player p) {
        for (int i = 0; i < 4; i++) {
            if (getKnowledgeAt(p, i) == null) {
                return new Pair<>(p, i);
            }
        }
        return null;
    }

    //finds any card in a list of players' hands which we don't know yet
    private Pair<Player, Integer> getUnknownCard(List<Player> players) {
        Player target = null;
        int minKnown = 5;

        for (Player p : players) {
            int knownCount = 0;

            for (int i = 0; i < 4; i++) {
                if (getKnowledgeAt(p, i) != null) {
                    knownCount++;
                }
            }

            //prioritise finding out about the player with the least information already known
            if (knownCount < minKnown && knownCount < 4) {
                minKnown = knownCount;
                target = p;
            }
        }

        return target != null ? getUnknownCard(target) : null;
    }

    //gets all players who are not us
    private List<Player> getOpponents() {
        return GameEngine.getInstance().getPlayers().stream()
                .filter(p -> !p.equals(this))
                .collect(Collectors.toList());
    }

    //chooses a card from a fake deck safely
    private Card getRandomCard(List<Card> simDeck) {

        //we may have an empty deck
        if (simDeck.isEmpty()) {
            return new Card(Rank.TWO, Suit.HEARTS);
        }
        return simDeck.remove(0);
    }
}