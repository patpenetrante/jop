package com.jopdesign.tools;

/**
	Assemler for JOP3

	Author: Martin Schoeberl	martin@good-ear.com


	instruction coding:


revision:

	2001-09-22	creation
	2001-10-24	working version
	2001-12-08	intruction set change (16->8 bit)

*/

import java.io.*;
import java.util.*;


public class Jopa {

	private String fname;
	static final int ADDRBITS = 10;
	static final int DATABITS = 10;
	static final int BRBITS = 10;
	static final int OPDBITS = 5;
	static final int CONST_ADDR = 32;
	private String srcDir;
	private String dstDir;

	public Jopa(String fn) {
		srcDir = System.getProperty("user.dir");
		dstDir = System.getProperty("user.dir");
		fname = fn;
		if (!srcDir.endsWith(File.separator))
			srcDir += File.separator;
		if (!dstDir.endsWith(File.separator))
			dstDir += File.separator;
	}

	public Jopa(String[] clist) {
		srcDir = System.getProperty("user.dir");
		dstDir = System.getProperty("user.dir");
		processOptions(clist);
		if (!srcDir.endsWith(File.separator))
			srcDir += File.separator;
		if (!dstDir.endsWith(File.separator))
			dstDir += File.separator;
	}

	void error(StreamTokenizer in, String s) {
		System.out.println((in.lineno()-1)+" error: "+s);
	}

	private StreamTokenizer getSt() {

		try {
			FileReader fileIn = new FileReader(srcDir + fname);
			StreamTokenizer in = new StreamTokenizer(fileIn);

			in.wordChars( '_', '_' );
			in.wordChars( ':', ':' );
			in.eolIsSignificant(true);
			in.slashStarComments(true);
			in.slashSlashComments(true);
			in.lowerCaseMode(true);
			return in;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
			return null;
		}
	}

	private class Line {
		int jinstr;
		String label;
		Instruction instr;
		int special;
		int intVal;
		String symVal;
		boolean nxt;
		boolean opd;
	}

	private Line getLine(StreamTokenizer in) {

		Line l = new Line();
		l.jinstr = -1;

		try {
			for (int cnt=0; in.nextToken()!=StreamTokenizer.TT_EOL; ++cnt) {

				if (in.ttype == StreamTokenizer.TT_WORD) {

					int pos = in.sval.indexOf(":");
					if (pos!=-1) {
						String s = in.sval.substring(0, pos);
						l.jinstr = JopInstr.get(s);
						if (l.jinstr==-1) {
							l.label = s;
						}
					} else {
						if (in.sval.equals("nxt")) {
							l.nxt = true;
						} else if (in.sval.equals("opd")) {
							l.opd = true;
						} else {
							Instruction i = Instruction.get(in.sval);
							if (i==null) {
								l.symVal = in.sval;
							} else if (l.instr==null) {
								l.instr = i;
							}
						}
					}

				} else if (in.ttype == StreamTokenizer.TT_NUMBER) {

					l.intVal = (int) in.nval;

				} else if (in.ttype == '=') {
					l.special = in.ttype;
				} else if (in.ttype == '?') {
					l.special = in.ttype;
				} else {
					error(in, "'"+(char) in.ttype+"' syntax");
				}
			} // EOL
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}

		return l;
	}


	static String bin(int val, int bits) {

		String s = "";
		for (int i=0; i<bits; ++i) {
			s += (val & (1<<(bits-i-1))) != 0 ? "1" : "0";
		}
		return s;
	}

