using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace CSBCMiner
{
    public class Conversions
    {
        /// <summary>Prevent the compiler from making an unneeded default public constructor.</summary>
        private Conversions() { }

        public static string UIntToHexadecimal(uint[] array)
        {
            return ByteToHexadecimal(ToByte(array, array.Length));
        }

        public static uint[] HexadecimalToUInt(string data)
        {
            return ToUInt(HexadecimalToByte(data));
        }

        public static string ByteToHexadecimal(byte[] array)
        {
            StringBuilder temp = new StringBuilder(2 * array.Length);
            for (int i = 0; i < array.Length; i++)
            {
                temp.Append(array[i].ToString("X2", System.Globalization.CultureInfo.InvariantCulture));
            }

            return temp.ToString();
        }

        public static byte[] HexadecimalToByte(string data)
        {
            byte[] temp = new byte[data.Length / 2];

            for (int i = 0; i < temp.Length; i++)
            {
                temp[i] = byte.Parse(data.Substring((i * 2), 2), System.Globalization.NumberStyles.HexNumber, System.Globalization.CultureInfo.InvariantCulture);
            }

            return temp;
        }

        public static byte[] ToByte(uint[] source, int intCount)
        {
            byte[] target = new byte[intCount * 4];
            ToByte(source, target, intCount);
            return target;
        }

        public static void ToByte(uint[] source, byte[] target, int intCount)
        {
            for (int i = 0; i < intCount; i++)
            {
                ToByte(source[i], target, i * 4);
            }
        }

        public static void ToByte(uint source, byte[] target, int offset)
        {
            target[offset++] = (byte)(source >> 24);
            target[offset++] = (byte)(source >> 16);
            target[offset++] = (byte)(source >> 8);
            target[offset] = (byte)source;
        }

        public static uint[] ToUInt(byte[] source)
        {
            uint[] target = new uint[source.Length / 4];
            ToUInt(source, target);
            return target;
        }

        public static void ToUInt(byte[] source, uint[] target)
        {
            for (int i = 0; i < target.Length; i++)
            {
                int soffset = i * 4;
                target[i] = (((uint)source[soffset++] & 0xFF) << 24) |
                            (((uint)source[soffset++] & 0xFF) << 16) |
                            (((uint)source[soffset++] & 0xFF) << 8) |
                            ((uint)source[soffset] & 0xFF);
            }
        }
    }
}
