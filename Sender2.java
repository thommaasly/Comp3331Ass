import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;

import java.nio.ByteBuffer;


public class Sender {
	//number of bits in the header
	//consists of sourceport# (16b) destport# (16b) seq# (32b) ack# (32b) flags(4b) window size (16b) checksum (16b)
	private static final int STP_HEADER_SIZE = 21;	
	//flags for STP header
	private static final byte SYN_FLAG = 0x8;
	private static final byte ACK_FLAG = 0x4;
	private static final byte RST_FLAG = 0x2;
	private static final byte FIN_FLAG = 0x1;
	private static final byte NUL_FLAG = 0x0;

	private static final int PDRP = 1;
	private static final int PDUP = 2;
	private static final int PCOR = 3;
	private static final int PORD = 4;
	private static final int PDEL = 5;
	
	//variables used for reordering, made global
	private static int hasReOrder = 0;
	private static DatagramPacket reOrder;
	private static int waited = 0;

	//determines if a timeout has occurred
	private static int retransmitted = 0;


	public static void main(String[] args) throws Exception
{
			//receives 14 arguments in the format:
			//java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop
			//pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed
			if (args.length != 14) {
				System.out.println("Incorrect number of arguments, expected: java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed");
			}
			//store the arguments in appropriately named variables

			//IP address of the host machine of the receiver
			InetAddress receiver_host_ip = InetAddress.getByName(args[0]);
			//Receiver port number
			int destPort = Integer.parseInt(args[1]);
			//name of the pdf file to be tranferred
			File f = new File(args[2]); //may need to check if this one works
			//Maximum window size in bytes
			int MWS = Integer.parseInt(args[3]);
			//Maximum Segment Size in bytes (amount of data in each segment), does not count STP header
			int MSS = Integer.parseInt(args[4]);
			//Used for calculation of timeout value
			int gamma = Integer.parseInt(args[5]);

			//PLD module variables
			//probability that segment is dropped. range(0,1)
			float pDrop = Float.parseFloat(args[6]);
			//probability that segment is duplicated. range(0,1)
			float pDup = Float.parseFloat(args[7]);
			//probability that segment is corrupted. range(0,1)
			float pCorr = Float.parseFloat(args[8]);
			//probability that segment is out of order. range(0,1)
			float pOrder = Float.parseFloat(args[9]);
			//the maximum number of packets to be held back range(1,6)
			int maxOrder = Integer.parseInt(args[10]);
			//probability that segment is delayed. range(0,1)
			float pDelay = Float.parseFloat(args[11]);
			//maximum delay in milliseconds
			int maxDelay = Integer.parseInt(args[12]);
			//seed for random number generator
			int seed = Integer.parseInt(args[13]);
		
		
			//generate seed
			Random random = new Random(seed);
			//UDP Socket for receiving and sending from client
			//binds to a local available socket
			DatagramSocket socket = new DatagramSocket();


}

class UDPThread extends Thread {
	
}