	private Map symMap  = new HashMap();
	private int memcnt = 0;
	private List varList = new LinkedList();

/**
*	first pass.
*	get values for all symbols
*/
	public void pass1() {

		StreamTokenizer in = getSt();
		int pc = 0;

		try {
			while (in.nextToken() != StreamTokenizer.TT_EOF) {
				in.pushBack();

				Line l = getLine(in);
//System.out.println(l.jinstr+" "+l.label+" "+l.instr+" '"+(char) l.special+"' "+l.intVal+" "+l.symVal);

				if (l.jinstr==-1) {
					if (l.label!=null) {
						if (symMap.containsKey(l.label)) {
							error(in, "symbol "+l.label+" allready defined");
						} else {
							symMap.put(l.label, new Integer(pc));
						}
					}

					if (l.special=='=') {
						if (l.symVal==null) {
							error(in, "missing symbol for '='");
						} else {
							if (symMap.containsKey(l.symVal)) {
								error(in, "symbol "+l.symVal+" allready defined");
							} else {
								symMap.put(l.symVal, new Integer(l.intVal));
							}
						}
					} else if (l.special=='?') {
						if (symMap.containsKey(l.symVal)) {
							error(in, "symbol "+l.symVal+" allready defined");
						} else {
							symMap.put(l.symVal, new Integer(memcnt++));
							varList.add(l.symVal);
						}
					}

				}
				if (l.instr!=null) {
					++pc;
				}

			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
//System.out.println(symMap);
	}

	static String hex(int i, int len) {

		String s = Integer.toHexString(i);
		int cnt = len-s.length();
		for (int k=0; k<cnt; ++k) s = "0"+s;
		return s;
	}


	private Map constMap = new HashMap();
	private List constList = new LinkedList();
	private Map offMap = new HashMap();
	private List offList = new LinkedList();

	private int[] romData = new int[1<<ADDRBITS];
	private int romLen = 0;

/**
*	second pass.
*	generate code and write rom.mif and ram.mif.
*/
	public void pass2() {

		StreamTokenizer in = getSt();
		int pc = 0;
		int ji_cnt = 0;


		try {
			FileWriter rom = new FileWriter(dstDir + "rom.mif");
			FileWriter jtbl = new FileWriter(dstDir + "jtbl.vhd");
			FileWriter bcfetbl = new FileWriter(dstDir + "bcfetbl.vhd");

			BufferedReader inraw = new BufferedReader(new FileReader(srcDir + fname));

			String line;
//
//	print rom.mif head
//
			line = "--\n";
			line += "--\trom.mif\n";
			line += "--\n";
			line += "depth = 1024;\n";
			line += "width = "+DATABITS+";\n";
			line += "\n";
			line += "content\n";
			line += "\n";
			line += "begin\n";
			line += "\n";
			line += "\t[0..1ff] : 080;	-- nop\n\n";

			rom.write( line );

//
//	print jtbl.vhd head
//
			line = "--\n";
			line += "--\tjtbl.vhd\n";
			line += "--\n";
			line += "--\tjump table for java bc to jvm address\n";
			line += "--\n";
			line += "--\t\tDONT edit this file!\n";
			line += "--\t\tgenerated by Jopa.java\n";
			line += "--\n";
			line += "\n";
			line += "library ieee;\n";
			line += "use ieee.std_logic_1164.all;\n";
			line += "use ieee.std_logic_arith.all;\n";
			line += "use ieee.std_logic_unsigned.all;\n";
			line += "\n";
			line += "entity jtbl is\n";
			line += "port (\n";
			line += "\tbcode\t: in std_logic_vector(7 downto 0);\n";
			line += "\tq\t\t: out std_logic_vector("+(ADDRBITS-1)+" downto 0)\n";
			line += ");\n";
			line += "end jtbl;\n";
			line += "\n";
			line += "--\n";
			line += "--\tunregistered rdbcode\n";
			line += "--\tunregistered dout\n";
			line += "--\n";
			line += "architecture rtl of jtbl is\n";
			line += "\n";
			line += "begin\n";
			line += "\n";
			line += "process(bcode) begin\n";
			line += "\n";
			line += "\tcase bcode is\n";
			line += "\n";

			jtbl.write( line );

//
//	print bcfetbl.vhd head
//
			line = "--\n";
			line += "--\tbcfetbl.vhd\n";
			line += "--\n";
			line += "--\tnext bc or bc operand read for bcfetch.\n";
			line += "--\n";
			line += "--\t\tDONT edit this file!\n";
			line += "--\t\tgenerated by Jopa.java\n";
			line += "--\n";
			line += "\n";
			line += "library ieee;\n";
			line += "use ieee.std_logic_1164.all;\n";
			line += "use ieee.std_logic_arith.all;\n";
			line += "use ieee.std_logic_unsigned.all;\n";
			line += "\n";
			line += "entity bcfetbl is\n";
			line += "port (\n";
			line += "\taddr\t\t: in std_logic_vector("+(ADDRBITS-1)+" downto 0);\n";
			line += "\tnxt, opd\t: out std_logic\n";
			line += ");\n";
			line += "end bcfetbl;\n";
			line += "\n";
			line += "architecture rtl of bcfetbl is\n";
			line += "\n";
			line += "begin\n";
			line += "\n";
			line += "process(addr) begin\n";
			line += "\n";
			line += "\tcase addr is\n";
			line += "\n";

			bcfetbl.write( line );

			int noim_address = 0;

			while (in.nextToken() != StreamTokenizer.TT_EOF) {
				in.pushBack();

				Line l = getLine(in);

				if (l.jinstr!=-1) {
					++ji_cnt;
					jtbl.write("\t\twhen \""+bin(l.jinstr, 8) +
						"\" => q <= \""+bin(pc, ADDRBITS)+"\";" +
						"\t--\t"+hex(pc,4)+"\t"+JopInstr.name(l.jinstr)+"\n");
					if (JopInstr.name(l.jinstr).equals("sys_noim")) {
						noim_address = pc;
					}
				}

				line = "\t";
				if (l.instr==null) {
					line += "            ";
				} else {

//
//	do the assembling
//
					int opcode = l.instr.opcode;

					if (l.nxt || l.opd) {
						bcfetbl.write("\t\twhen \""+bin(pc, ADDRBITS) + "\" => ");
						bcfetbl.write("nxt <= '"+(l.nxt ? 1 : 0)+"'; ");
						bcfetbl.write("opd <= '"+(l.opd ? 1 : 0)+"';\n");
					}

					if (l.instr.hasOpd) {
						int opVal = 0;
						if (l.symVal!=null) {
							Integer i = (Integer) symMap.get(l.symVal);
							if (i==null) {
								error(in, "Symbol "+l.symVal+" not defined");
							} else {
								opVal = i.intValue();
							}
						} else {
							opVal = l.intVal;
						}

						if (l.instr.name.equals("ldi")) {
							Integer i = new Integer(opVal);
							Integer addr;
							if (constMap.containsKey(i)) {
								addr = (Integer) constMap.get(i);
							} else {
								addr = new Integer(constMap.size());
								constMap.put(i, addr);
								constList.add(i);
							}
							opVal = addr.intValue();
						}

						if (l.instr.isJmp) {						// List of branch offsets
							Integer off = new Integer(opVal-pc-1);
							if (off.intValue()< -512 || off.intValue()>511) {
								error(in, "offset "+off+" wrong range");
							}
							Integer addr;
							if (offMap.containsKey(off)) {
								addr = (Integer) offMap.get(off);
							} else {
								addr = new Integer(offMap.size());
								offMap.put(off, addr);
								offList.add(off);
							}
							opVal = addr.intValue();
						}

						if (opVal>31 || opVal<0) {
							error(in, "operand wrong: "+opVal);
						}
						opcode |= opVal & 0x1f;		// use 5 bit operand
					}

					if (l.nxt) opcode |= 0x200;
					if (l.opd) opcode |= 0x100;
					romData[romLen] = opcode;
					++romLen;
					line += hex(pc, 4)+" : "+hex(opcode, 3)+";\t";

					++pc;
				}


				line += "\t--\t"+inraw.readLine()+"\n";
				rom.write( line );
// System.out.print(line);



			}

			rom.write( "\nend;\n" );
			rom.close();

			line = "\n";
//			line += "\t\twhen others => q <= (others => '0');\t--\tinit on umimplemented bc\n";
			line += "\t\twhen others => q <= \""+bin(noim_address, ADDRBITS)+"\";\n";
			line += "\tend case;\n";
			line += "end process;\n";
			line += "\n";
			line += "end rtl;\n";

			jtbl.write(line);
			jtbl.close();

			line = "\n";
			line += "\t\twhen others => nxt <= '0'; opd <= '0'; \n";
			line += "\tend case;\n";
			line += "end process;\n";
			line += "\n";
			line += "end rtl;\n";

			bcfetbl.write(line);
			bcfetbl.close();

//
//	print ROM as generic VHDL file
//
			FileWriter romvhd = new FileWriter(dstDir + "rom.vhd");

			line = "--\n";
			line += "--\trom.vhd\n";
			line += "--\n";
			line += "--\tgeneric VHDL version of ROM\n";
			line += "--\n";
			line += "--\t\tDONT edit this file!\n";
			line += "--\t\tgenerated by Jopa.java\n";
			line += "--\n";
			line += "\n";
			line += "library ieee;\n";
			line += "use ieee.std_logic_1164.all;\n";
			line += "use ieee.std_logic_arith.all;\n";
			line += "use ieee.std_logic_unsigned.all;\n";
			line += "\n";
			line += "entity rom is\n";
			line += "generic (width : integer; addr_width : integer);\t-- for compatibility\n";
			line += "port (\n";
			line += "\tclk\t\t\t: in std_logic;\n";
			line += "\taddress\t\t: in std_logic_vector("+(ADDRBITS-1)+" downto 0);\n";
			line += "\tq\t\t\t: out std_logic_vector("+(DATABITS-1)+" downto 0)\n";
			line += ");\n";
			line += "end rom;\n";
			line += "\n";
			line += "architecture rtl of rom is\n";
			line += "\n";
			line += "\tsignal areg\t\t: std_logic_vector("+(ADDRBITS-1)+" downto 0);\n";
			line += "\tsignal data\t\t: std_logic_vector("+(DATABITS-1)+" downto 0);\n";
			line += "\n";
			line += "begin\n";
			line += "\n";
			line += "process(clk) begin\n";
			line += "\n";
//			line += "\tif falling_edge(clk) then\n";
//			line += "\t\tareg <= address;\n";
//			line += "\tend if;\n";
			line += "\tif rising_edge(clk) then\n";
//			line += "\t\tq <= data;\n";
	line += "\t\tareg <= address;\n";
			line += "\tend if;\n";
			line += "\n";
			line += "end process;\n";
			line += "\n";
	line += "\tq <= data;\n";
			line += "\n";
			line += "process(areg) begin\n";
			line += "\n";
			line += "\tcase areg is\n";
			line += "\n";

			romvhd.write(line);

			for (int i=0; i<romLen; ++i) {
				romvhd.write("\t\twhen \""+bin(i, ADDRBITS) +
					"\" => data <= \""+bin(romData[i], DATABITS)+"\";");
				romvhd.write("\t-- "+"TODO: comment"+"\n");
			}


			line = "\n";
			line += "\t\twhen others => data <= \""+bin(0, DATABITS)+"\";\n";
			line += "\tend case;\n";
			line += "end process;\n";
			line += "\n";
			line += "end rtl;\n";

			romvhd.write(line);
			romvhd.close();

//
//	print table of branch offsets
//
			FileWriter offtbl = new FileWriter(dstDir + "offtbl.vhd");

			line = "--\n";
			line += "--\tofftbl.vhd\n";
			line += "--\n";
			line += "--\tnext bc or bc operand read for offtch.\n";
			line += "--\n";
			line += "--\t\tDONT edit this file!\n";
			line += "--\t\tgenerated by Jopa.java\n";
			line += "--\n";
			line += "\n";
			line += "library ieee;\n";
			line += "use ieee.std_logic_1164.all;\n";
			line += "use ieee.std_logic_arith.all;\n";
			line += "use ieee.std_logic_unsigned.all;\n";
			line += "\n";
			line += "entity offtbl is\n";
			line += "port (\n";
			line += "\tidx\t\t: in std_logic_vector("+(OPDBITS-1)+" downto 0);\n";
			line += "\tq\t\t: out std_logic_vector("+(BRBITS-1)+" downto 0)\n";
			line += ");\n";
			line += "end offtbl;\n";
			line += "\n";
			line += "architecture rtl of offtbl is\n";
			line += "\n";
			line += "begin\n";
			line += "\n";
			line += "process(idx) begin\n";
			line += "\n";
			line += "\tcase idx is\n";
			line += "\n";

			offtbl.write( line );

			for (int i=0; i<offList.size(); ++i) {
				Integer val = (Integer) offList.get(i);
				offtbl.write("\t\twhen \""+bin(i, OPDBITS) +
					"\" => q <= \""+bin(val.intValue(), BRBITS)+"\";");
				offtbl.write("\t-- "+val.intValue()+"\n");
			}

			line = "\n";
			line += "\t\twhen others => q <= \""+bin(0, BRBITS)+"\";\n";
			line += "\tend case;\n";
			line += "end process;\n";
			line += "\n";
			line += "end rtl;\n";

			offtbl.write( line );
			offtbl.close();


//
//	print symbol table
//
			FileWriter ram = new FileWriter(dstDir + "ram.mif");

			line = "--\n";
			line += "--\tram.mif\n";
			line += "--\n";
			line += "depth = 256;\n";
			line += "width = 32;\n";
			line += "\n";
			line += "content\n";
			line += "\n";
			line += "begin\n";
//			line += "\t[0..ff] : 00000000;\n";
			line += "\t[0..ff] : 12345678;\n";
			line += "\n";
			ram.write( line );

			line = "--\n";
			line += "-- "+memcnt+" vars\n";
			line += "--\n\n";
			ram.write( line );

			for (int i=0; i<varList.size(); ++i) {
				String s = (String) varList.get(i);
				line = "\t";
				line += hex(i, 4) + " : " ;
				line += hex(0, 8) + ";\t--\t";
				line += s + "\n";
				ram.write( line );
			}

			line = "--\n";
			line += "-- "+constMap.size()+" consts\n";
			line += "--\n\n";
			ram.write( line );
			for (int i=0; i<constList.size(); ++i) {
				Integer val = (Integer) constList.get(i);
				line = "\t";
				line += hex(CONST_ADDR+i, 4) + " : " ;
				line += hex(val.intValue(), 8) + ";\t--\t";
				line += val + "\n";
				ram.write( line );
			}

			ram.write( "\nend;\n" );
			ram.close();

			System.out.println(ji_cnt+" Instructions implemented");

		} catch (IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
	}

	private boolean processOptions(String clist[]) {
		boolean success = true;

		for (int i = 0; i < clist.length; i++) {
			if (clist[i].equals("-s")) {
				srcDir = clist[++i];
			} else if (clist[i].equals("-d")) {
				dstDir = clist[++i];
			} else {
				fname = clist[i];
			}
		}

		return success;
	}

/**
*	Main for Jop assembler.
*/
	public static void main(String args[]) {

		if (args.length < 1) {
			System.out.println(
				"usage: java Jopa [-s srcDir] [-d dstDir] filename");
			System.exit(-1);
		}

		Jopa j = new Jopa(args);
		j.pass1();
		j.pass2();
	}
}
