package com.infine.demo.bcminer;

import com.infine.demo.bcminer.java.Sha256;

import java.util.function.Predicate;

/**
 * Predicate to test a hash.
 * Use an int bit mask  for the first int in the h and check for 0 for other int from the start offset.
 *
 * @param hOffset
 * @param mask
 */
public record HashPredicate(int hOffset, int mask) implements Predicate<int[]> {
    @Override
    public boolean test(int[] hash) {
        int sum = hash[hOffset] & mask;
        for (int i = hOffset + 1; i < Sha256.H_INTS; i++) {
            sum |= hash[i];
        }
        return sum == 0;
    }
}
