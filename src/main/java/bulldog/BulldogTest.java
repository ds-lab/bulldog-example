package bulldog;

import java.io.IOException;

import io.silverspoon.bulldog.core.platform.Board;
import io.silverspoon.bulldog.core.platform.Platform;

public class BulldogTest {
	public static void main(String[] args) {
		// Detect the board we are running on
		final Board board = Platform.createBoard();

		System.out.println("Starting");

		System.out.println("Finished");
	}
}
