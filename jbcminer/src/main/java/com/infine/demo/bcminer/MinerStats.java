package com.infine.demo.bcminer;

public class MinerStats {
    protected long startTime;
    protected long totalHashes;
    protected double totalTime;

    public long totalHashes() {
        return totalHashes;
    }

    public double totalTime() {
        return totalTime;
    }

    /**
     * milion H per sec
     */
    public double mhps() {
        if (totalTime > 0)
            return (totalHashes * 1E-6) / totalTime;
        return 0;
    }

    public void start() {
        totalHashes = 0;
        startTime = System.currentTimeMillis();
    }

    public synchronized MinerStats update(int newHashes) {
        totalHashes += newHashes;
        totalTime = (System.currentTimeMillis() - startTime) * 1E-3;
        return this;
    }

    @Override
    public synchronized String toString() {
        return String.format("Hashed %-5.0f million in %-6.2f secs (%.1f million hash/s)",
                totalHashes * 1E-6, totalTime, this.mhps());
    }

}
