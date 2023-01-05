using System;
using System.Threading;

namespace CSBCMiner
{

    class MiningResult
    {
        public readonly uint nonce;
        public readonly uint[] hash;

        public MiningResult(uint nonce, uint[] hash)
        {
            this.nonce = nonce;
            this.hash = hash;
        }
    }

    class Miner
    {
        private readonly BlockHeader header;

        private readonly uint[] midstate;

        private uint[] threadHashes;
        private int remainingThreads;
        private bool cancelRequested;
        private MiningResult result;

        private readonly object monitor = new object();

        public Miner(BlockHeader header)
        {
            this.header = header;
            midstate = Sha256.CreateMidstate(header.data);
        }

        public void Mine(int requestedThreads)
        {
            uint startNonce = header.Nonce;
            uint endNonce = 0xFFFFFFFF;
            ulong totalNonces = 0;
            if (endNonce >= startNonce)
                totalNonces = endNonce - startNonce + 1;

            int threads = (int)Math.Min((ulong)requestedThreads, totalNonces); // less nonces than threads, strange 
            lock (monitor)
            {
                if (IsRunning)
                    throw new InvalidOperationException("already mining");
                remainingThreads = threads;
            }

            result = null;
            cancelRequested = false;
            threadHashes = new uint[threads];

            Console.WriteLine($"Start mining {totalNonces} nonces using {threads} threads");

            ulong threadNonces = totalNonces / (ulong)threads;
            uint threadStartNonce, threadEndNonce;
            for (int i = 0; i < threads; i++)
            {
                threadStartNonce = (uint)(startNonce + (uint)i * threadNonces);
                threadEndNonce = (uint)(threadStartNonce + threadNonces - 1);
                if (i == threads - 1)
                    threadEndNonce += (uint)(totalNonces % (ulong)threads);
                new Thread(MiningTask(i, threadStartNonce, threadEndNonce)).Start();
            }
        }

        public MiningResult Await()
        {
            lock (monitor)
            {
                while (remainingThreads > 0)
                {
                    Monitor.Wait(monitor);
                }
            }
            return result;
        }

        public MiningResult Result => result;

        public bool IsRunning => remainingThreads > 0;

        public void Cancel()
        {
            cancelRequested = true;
            Await();
        }

        private ThreadStart MiningTask(int threadIndex, uint startNonce, uint endNonce)
        {
            return new ThreadStart(() => DoMine(threadIndex, startNonce, endNonce));
        }

        private void DoMine(int threadIndex, uint startNonce, uint endNonce)
        {
            uint[] hash = new uint[Sha256.H_INTS];
            uint[] workBuffer = new uint[Sha256.BUFFER_INTS];
            uint[] data = header.data;
            MiningResult result = null;
            for (uint nonce = startNonce; !IsDone && nonce < endNonce; nonce++)
            {
                Sha256.Hash(data, midstate, nonce, workBuffer, hash);
                bool matched = header.TestHash(hash);
                threadHashes[threadIndex]++;
                if (matched)
                {
                    result = new MiningResult(nonce, hash);
                    break;
                }
            }
            CompleteThread(result);
        }

        private bool IsDone => result != null || cancelRequested;

        public uint TotalHashes => SumThreadHashes();

        private uint SumThreadHashes()
        {
            if (threadHashes == null)
                return 0;
            uint total = 0;
            for (int i = 0; i < threadHashes.Length; i++)
            {
                total += threadHashes[i];
            }
            return total;
        }

        private void CompleteThread(MiningResult threadResult)
        {
            lock (monitor)
            {
                if (threadResult != null)
                {
                    if (result != null)
                        throw new InvalidOperationException($"Nonce {threadResult.nonce} refused, already matched to {result.nonce}");
                    result = threadResult;
                }
                remainingThreads--;
                Monitor.Pulse(monitor);
            }
        }

    }
}
