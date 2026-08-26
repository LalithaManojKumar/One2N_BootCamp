package basics.GameofPig_4;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class  PigGame {

    private static final int WINNING_SCORE = 100;
    private static final int GAMES_PER_MATCH = 10;
    private static final Random random = new Random();

    record Player(int holdAt) {
    }

    record Result(int wins, int losses) {
    }

    public static void main(String[] args) {

        // Story 1
        printResult(new Player(10), new Player(15));

        // Story 2
        Player fixed = new Player(21);

        IntStream.rangeClosed(1, 100)
                .filter(k -> k != fixed.holdAt())
                .mapToObj(Player::new)
                .forEach(player -> printResult(fixed, player));

        // Story 3
        List<Player> strategies = IntStream.rangeClosed(1, 100)
                .mapToObj(Player::new)
                .toList();

        for (Player player1 : strategies) {

            int wins = 0;
            int losses = 0;

            for (Player player2 : strategies) {

                if (player1.holdAt() == player2.holdAt()) {
                    continue;
                }

                Result result = simulate(player1, player2);

                wins += result.wins();
                losses += result.losses();
            }

            int total = wins + losses;

            System.out.printf(
                    "Result: Wins, losses staying at k = %d: %d/%d (%.1f%%), %d/%d (%.1f%%)%n",
                    player1.holdAt(),
                    wins,
                    total,
                    wins * 100.0 / total,
                    losses,
                    total,
                    losses * 100.0 / total
            );
        }
    }

    private static Result simulate(Player first, Player second) {

        int wins = 0;

        for (int i = 0; i < GAMES_PER_MATCH; i++) {
            if (play(first, second)) {
                wins++;
            }
        }

        return new Result(wins, GAMES_PER_MATCH - wins);
    }

    private static boolean play(Player first, Player second) {

        int score1 = 0;
        int score2 = 0;

        while (true) {

            score1 += playTurn(first);

            if (score1 >= WINNING_SCORE) {
                return true;
            }

            score2 += playTurn(second);

            if (score2 >= WINNING_SCORE) {
                return false;
            }
        }
    }

    private static int playTurn(Player player) {

        int turnScore = 0;

        while (turnScore < player.holdAt()) {

            int roll = rollDice();

            if (roll == 1) {
                return 0;
            }

            turnScore += roll;
        }

        return turnScore;
    }

    private static int rollDice() {
        return random.nextInt(6) + 1;
    }

    private static void printResult(Player first, Player second) {

        Result result = simulate(first, second);

        System.out.printf(
                "Holding at %d vs Holding at %d: wins: %d/%d (%.1f%%), losses: %d/%d (%.1f%%)%n",
                first.holdAt(),
                second.holdAt(),
                result.wins(),
                GAMES_PER_MATCH,
                result.wins() * 100.0 / GAMES_PER_MATCH,
                result.losses(),
                GAMES_PER_MATCH,
                result.losses() * 100.0 / GAMES_PER_MATCH
        );
    }
}