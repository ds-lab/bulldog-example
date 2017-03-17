package bulldog;

import java.io.IOException;

import io.silverspoon.bulldog.core.gpio.DigitalOutput;
import io.silverspoon.bulldog.core.io.bus.i2c.I2cBus;
import io.silverspoon.bulldog.core.io.bus.spi.SpiBus;
import io.silverspoon.bulldog.core.io.bus.spi.SpiConnection;
import io.silverspoon.bulldog.core.io.bus.spi.SpiMessage;
import io.silverspoon.bulldog.core.io.bus.spi.SpiMode;
import io.silverspoon.bulldog.core.platform.Board;
import io.silverspoon.bulldog.core.platform.Platform;
import io.silverspoon.bulldog.core.pwm.Pwm;
import io.silverspoon.bulldog.core.util.BitMagic;
import io.silverspoon.bulldog.core.util.BulldogUtil;
import io.silverspoon.bulldog.devices.pwmdriver.PCA9685;
import io.silverspoon.bulldog.raspberrypi.RaspiNames;

public class BulldogTest {
	private static final int PCA_I2C_ADDR = 0x40;

	private static final int I2C_MIN_ADDR = 0b0000_0011;
	private static final int I2C_MAX_ADDR = 0b0111_1111;

	private static void enumerateI2cBuses(Board board) {
		for (I2cBus bus : board.getI2cBuses()) {
			System.out.println("I2C bus: " + bus.getName());

			try {
				if (!bus.isOpen()) {
					System.out.println("Opening bus");
					bus.open();
				}

				// for (short addr = I2C_MIN_ADDR; addr <= I2C_MAX_ADDR; ++addr)
				// {
				// final String hexAddr = String.format("0x%02x", addr);
				// try {
				// bus.selectSlave(addr);
				// bus.readByteFromRegister(0);
				// System.out.println("Found device at address " + hexAddr);
				// } catch (Exception e) {
				// // Ignored, no device present
				// }
				// }
				final PCA9685 pwmDriver = new PCA9685(bus, PCA_I2C_ADDR);
				pwmDriver.open();
				
				// Setup MODE2 register to use direct LED connection with open-drain
				// INVRT=1, OUTDRV=0
				pwmDriver.writeByteToRegister(0x01, 0b0001_0010);
				
				System.out.println("PWM driver: " + pwmDriver.getName());
//				for (Pwm pwm : pwmDriver.getChannels()) {
//					System.out.println("PWM: " + pwm.getName());
//				}
				
				final DigitalOutput enable = board.getPin(RaspiNames.P1_13).as(DigitalOutput.class);
				enable.low();
				
				final Pwm channel = pwmDriver.getChannel(4);
				for (int i = 0; i < 10; ++i) {
					channel.setDuty(0.1 * i);
					channel.enable();
					System.out.println(channel.getName());
					Thread.sleep(250);
				}
				channel.disable();
				pwmDriver.close();
				
				enable.high();

				bus.close();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void enumerateSpi(Board board) {
		for (SpiBus bus : board.getSpiBuses()) {
			System.out.println("SPI bus: " + bus.getName());

			try {
				if (!bus.isOpen()) {
					System.out.println("Opening bus");
					bus.open();
				}

				bus.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static enum MS5611Command {
		RESET(0b0001_1110), READ_PROM_BASE(0b0101_0000),

		D1_CONV_OSR256(0b0100_0000), D1_CONV_OSR512(0b0101_0010), D1_CONV_OSR1024(0b0101_0100), D1_CONV_OSR2048(
				0b0101_0110), D1_CONV_OSR4096(0b0100_1000), D2_CONV_OSR256(0b0101_0000), D2_CONV_OSR512(
						0b0101_0010), D2_CONV_OSR1024(
								0b0101_0100), D2_CONV_OSR2048(0b0101_0110), D2_CONV_OSR4096(0b0101_1000),

		ADC_READ(0x00);

		public final byte cmd;

		MS5611Command(int cmd) {
			this.cmd = (byte) (cmd & 0xff);
		}
	}

	private static byte crc4(short data[]) {
		short rem = 0;
		for (byte cnt = 0; cnt < 16; ++cnt) {
			if (cnt % 2 == 1) {
				rem ^= (((data[cnt >> 1]) & 0x00ff));
			} else {
				rem ^= (((data[cnt >> 1]) >>> 8) & 0xffff);
			}

			for (byte bit = 8; bit > 0; --bit) {
				if ((rem & 0x8000) > 0) {
					rem = (short) (((rem << 1) ^ 0x3000) & 0xffff);
				} else {
					rem = (short) ((rem << 1) & 0xffff);
				}
			}
		}

		return (byte) ((rem >> 12) & 0xf);
	}

	private static void detectMS5611(Board board) {
		final SpiBus spi = board.getSpiBus(RaspiNames.SPI_0_CS0);
		try {
			spi.setMode(SpiMode.Mode0);
			spi.setBitsPerWord(8);
			spi.setSpeedInHz(4_000_000);
			// spi.setDelayMicroseconds(10 * 1000);
			spi.open();
			final SpiConnection spiConn = spi
					.createSpiConnection(board.getPin(RaspiNames.P1_24).as(DigitalOutput.class));

			System.out.println("RESET = " + BitMagic.toBitString(MS5611Command.RESET.cmd));

			System.out.println("Sending reset");
			spiConn.writeByte(MS5611Command.RESET.cmd);
			BulldogUtil.sleepMs(25);

			/* Read PROM */
			final short[] prom = new short[8];
			for (int promWord = 0; promWord < 8; ++promWord) {
				System.out.println("PROM word #" + promWord);
				final byte cmd = (byte) ((0b1010_0000 | promWord << 1));
				System.out.println("PROM cmd " + BitMagic.toBitString(cmd));

				final SpiMessage result = spiConn.transfer(new byte[] { cmd, 0, 0 });
				final byte[] buf = result.getReceivedBytes();
				for (byte b : buf) {
					System.out.println(" >> " + BitMagic.toBitString(b));
				}

				prom[promWord] = (short) ((((buf[1] & 0xff) << 8) | (buf[2] & 0xff)));
			}

			System.out.println("PROM contents");
			for (int promWord = 0; promWord < 8; ++promWord) {
				System.out.println(" >> " + BitMagic.toBitString(prom[promWord]));
			}
			final byte crc_read = (byte) (prom[7] & 0xf);

			prom[7] &= 0xff00;
			System.out.println(" >> " + BitMagic.toBitString(prom[7]));
			final byte crc_calc = crc4(prom);

			System.out.println("CRC read = " + crc_read);
			System.out.println("CRC calc = " + crc_calc);

			for (int i = 0; i < 10; ++i) {
				System.out.println("Request D2 conversion");
				spiConn.writeByte(MS5611Command.D2_CONV_OSR1024.cmd);
				BulldogUtil.sleepMs(10);

				System.out.println("Reading ADC");
				final SpiMessage result = spiConn.transfer(new byte[] { MS5611Command.ADC_READ.cmd, 0, 0, 0 });

				System.out.println("Received bytes");
				for (byte b : result.getReceivedBytes()) {
					System.out.println(" >> " + BitMagic.toBitString(b));
				}

				final byte[] buf = result.getReceivedBytes();
				final int reading = ((buf[1] & 0xff) << 16) | ((buf[2] & 0xff) << 8) | (buf[3] & 0xff);
				System.out.println("Raw temp reading: " + reading);

				// Get calibrated temperature
				final int dT = reading - prom[5] * (1 << 8);
				final int temp = 2000 + dT * prom[6] / (1 << 23);
				System.out.println("Converted temp: " + temp);

				BulldogUtil.sleepMs(1000);
			}
			spi.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void blinkLed(Board board) {
		// Set up a digital output for the amber LED
		// The pins are named after the physical layout
		final DigitalOutput output = board.getPin(RaspiNames.P1_18).as(DigitalOutput.class);

		// Blink the LED
		for (int i = 0; i < 10; ++i) {
			output.low();
			BulldogUtil.sleepMs(500);
			output.high();
			BulldogUtil.sleepMs(500);
		}
	}

	public static void main(String[] args) {
		// Detect the board we are running on
		final Board board = Platform.createBoard();

		System.out.println("Starting");

		//enumerateI2cBuses(board);
		// enumerateSpi(board);
		detectMS5611(board);

		// blinkLed(board);

		System.out.println("Finished");
	}

}
