package io.github.soldierdonkey2.winterheart.api;

import java.util.ArrayList;
import java.util.List;

public final class Numbers {
    private static final String[] ONES = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
    private static final String[] TEENS = {"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] THOUSANDS = {"", "thousand", "million"};

    private Numbers() {}

    /** A number as English text with "and" over 100: 107 -> "one hundred and seven". Supports up to 999,999,999. */
    public static String toText(long num) {
        if (num == 0) return "zero";
        long whole = num;
        List<String> words = new ArrayList<>();
        int chunkIndex = 0;
        while (num > 0) {
            int chunk = (int) (num % 1000);
            if (chunk != 0) {
                String text = chunkText(chunk, chunkIndex == 0, whole);
                String suffix = chunkIndex < THOUSANDS.length ? THOUSANDS[chunkIndex] : "";
                words.add(0, suffix.isEmpty() ? text : text + " " + suffix);
            }
            num /= 1000;
            chunkIndex++;
        }
        return String.join(" ", words).trim();
    }

    private static String chunkText(int n, boolean lowest, long whole) {
        List<String> w = new ArrayList<>();
        int hundred = n / 100, rem = n % 100;
        if (hundred > 0) { w.add(ONES[hundred]); w.add("hundred"); }
        if (rem > 0) {
            if (hundred > 0 || (lowest && whole > 100)) w.add("and");
            if (rem < 10) w.add(ONES[rem]);
            else if (rem < 20) w.add(TEENS[rem - 10]);
            else w.add(rem % 10 > 0 ? TENS[rem / 10] + "-" + ONES[rem % 10] : TENS[rem / 10]);
        }
        return String.join(" ", w);
    }

    /** "Fall three times, stand up four." */
    public static String ominousMessage(int deathCount) {
        return "Fall " + toText(deathCount) + (deathCount == 1 ? " time" : " times") + ", stand up " + toText(deathCount + 1L) + ".";
    }
}
