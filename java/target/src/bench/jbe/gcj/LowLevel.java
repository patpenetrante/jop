package jbe;


/**
*	LowLevel time and output for gcj (Linux)
*/
public class LowLevel {

	static boolean init;

	public static int timeMillis() {
    	return (int) System.currentTimeMillis();
	}

	public static int clockTicks() {

		return (int) (jbe.gcj.TSC.read()/267); // 267 is 266.6 MHz clock
	}

	public static void msg(String msg) {

		System.out.print(msg);
		System.out.print(" ");
	}

	public static void msg(int val) {

		System.out.print(val);
		System.out.print(" ");
	}

	public static void msg(String msg, int val) {

		msg(msg);
		msg(val);
	}

	public static void lf() {

		System.out.println();
	}
}
