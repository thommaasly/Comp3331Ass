import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;
import java.nio.ByteBuffer;

/*
* the client
*/

//use case: java Receiver receiver_port file_r.pdf

//LOOK AT TABLE 3.2 IN PAGE 250
/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

public class Receiver
{
	//flags for STP header
	private static final byte SYN_FLAG = 0x8;
	private static final byte ACK_FLAG = 0x4;
	private static final byte RST_FLAG = 0x2;
	private static final byte FIN_FLAG = 0x1;
	private static final byte NUL_FLAG = 0x0;
	
	//STP header will always be fixed size when sent by receiver because no payload
	private static final byte STP_HEADER_SIZE = 21;

	public static void main(String[] args) throws Exception
	{
		// Get command line argument.
		if (args.length != 2) {
			System.out.println("Required arguments: receiver_port file_r.pdf");
			return;
		}
		//the port number which the Reeiver will open a UDP socket for receiving datagrams
		int port = Integer.parseInt(args[0]);
		//the name of the pdf file which the data hsould be stored
		File f = new File(args[1]); //may need to check if this one works

      	//initialise first sequence number;
      	int nextSeqNum = 0;
      	int ackNum = 0;

		// Create a datagram socket for sending UDP packets
		// bind to port passed in as variable
		DatagramSocket socket = new DatagramSocket(port);
		System.out.println("socket is bound to port" + Integer.toString(port) + "and is connected: " + socket.isConnected());
		// Create a datagram packet to hold incomming UDP packet.
		DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
		while(true) {
//			try{
			socket.receive(request);
			//storing data received inside variables
			InetAddress sender_host_ip = request.getAddress();
			int sender_port = request.getPort();
			int length = request.getLength();
			ByteBuffer byteBuf = ByteBuffer.wrap(request.getData())	;
			int seqNo = byteBuf.getInt();
			int ackNo = byteBuf.getInt();
			byte flags = byteBuf.get();
			int MWS = byteBuf.getInt();
			int MSS = byteBuf.getInt();
			int checksum = byteBuf.getInt();
			byte[] payload = new byte[byteBuf.remaining()];
			byteBuf.get(payload);
				
			if((flags & SYN_FLAG) != 0) {
				System.out.println("we got a SYN_FLAG");
				//reply with a SYN_ACK
				//ackNum is the received seqNum+1
				ackNum = seqNo + 1;
				flags = SYN_FLAG | ACK_FLAG;
				byte[] buf = STPHeader(nextSeqNum, ackNum, flags, MWS, MSS, checksum, "");
				DatagramPacket sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
				socket.send(sendPac);

				byte[] recbuf = new byte[STP_HEADER_SIZE + MSS];
				socket.receive(request);
				if(getFlags(request.getData()) == ACK_FLAG) {
					System.out.println("got ACK flag");
				}
			}
			printData(request);


		}
			 
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

	private static byte[] STPHeader(int seqNo, int ackNo, byte flags, int MWS, int MSS, int checksum, String stp_load) {
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
		//System.out.println("here's what I got: " + byteBuf.toString());
//System.out.println("newline");
		byte[] buf = new byte[length];
		//System.out.println("bytelength: " + Integer.toString(buf.length));
		byteBuf.get(buf);

		return buf;
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
