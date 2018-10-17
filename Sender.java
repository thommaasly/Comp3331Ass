import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;

import java.nio.ByteBuffer;

/*
* the server
*java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop
pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed
*/
//===TESTS===
//java Sender 127.0.0.1 2008 8889 test0.pdf 500 100 4 0 0 0 0 0 0 0 100


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
			float pdelay = Float.parseFloat(args[11]);
			//maximum delay in milliseconds
			int maxDelay = Integer.parseInt(args[12]);
			//seed for random number generator
			int seed = Integer.parseInt(args[13]);
		

			//UDP Socket for receiving and sending from client
			//binds to a local available socket
			DatagramSocket socket = new DatagramSocket();
	      	
	      	//initialise first sequence number;
	      	int nextSeqNum = 0;
	      	int ackNum = 0;
	      	//initialise send base, the sequence number of the oldest unacknowledged byte.
	      	//therefore sendBase - 1 is the last known seccessufula ACK'd
	      	int sendBase = 0;

	      	//initially not connected to receiver
	      	int connected = 0;

			//processing loop
			while (true) {
				//if not connected to receiver, send first SYN
				if(connected != 1) {
					//creating of SYN segment
					byte[] buf = STPHeader(nextSeqNum, ackNum, SYN_FLAG, MWS, MSS, checksum(), "");
					//send data back 
					System.out.println("buff lenth is " + Integer.toString(buf.length));
					System.out.println("receiver_host_ip:" + receiver_host_ip.toString() + " destport: " + Integer.toString(destPort));
					DatagramPacket sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
					socket.send(sendPac);
					System.out.println("SYN sent");



					DatagramPacket recPac = new DatagramPacket(buf, buf.length);
					socket.receive(recPac);
					printData(recPac);
					
					if(getAckNo(recPac.getData()) == 1 && getFlags(recPac.getData()) == (SYN_FLAG | ACK_FLAG)) {
						nextSeqNum = 1;
						ackNum = 1;
						buf = STPHeader(nextSeqNum, ackNum, ACK_FLAG, MWS, MSS, checksum(), "");
						sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
						socket.send(sendPac);
						System.out.println("ACK sent");

					} else {
						System.out.println("did not received SYN_ACK, ackNo: " + Integer.toString(getAckNo(recPac.getData()))
						+ " getFlags: " + getFlags(recPac.getData()));

					}


					connected = 1;
					// try {
						
					// }

				}
				System.out.println("Handshake complete");
				break;

				// //buffer to store incoming data
				// ByteBuffer buf =  ByteBuffer.allocate(STP_HEADER_SIZE);
				// //packet receivered from client, holds the incoming UDP packet

				// //datagram packet
				// DatagramPacket request = new DatagramPacket(buf,buf.length);

				// //store any request coming in from socket into datagrampacket
				// socket.receive(request);



				// if(datareceived from receiver) {
				// 	create STP segment with seq num nextSeqNum
				// 	if(timer not running) {
				// 		start timer
				// 	}
				// 	pass segment to IP
				// 	nextSeqNum = NextSeqNum + length(data);
				// }
				// //timer is associated with oldest non-acked segment 
				// else if(timerTimeout) {
				// 	retransmit not ack'd semgnets'
				// 	start timer
				// } 
				// else if(ack received, with ack field value of y) {
				// 	if (y> sendbase) {
				// 		sendbase = y;
				// 		if(there are currently any not yet acked segments){ 
				// 			start timer
				// 		}
				// 	}
				// }


				// //initialise STP header
				// //source port num
				// sourcePort = socket.getLocalPort();
				// //dest port num already initialised
				// //sequence number already initialised
				// if(ackNum > )
				// nextSeqNum = ackNum;



				// //checks the acks and stuff
				// processData(request);

				// //fill in buf with data to be sent back
				// getData(&buf)


			}

	}

	private static byte[] STPHeader(int seqNo, int ackNo, byte flags, int MWS, int MSS, int checksum, String stp_load) {
		System.out.println("seqNo: " + Integer.toString(seqNo) +
		" ackNo: " + Integer.toString(ackNo) +
		" flags: " + Byte.toString(flags) +
		" MWS: " + Integer.toString(MWS) +
		" MSS: " + Integer.toString(MSS) + 
		" checksum: " + Integer.toString(checksum));

		int length = STP_HEADER_SIZE + stp_load.length();
		ByteBuffer byteBuf = ByteBuffer.allocate(length);

		byteBuf.putInt(seqNo);
		byteBuf.putInt(ackNo);
		//assume bool is a byte 
		byteBuf.put(flags);
		byteBuf.putInt(MWS);
		byteBuf.putInt(MSS);
		byteBuf.putInt(checksum);
		//byteBuf.put(stp_load);

		//makes it so the index of byteBuf goes back to 0 with limit at w/e index was at. allows get
		byteBuf.flip();
		byte[] buf = new byte[length];
		//System.out.println("bytelength: " + Integer.toString(buf.length));
		byteBuf.get(buf);

		return buf;
	 }

	private static int checksum() {
		return 0;
	}

		/* 
	 * Print ping data to the standard output stream.
	 */
	private static void printData(DatagramPacket request) throws Exception
	{
		int length = request.getLength();
		ByteBuffer byteBuf = ByteBuffer.wrap(request.getData())	;
		int seqNo = byteBuf.getInt();
		int ackNo = byteBuf.getInt();
		byte flags = byteBuf.get();
		int MWS = byteBuf.getInt();
		int MSS = byteBuf.getInt();
		int checksum = byteBuf.getInt();
		System.out.println("Length: " + Integer.toString(length) +
		 "seqNo: " + Integer.toString(seqNo) +
		" ackNo: " + Integer.toString(ackNo) +
		" flags: " + Byte.toString(flags) +
		" MWS: " + Integer.toString(MWS) +
		" MSS: " + Integer.toString(MSS) +
		" checksum: " + Integer.toString(checksum));
	}

	private static int getSeqNo(byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt();
	}

	private static int getAckNo (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(4);
	}
	
	private static byte getFlags (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.get(8);
	}
	
	private static int MWS (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(9);
	}

	private static int MSS (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(13);
	}

	private static int checksum(byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(17);
	}

}

// private static void handshake(DatagramPacket request) throws Exception
// {
	
// }

// private static ByteBuffer handshake(DatagramPacket request) throws Exception
// {
	
// }

// private static void PLDModel() throws Exception
// {


// //set to initially 1000ms i.e. 1second
// TimeoutInterval = 1000;
// 	//when time timeout occurs, double the timeoutinterval
// 	if(timeout) {
// 		TimeoutInterval *= 2;
// 	} else {
// 		//otherwise, use the given formulae
// 		//for calculating timeoutinterval
// 		int SampleRTT = ??
// 		int DevRTT = 0.75 * DevRTT + 0.25 * (SampleRTT - EstimatedRTT);
// 		int EstimatedRTT = 0.875 * EstimatedRTT + 0.125 * SampleRTT;
// 		int TimeoutInterval = EstimatedRTT + gamma * DevRTT;

// 	}
// }