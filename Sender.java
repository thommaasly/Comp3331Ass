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

	private static final int PDRP = 1;
	private static final int PDUP = 2;
	private static final int PCOR = 3;
	private static final int PORD = 4;
	private static final int PDEL = 5;
	


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
	      	
	      	//initialise sSeqNum and sAckNum to be the values that are sent by sender
	      	int sSeqNum = 0;
	      	int sAckNum = 0;

	      	//initialise rSeqNum and rAckNum to be the values that are received by receiver
	    	//int rSeqNum = 0;
	    	//int rAckNum = 0;

	    	//given MWS % MSS will always be 0
	    	int maxPacSent = MWS / MSS;
	    
	      	//initialise send base, the sequence number of the oldest unacknowledged byte.
	      	//therefore sendBase - 1 is the last known seccessufula ACK'd
	      	int sendBase = 0;

	      	//initially not connected to receiver
	      	int connected = 0;

	      	//processing loop runs while the file has not been completely sent
	      	int finSending = 0;

	      	byte[] buf;

	      	//buffer of fixed type with no payload, used to receive segment from Receiver
			byte[] recBuf = new byte[STP_HEADER_SIZE];
			
			//datagrampacket that stores the received packets from Receiver 
			DatagramPacket recPac = new DatagramPacket(recBuf, recBuf.length);
    		
    		//make the socket only block for 1 second   
    		socket.setSoTimeout(1000);
			//iniialisation of timer variables (in milliseconds)
			double estimatedRTT = 500;
			double devRTT = 250;



			//Handshake
	
			//creating of SYN segment
			buf = STPHeader(sSeqNum, sAckNum, SYN_FLAG, MWS, MSS, checksum(), 0);
			//send data back 
		//	System.out.println("buff lenth is " + Integer.toString(buf.length));
		//	System.out.println("receiver_host_ip:" + receiver_host_ip.toString() + " destport: " + Integer.toString(destPort));
			DatagramPacket sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			//receive reply from Receiver
			socket.receive(recPac);
			printData(recPac);
			
			if(getAckNum(recPac.getData()) == 1 && getFlags(recPac.getData()) == (SYN_FLAG | ACK_FLAG)) {
				sSeqNum = 1;
				sAckNum = 1;
				buf = STPHeader(sSeqNum, sAckNum, ACK_FLAG, MWS, MSS, checksum(), 0);
				sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
				socket.send(sendPac);

			} else {
				System.out.println("did not received SYN_ACK, ackNo: " + Integer.toString(getAckNum(recPac.getData()))
				+ " getFlags: " + getFlags(recPac.getData()));
			}
			connected = 1;
			//Used to read in data from given file
			FileInputStream fis = new FileInputStream(f);
			System.out.println("Handshake complete, file input stream created");
			//end Handshake


			//checks if a segment has been retransmitted
			int retransmitted = 1;
			//Send the data, processing loop
			while(fis.available() != 0)
			{
				try {
					System.out.println("send data here");
					long sendTime = System.currentTimeMillis();
					
					//while there are still bytes left to send
					int rSeqNum = getSeqNum(recPac.getData());
					int rAckNum = getAckNum(recPac.getData());
					//check to see if rAckNum has received the correct packet
					if(rAckNum != sSeqNum + MSS && sSeqNum != 1) {
						System.out.println("an error has occurred" + rAckNum + "ssn: " + sSeqNum);
					}

					if(rAckNum == sSeqNum && sSeqNum != 1) {
						//for when Packet is dropped
						System.out.println("the error that occurred was packet drop , file has length" + Long.toString(f.length()));
						//resend the packet
						int error = PLDModule(random, pDrop, pDup,pCorr,pOrder,pDelay, sendPac, socket);
						//break;
						retransmitted = 1;
					} else if (rAckNum < sSeqNum) {
						//just received a duplicate ack because accidentally resent, disregard and continue
						System.out.println("received a duplicate ack");
						continue;
					} else {
						//for the final segment that isn't going to be of size MSS
						if(fis.available() < MSS) {
							MSS = fis.available();
						}
						sSeqNum = rAckNum;
						//acknumber of the sender is always 1 except for handshake and FIN
						sAckNum = rSeqNum;
						buf = STPHeader(sSeqNum, sAckNum, SYN_FLAG, MWS, MSS, checksum(), MSS);
						//puts MSS bytes into buf at an offset of STP_HEADER_LENGTH
						int read = fis.read(buf, STP_HEADER_SIZE, MSS);
						
						//initiates the packet for sending
						sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
						//use the PLD model to simultae any errors and send the datagram packet
						int error = PLDModule(random, pDrop, pDup,pCorr,pOrder,pDelay, sendPac, socket);

						retransmitted = 0;

					} 

					//receieve the packet
					System.out.println("receivingigg packet wait: " + Integer.toString(socket.getSoTimeout()));
					socket.receive(recPac);
					long receiveTime = System.currentTimeMillis();
					System.out.println("sent:");
					printData(sendPac);
					System.out.println("Received:");
					printData(recPac);
					//recalculate timeoutinterval if segment just sent has not been transmitte before
					if(retransmitted == 0) {
						// //set RTT times
						// double sampleRTT = receiveTime - sendTime;
						// System.out.println("devRTT init" + Double.toString(devRTT) + " estRTT " + Double.toString(estimatedRTT));
						// devRTT = 0.75 * devRTT + 0.25 * (sampleRTT - estimatedRTT);
					 // 	estimatedRTT = 0.875 * estimatedRTT + 1 / 8 * sampleRTT;
					 // 	double timeoutInterval = estimatedRTT + gamma * devRTT;

					 // 	System.out.println("sample RTT: " + Double.toString(sampleRTT) + " devrtt: " + Double.toString(devRTT) + " estRTT: " + Double.toString(estimatedRTT) + " setting tointerval to " + Double.toString(timeoutInterval));
						// socket.setSoTimeout((int) (timeoutInterval)); 
					}

				} 	catch (SocketTimeoutException e) {
					System.out.println("The socket timed out");
				}

				

			}

			//initaiate close, sending is done
			System.out.println("clsoe the file setting finSending to 1");
			fis.close();
			System.out.println("start FIN, send FIN_FALG");
			sSeqNum = getAckNum(recPac.getData());
			sAckNum = getSeqNum(recPac.getData()) + 1;
			//send FIN glag to receiver
			buf = STPHeader(sSeqNum, sAckNum, FIN_FLAG, MWS, MSS, checksum(), 0);
			sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			System.out.println("receive ack");
			//receieve reply, expecting ACK_FLAG;
			socket.receive(recPac);
			if(getFlags(recPac.getData()) != ACK_FLAG) {
				System.out.println("ERROR: Did not receive ACK_FLAG for FIN");
			}
			System.out.println("receive fin");
			//receieve second reply, expecting FIN_FLAG
			socket.receive(recPac);
			if(getFlags(recPac.getData()) != FIN_FLAG) {
				System.out.println("ERROR: Did not receive FIN_FLAG for FIN");
			}

			buf = STPHeader(sSeqNum, sAckNum, ACK_FLAG, MWS, MSS, checksum(), 0);
			sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			//need to wait for some time before closing



			socket.close();
			System.out.println("file had size: " + Long.toString(f.length()));
			System.out.println("Connection closed.");






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

	private static byte[] STPHeader(int seqNo, int ackNo, byte flags, int MWS, int MSS, int checksum, int stp_load_size) {
		
		if(stp_load_size != 0 && stp_load_size != MSS) {
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
		byte[] holder = new byte[STP_HEADER_SIZE];
		//System.out.println("bytelength: " + Integer.toString(buf.length));
		byteBuf.get(holder);
		byte[] buf = new byte[length];
		System.arraycopy(holder, 0, buf, 0, holder.length);

		return buf;
	 }
	private static int PLDModule(Random random, float pDrop, float pDuplicate,float pCorr,float pOrder, float pDelay, DatagramPacket sendPac, DatagramSocket socket) throws Exception {
		//random.nextFloat() is used to compare to probability of errors being simulated
		
		//error is the value being returned
		int error = 0;
		if(random.nextFloat() < pDrop) {
			//drop the packet
			System.out.println("drop a packet");
			error = PDRP;
		} 
		else if(random.nextFloat() < pDuplicate) {
			//duplicate the packet
			error = PDUP;
			System.out.println("duplicate the packet");
			socket.send(sendPac);
			socket.send(sendPac);
		}
		//  else if(random.nextFloat() < pCorr) {
		// 	//corrupt the packet
		// 	error = PCOR;
									// 	buf ^ (1 << (STP_HEADER_SIZE + MSS));

		// } else if(random.nextFloat() < pOrder) {
		// 	//reorder the packet
									// 	int __ = random.nextInt(maxOrder) + 1;

		// 	error = PORD;
		// } else if(random.nextFloat() < pDelay) {
		// 	//delete the packet
		// 	error = PDEL;
		// } 
		else {
			//send the packet normally
				socket.send(sendPac);
		}


		return error;
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



// 	}
// }