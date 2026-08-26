package basics.NumberFiltering_2;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NumberFilter {

    public static boolean isEven(int n) {
        return n % 2 == 0;
    }

    public static boolean isOdd(int n) {
        return n % 2 != 0;
    }

    public static boolean isPrime(int n) {

        if (n < 2) {
            return false;
        }

        return IntStream.rangeClosed(2, (int) Math.sqrt(n))
                .noneMatch(i -> n % i == 0);
    }

    public static Predicate<Integer> greaterThan(int value) {
        return n -> n > value;
    }

    public static Predicate<Integer> lessThan(int value) {
        return n -> n < value;
    }

    public static Predicate<Integer> multipleOf(int value) {
        return n -> n % value == 0;
    }

    public static List<Integer> filter(List<Integer> numbers, Predicate<Integer> condition) {

        return numbers.stream()
                .filter(condition)
                .collect(Collectors.toList());
    }


    public static List<Integer> all(List<Integer> numbers, Predicate<Integer>... conditions) {

        Predicate<Integer> predicate = Stream.of(conditions)
                        .reduce(x -> true, Predicate::and);

        return filter(numbers, predicate);
    }


    public static List<Integer> any(List<Integer> numbers, Predicate<Integer>... conditions) {

        Predicate<Integer> predicate = Stream.of(conditions)
                        .reduce(x -> false, Predicate::or);

        return filter(numbers, predicate);
    }

    public static void main(String[] args) {

        List<Integer> numbers = IntStream.rangeClosed(1, 20)
                        .boxed()
                        .toList();

        System.out.println("\nStory 1 : \n" + filter(numbers, NumberFilter::isEven));

        System.out.println("\nStory 2 : \n" + filter(numbers, NumberFilter::isOdd));

        System.out.println("\nStory 3 : \n" + filter(numbers, NumberFilter::isPrime));

        System.out.println("\nStory 4 : \n" +  all(
                    numbers,
                    NumberFilter::isOdd,
                    NumberFilter::isPrime));

        System.out.println("\nStory 5 : \n" +  all(
                    numbers,
                    NumberFilter::isEven,
                    multipleOf(5) ) );

        System.out.println("\nStory 6 : \n" +  all(
                        numbers,
                        NumberFilter::isOdd,
                        multipleOf(3),
                        greaterThan(10) ) );

        System.out.println("\nStory 7 : \n" +   all(
                        numbers,
                        NumberFilter::isOdd,
                        greaterThan(5),
                        multipleOf(3) ) );

        System.out.println("\nStory 8 : \n" +  any(
                        numbers,
                        NumberFilter::isPrime,
                        greaterThan(15),
                        multipleOf(5) ) );

        System.out.println("\nStory 1 : \n" +  any(
                        numbers,
                        lessThan(6),
                        multipleOf(3) ) );
    }
}