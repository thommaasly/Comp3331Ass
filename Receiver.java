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
	
	private static int bytes_received = 0;
	private static int total_segments_received = 0;
	private static int data_segments_received = 0;
	private static int data_segments_errors = 0;
	private static int data_segments_duplicated = 0;
	private static int duplicate_acks_sent = 0;

	private static long initTime;


	//STP header will always be fixed size when sent by receiver because no payload
	private static final byte STP_HEADER_SIZE = 21;
	private static PrintStream log;

	public static void main(String[] args) throws Exception
	{
		// Get command line argument.
		if (args.length != 2) {
			System.out.println("Required arguments: receiver_port file_r.pdf");
			return;
		}

		//text file that contains metadata on packets sent
		log = new PrintStream(new File("Receiver_log.txt"));

		//the port number which the Reeiver will open a UDP socket for receiving datagrams
		int port = Integer.parseInt(args[0]);
		//the name of the pdf file which the data hsould be stored
		File f = new File(args[1]); //may need to check if this one works
		//creates a file output stream to write to the file given in arguments
		//set so that writes willl append to the file rather than make a new file
		FileOutputStream fos = new FileOutputStream(f);
      	
      	//initialise first sequence number;
      	int nextSeqNum = 0;
      	int ackNum = 0;

		// Create a datagram socket for sending UDP packets
		// bind to port passed in as variable
		DatagramSocket socket = new DatagramSocket(port);

		//initiate MSS will a starting value of 0
		int MSS = 0;

		//handshake completed or not
		int handshake = 0;
		int timeCounted = 0;
		//indicates if transmission has started, used for if first segment gets duplicated
		int startTrans = 0;
		while(socket.isClosed() != true) {
						
			// Create a datagram packet to hold incomming UDP packet.
			DatagramPacket request = new DatagramPacket(new byte[STP_HEADER_SIZE + MSS], STP_HEADER_SIZE + MSS);

//			try{
			socket.receive(request);
			if(timeCounted == 0) {
				initTime = System.currentTimeMillis();
				timeCounted = 1;
			}
			//log.printf("rcv\t%.2f\tS\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));

			//storing data received inside variables
			InetAddress sender_host_ip = request.getAddress();
			int sender_port = request.getPort();
			int length = request.getLength();
			ByteBuffer byteBuf = ByteBuffer.wrap(request.getData())	;
			int seqNo = byteBuf.getInt();
			int ackNo = byteBuf.getInt();
			byte flags = byteBuf.get();
			int MWS = byteBuf.getInt();
			MSS = byteBuf.getInt();
			int checksum = byteBuf.getInt();
			byte[] payload = new byte[byteBuf.remaining()];
			byteBuf.get(payload);

			printData(request);
			//establish connection with receiver	 - HANDSHAKE
			if((flags & SYN_FLAG) != 0 && handshake != 1) {
				log.printf("rcv\t%.2f\tS\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
				total_segments_received +=1;
				//reply with a SYN_ACK
				//ackNum is the received seqNum+1
				ackNum = seqNo + 1;
				nextSeqNum = ackNo + 1;
				byte flag = SYN_FLAG | ACK_FLAG;
				byte[] buf = STPHeader(nextSeqNum, ackNum, flag, MWS, MSS, checksum, 0);
				DatagramPacket sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
				socket.send(sendPac);
				log.printf("snd\t%.2f\tS\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(sendPac.getData()), sendPac.getLength()- STP_HEADER_SIZE, getAckNum(sendPac.getData()));

				byte[] recbuf = new byte[STP_HEADER_SIZE + MSS];
				socket.receive(request);
				log.printf("rcv\t%.2f\tA\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
				total_segments_received +=1;
				if(getFlags(request.getData()) == ACK_FLAG) {
					System.out.println("got ACK flag");
				}
				handshake = 1;
			}
			//terminate connection with sender
			else if((flags & FIN_FLAG) != 0) {
				log.printf("rcv\t%.2f\tF\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
				total_segments_received +=1;
				System.out.println("closing connection");
				//send ACK to FIN received
				System.out.println("sending closing ACK_FLAG");
				ackNum = seqNo + 1;
				byte[] buf = STPHeader(nextSeqNum, ackNum, ACK_FLAG, MWS, MSS, checksum, 0);
				DatagramPacket sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
				socket.send(sendPac);
				log.printf("snd\t%.2f\tF\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(sendPac.getData()), sendPac.getLength()- STP_HEADER_SIZE, getAckNum(sendPac.getData()));

				System.out.println("sending closing FIN_FLAG");
				//send FIN flag after ACK
				buf = STPHeader(nextSeqNum, ackNum, FIN_FLAG, MWS, MSS, checksum, 0);
				sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
				socket.send(sendPac);
				log.printf("snd\t%.2f\tA\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(sendPac.getData()), sendPac.getLength()- STP_HEADER_SIZE, getAckNum(sendPac.getData()));

				socket.receive(request);
				log.printf("rcv\t%.2f\tA\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
				total_segments_received +=1;
				if(getFlags(request.getData()) != ACK_FLAG) {
					System.out.println("ERROR: failed to receive ACK_FLAG for close");
				}
				socket.close();
				System.out.println("closed");
			} 
			//receiving data
			else {

				//the checksum we calculate
				int calChecksum = checkSum(request.getData());
				 //the checksum given to us
				int sChecksum = getChecksum(request.getData());
				System.out.println("calchecksum " + calChecksum + " sChecksum " + sChecksum);
				if(calChecksum != sChecksum) {
					log.printf("rcv/corr\t%.2f\tD\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
					bytes_received += request.getLength()-STP_HEADER_SIZE;
					total_segments_received +=1;
					data_segments_received +=1;
					data_segments_errors +=1;
					//corrupt packet received
					System.out.println("corrupt packet received");
					//drop the packet	
				}
				//when seqNo is not matching, from Duplicate packets
				else if(seqNo != ackNum || (startTrans == 1 && seqNo != ackNum )) {
					System.out.println("duplicate packet received got seqNo: " + Integer.toString(seqNo) + " instead of: " + Integer.toString(ackNum + MSS));
					log.printf("rcv\t%.2f\tD\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
					duplicate_acks_sent +=1;
					bytes_received += request.getLength()-STP_HEADER_SIZE;
					total_segments_received +=1;
					data_segments_received +=1;
					data_segments_duplicated +=1;
					//resend lack ack that was proper
					byte[] buf = STPHeader(nextSeqNum, ackNum, ACK_FLAG, MWS, MSS, checksum, 0);
					DatagramPacket sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
					socket.send(sendPac);
					log.printf("snd/DA\t%.2f\tA\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(sendPac.getData()), sendPac.getLength()- STP_HEADER_SIZE, getAckNum(sendPac.getData()));

				}
				else {
					System.out.println("writing data");
					log.printf("rcv\t%.2f\tD\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(request.getData()), request.getLength()- STP_HEADER_SIZE, getAckNum(request.getData()));
					bytes_received += request.getLength()-STP_HEADER_SIZE;
					total_segments_received +=1;
					data_segments_received +=1;
					System.out.println("got as wanted seqNo: " + Integer.toString(seqNo) );

					//writes the first segment which has seqNo 1 which is the same as during handshake (not to be confused with duplicate)
					if(startTrans == 0) {
						startTrans = 1;
					}

					//when the correct segment is receieved
					//got the data from request, need to write it in
					fos.write(request.getData(), STP_HEADER_SIZE, MSS);

					//update syn, ack variables
					nextSeqNum = ackNo;
					ackNum = seqNo + request.getLength() - STP_HEADER_SIZE;

					//send ack for data written
					byte[] buf = STPHeader(nextSeqNum, ackNum, ACK_FLAG, MWS, MSS, checksum, 0);
					DatagramPacket sendPac = new DatagramPacket(buf, buf.length, sender_host_ip, sender_port);
					socket.send(sendPac);
					log.printf("snd\t%.2f\tA\t%d\t%d\t%d%n", elapsedTime(initTime), getSeqNum(sendPac.getData()), sendPac.getLength()- STP_HEADER_SIZE, getAckNum(sendPac.getData()));

				}
			}


		}
		log.printf("==============================================%n");
		log.printf("Amount of data received (bytes) %d%n",bytes_received);
		log.printf("Total Segments Received %d%n", total_segments_received);
		log.printf("Data segments received %d%n", data_segments_received);
		log.printf("Data segments with Bit Errors %d%n", data_segments_errors);
		log.printf("Duplicate data segments received %d%n", data_segments_duplicated);
		log.printf("Duplicate ACKs sent %d%n",duplicate_acks_sent);
		log.printf("==============================================%n");
		 
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

	private static byte[] STPHeader(int seqNo, int ackNo, byte flags, int MWS, int MSS, int checksum, int stp_load_size) {
		
		if(stp_load_size != 0 || stp_load_size != MSS) {
			System.out.println("load length is not 0 or MSS: " + Integer.toString(stp_load_size));
		}
		int length = STP_HEADER_SIZE + stp_load_size;
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
		byteBuf = ByteBuffer.wrap(buf);
		byteBuf.putInt(17, checkSum(buf));
		return buf;
	 }

	private static double elapsedTime(long startTime) {
		double elapsedTime = System.currentTimeMillis() - startTime;
		//convert to seconds
		elapsedTime /= 1000;
		return elapsedTime;
	}
	private static int checkSum(byte[] segment) {
		int b = 0;
		//range of header (excluding checksum variable);
		for(int i = 0; i < (STP_HEADER_SIZE -4); i++) {
			b += (int) segment[i];
		}
		return b;
	}
	private static int getSeqNum(byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt();
	}

	private static int getAckNum (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(4);
	}
	
	private static byte getFlags (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.get(8);
	}
	
	private static int getMWS (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(9);
	}

	private static int getMSS (byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(13);
	}

	private static int getChecksum(byte[] buf) 
	{
		ByteBuffer byteBuf = ByteBuffer.wrap(buf);
		return byteBuf.getInt(17);
	}
}
