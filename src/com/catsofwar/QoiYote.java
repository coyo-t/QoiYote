package com.catsofwar;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.nio.ByteOrder.BIG_ENDIAN;

public class QoiYote
{
	public static final int CHUNK_INDEX = 0b00_000000;
	public static final int CHUNK_DIFF  = 0b01_000000;
	public static final int CHUNK_LUMA  = 0b10_000000;
	public static final int CHUNK_RLE   = 0b11_000000;
	public static final int CHUNK_RGB   = 0b11_111110;
	public static final int CHUNK_RGBA  = 0b11_111111;
	public static final int CHUNK_DATA_MASK = 0b11_000000;
	
	static int lastChannelCount = -1;
	static int lastSpace = -1;
	
	/**
	 * Returns the channel count of the last QOI image decoded with QoiYote.decode,
	 * or -1 if no images have been decoded yet
	 */
	public static int getLastChannelCount ()
	{
		return lastChannelCount;
	}
	
	/**
	 * Returns the Colur Space of the last QOI image decoded with QoiYote.decode,
	 * or -1 if no images have been decoded yet
	 */
	public static int getLastSpace ()
	{
		return lastSpace;
	}

	public static BufferedImage decode (byte[] sourceQoiData)
	{
		return decode(ByteBuffer.wrap(sourceQoiData));
	}

	public static BufferedImage decode (ByteBuffer sourceQoiData)
	{
		final var data = sourceQoiData.slice().order(BIG_ENDIAN);
		
		// check and read header magic
		{
			final var magic1 = data.get() & 0xFF;
			final var magic2 = data.get() & 0xFF;
			final var magic3 = data.get() & 0xFF;
			final var magic4 = data.get() & 0xFF;
			if (magic1 != 'q' || magic2 != 'o' || magic3 != 'i' || magic4 != 'f')
			{
				throw new RuntimeException(
					"Bogus qoi magic! expected 'qoif', got %02X, %02X, %02X, %02X!".formatted(magic1, magic2, magic3, magic4)
				);
			}
		}
		
		final var wide = data.getInt();
		final var tall = data.getInt();
		
		if (wide < 0 || tall < 0)
		{
			throw new RuntimeException("Bogus image dimensions of %d x %d".formatted(wide, tall));
		}
		
		final var count = wide * tall;
		
		lastChannelCount = data.get() & 0xFF;
		lastSpace = data.get() & 0xFF;
		
		try (var arena = Arena.ofConfined())
		{
			int rCur = 0;
			int gCur = 0;
			int bCur = 0;
			int aCur = 255;
			final var seen = arena.allocate(64 << 2);
			final var pixels = arena.allocate(JAVA_INT, count);
			
			seen.fill((byte)0);
			
			for (int cursor = 0; cursor < count;)
			{
				// Set Table
				final var tAddr = (long)((rCur*3+gCur*5+bCur*7+aCur*11)&63) << 2;
				seen.set(JAVA_BYTE, tAddr,   (byte)rCur);
				seen.set(JAVA_BYTE, tAddr+1, (byte)gCur);
				seen.set(JAVA_BYTE, tAddr+2, (byte)bCur);
				seen.set(JAVA_BYTE, tAddr+3, (byte)aCur);
				
				// Handle Chunks
				final var chunk = data.get() & 0xFF;
				if (chunk == CHUNK_RGB)
				{
					rCur = data.get() & 0xFF;
					gCur = data.get() & 0xFF;
					bCur = data.get() & 0xFF;
				}
				else if (chunk == CHUNK_RGBA)
				{
					rCur = data.get() & 0xFF;
					gCur = data.get() & 0xFF;
					bCur = data.get() & 0xFF;
					aCur = data.get() & 0xFF;
				}
				else
				{
					final var tt = chunk & CHUNK_DATA_MASK;
					
					if (tt == CHUNK_INDEX)
					{
						final var addr = (long)chunk << 2;
						rCur = seen.get(JAVA_BYTE, addr) & 0xFF;
						gCur = seen.get(JAVA_BYTE, addr + 1) & 0xFF;
						bCur = seen.get(JAVA_BYTE, addr + 2) & 0xFF;
						aCur = seen.get(JAVA_BYTE, addr + 3) & 0xFF;
					}
					else if (tt == CHUNK_DIFF)
					{
						rCur = (rCur + (((chunk >> 4) & 3) - 2)) & 0xFF;
						gCur = (gCur + (((chunk >> 2) & 3) - 2)) & 0xFF;
						bCur = (bCur + (( chunk       & 3) - 2)) & 0xFF;
					}
					else if (tt == CHUNK_LUMA)
					{
						final var  l = data.get() & 0xFF;
						final var vg = (chunk & 0x3F) - 32;
						rCur = (rCur + (vg - 8 + ((l >> 4) & 15))) & 0xFF;
						gCur = (gCur + vg) & 0xFF;
						bCur = (bCur + (vg - 8 + (l & 15))) & 0xFF;
					}
					else if (tt == CHUNK_RLE)
					{
						final var run = (chunk & 0x3F) + 1;
						final var stop = cursor + run;
						// ARGB rather than RGBA, because thats how the awt image do
						final var pixel = (aCur<<24)|(rCur<<16)|(gCur<<8)|bCur;
						for (; cursor < stop; cursor++)
						{
							pixels.set(JAVA_INT, (long)cursor << 2, pixel);
						}
						continue;
					}
				}
				final var pixel = (aCur<<24)|(rCur<<16)|(gCur<<8)|bCur;
				pixels.set(JAVA_INT, (long)(cursor++) << 2, pixel);
			}
			for (var i = 7; --i >= 0;)
			{
				if (data.get() != 0)
				{
					throw new RuntimeException("Bogus QOI zero ender @"+(data.position()-1));
				}
			}
			if (data.get() != 1)
			{
				throw new RuntimeException("Bogus QOI one ender @"+(data.position()-1));
			}
			final var outs = new BufferedImage(wide, tall, BufferedImage.TYPE_4BYTE_ABGR);
			outs.setRGB(0, 0, wide, tall, pixels.toArray(JAVA_INT), 0, wide);
			return outs;
		}
	
	}
}
