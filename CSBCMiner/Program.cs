using System;
using System.Threading;
using System.Collections.Generic;
using static CSBCMiner.Conversions;

namespace CSBCMiner
{
    class Program
    {
        private const string TEST_HEADER = "020000000AFFED3FC96851D8C74391C2D9333168FE62165EB228BCED7E000000000000004277B65E3BD527F0CEB5298BDB06B4AACBAE8A4A808C2C8AA414C20F252DB801130DAE516461011A00000000";
        private const string EXPECTED_HASH = "5C8AD782C007CC563F8DB735180B35DAB8C983D172B57E2C2701000000000000";
        private const uint EXPECTED_NONCE = 0xB89BEB3A;

        static void Main()
        {
            var header = new BlockHeader(HexadecimalToUInt(TEST_HEADER));
            header.Nonce = EXPECTED_NONCE - 100_000_000;

            if (false)
            {
                header.Nonce = EXPECTED_NONCE;
                Test(header);
            }
            else
            {
                var miner = new Miner(header);
                miner.Mine(Environment.ProcessorCount);
                var result = Monitor(miner);
                if (result != null)
                {
                    Console.WriteLine($"Matched : nonce = {result.nonce}, hash= {UIntToHexadecimal(result.hash)}");
                    if (result.nonce != EXPECTED_NONCE)
                        throw new InvalidOperationException("Invalid nonce " + result.nonce);
                }
                else
                {
                    Console.WriteLine("not matched");
                }
            }
        }

        static MiningResult? Monitor(Miner miner)
        {
            var startTime = DateTime.UtcNow;
            while (miner.IsRunning)
            {
                Thread.Sleep(1000);
                var hashes = miner.TotalHashes;
                var elapsed = DateTime.UtcNow - startTime;
                Console.WriteLine($"total={hashes,-10:N0} HPS={hashes / elapsed.TotalSeconds:N0}");
            }
            if (miner.Result != null)
            {
                var hashes = miner.TotalHashes;
                var elapsed = DateTime.UtcNow - startTime;
                Console.WriteLine($"Nonce found after {hashes,-10:N0} hashes in {elapsed} ({hashes / elapsed.TotalSeconds:N0}H/s)");
            }
            return miner.Result;
        }

        static void Test(BlockHeader header)
        {
            header.Nonce = EXPECTED_NONCE;
            var miner = new Miner(header);
            miner.Mine(1);
            MiningResult result = miner.Await();
            if (result != null)
            {
                var errors = new List<string>();
                if (result.nonce != EXPECTED_NONCE)
                    errors.Add("Invalid nonce " + result.nonce);
                var hash = UIntToHexadecimal(result.hash);
                if (hash != EXPECTED_HASH)
                    errors.Add("Invalid nonce " + hash);
                if (errors.Count == 0)
                    Console.WriteLine("Success");
                else
                    Console.WriteLine(string.Join("\n", errors));
            }
            else
            {
                Console.WriteLine("No matched");
            }
        }
    }
}