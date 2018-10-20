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
	
	//variables used for reordering, made global
	// private static int hasReOrder = 0;
	// private static DatagramPacket reOrder;
	// private static int waited = 0;

	//determines if a timeout has occurred
	private static int retransmitted = 0;

	private static byte[] oldBuf = new byte[0];
	private static DatagramPacket sendPac = new DatagramPacket(new byte[0],0);

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
    		double timeoutInterval = 1000;
			//iniialisation of timer variables (in milliseconds)
			double estimatedRTT = 500;
			double devRTT = 250;



			//Handshake
			//Used to read in data from given file
			FileInputStream fis = new FileInputStream(f);	
			//creating of SYN segment
			buf = STPHeader(sSeqNum, sAckNum, SYN_FLAG, MWS, MSS, 0, 0, fis);
			//rece back 
		//	System.out.println("buff lenth is " + Integer.toString(buf.length));
		//	System.out.println("receiver_host_ip:" + receiver_host_ip.toString() + " destport: " + Integer.toString(destPort));
			sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			//receive reply from Receiver
			socket.receive(recPac);
			sAckNum = getAckNum(recPac.getData());
			if(getAckNum(recPac.getData()) == 1 && getFlags(recPac.getData()) == (SYN_FLAG | ACK_FLAG)) {
				sSeqNum = 1;
				
				buf = STPHeader(sSeqNum, sAckNum, ACK_FLAG, MWS, MSS, 0, 0, fis);
				sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
				socket.send(sendPac);

			} else {
				System.out.println("did not received SYN_ACK, ackNo: " + Integer.toString(getAckNum(recPac.getData()))
				+ " getFlags: " + getFlags(recPac.getData()));
			}
			connected = 1;


			System.out.println("Handshake complete, file input stream created");
			//end Handshake


			//determines if a duplicate pack was received
			int dupPac = 0;
			//checks if a segment has been retransmitted
			retransmitted = 1;

			//size of payload (excluding header)			
			int stp_load_size = 0;

			//determines the behaviour of the packet
			int error = 0;





			//Send the data, processing loop
			while(fis.available() != 0)
			{
					System.out.println("send data here");
					//starts counting the time
					Timer timer = new Timer();
					timer.schedule(new TimerTask() {
					@Override
					public void run() {	
						try {
							System.out.println("THE SOCKET TIMED OUT");
							//for when Packet is dropped
							System.out.println("the error that occurred was packet drop or corrupt packet");
							//resend the packet
							sendPac.setData(oldBuf);
							socket.send(sendPac);
							retransmitted = 1;
						} catch (Exception e) {
							System.out.println("		COULD NOT RETRANSMIT, first transmission was wrong");
							//socket.send()
						}
					  }
					}, (long) timeoutInterval);	
					System.out.println("run other code");
					long sendTime = System.currentTimeMillis();
					
					//while there are still bytes left to send
					int rSeqNum = getSeqNum(recPac.getData());
					int rAckNum = getAckNum(recPac.getData());
					//if there isi a segment that hsa been reordered and has waited for maxOrder other segments
					//sends the packet
					// if(hasReOrder == 1 && waited == maxOrder) {
					// 	socket.send(reOrder);
					// 	hasReOrder = 0;
					// }
					
					if(rAckNum != sSeqNum + MSS && sSeqNum != 1) {
						System.out.println("an error has occurred" + rAckNum + "ssn: " + sSeqNum);
					}

				if(rAckNum == sSeqNum && sSeqNum != 1) {
						//just received a duplicate ack because accidentally resent, do nothing in response
						System.out.println("received a duplicate ack");
						dupPac = 1;
					} else {
						System.out.println("Noerror");

						//for the final segment that isn't going to be of size MSS
						if(fis.available() < MSS) {
							stp_load_size = fis.available();
						} else {
							stp_load_size = MSS;
						}
						//increase sSeqNum based on segment acked.
						//only doesn't change when error occurred previously
						if(sSeqNum < rAckNum) {
							sSeqNum = rAckNum;
						}
						//acknumber of the sender is always 1 except for handshake and FIN
						sAckNum = rSeqNum;
						buf = STPHeader(sSeqNum, sAckNum, SYN_FLAG, MWS, MSS, 0, stp_load_size, fis);
						// //puts MSS bytes into buf at an offset of STP_HEADER_LENGTH
						// int read = fis.read(buf, STP_HEADER_SIZE, stp_load_size);

						//store the segment to be sent as a backup in case of retansmission of corruption
						
					
						//initiates the packet for sending

						sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);


						//use the PLD model to simulate any errors and send the datagram packet
						error = PLDModule(random, pDrop, pDup,pCorr,pOrder,pDelay, maxDelay, sendPac, socket);
						if(error == PORD) {
							continue;
						}
						retransmitted = 0;

					} 

					//receieve the packet
					//System.out.println("receivingigg packet wait: " + Integer.toString(socket.getSoTimeout()));
					
					System.out.println("abouttoreceive");
					socket.receive(recPac);
					
					
					//if the socket didn't time out, cancel it
					timer.cancel();
								
					long receiveTime = System.currentTimeMillis();
					System.out.println("sent:");
					printData(sendPac);
					System.out.println("Received:");
					printData(recPac);
				//	recalculate timeoutinterval if segment just sent has not been transmitte before
					if(retransmitted == 0) {
						// //set RTT times
						double sampleRTT = receiveTime - sendTime;
						
						double difference = sampleRTT - estimatedRTT;
						if(difference < 0) {
							difference *= -1;
						}
						System.out.println("devRTT befor" + Double.toString(devRTT) + " estRTT " + Double.toString(estimatedRTT) + " difference: " + Double.toString(difference) + " sample " + Double.toString(sampleRTT));

						devRTT = 0.75 * devRTT + 0.25 * (difference);
					 	estimatedRTT = 0.875 * estimatedRTT + 0.125 * sampleRTT;
					 	timeoutInterval = estimatedRTT + gamma * devRTT;

					 // 	System.out.println("devRTT after" + Double.toString(devRTT) + " estRTT " + Double.toString(estimatedRTT));
						 System.out.println("timeoutINternaval: " + timeoutInterval);
					 	//System.out.println("sample RTT: " + Double.toString(sampleRTT) + " devrtt: " + Double.toString(devRTT) + " estRTT: " + Double.toString(estimatedRTT) + " setting tointerval to " + Double.toString(timeoutInterval));
					}

			}



			//initaiate close, sending is done
			System.out.println("clsoe the file setting finSending to 1");
			fis.close();
			System.out.println("start FIN, send FIN_FALG");
			sSeqNum = sSeqNum + stp_load_size;
			sAckNum = getSeqNum(recPac.getData()) + 1;
			//send FIN glag to receiver
			buf = STPHeader(sSeqNum, sAckNum, FIN_FLAG, MWS, MSS, 0, 0, fis);
			sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			System.out.println("receive ack");
			//receieve reply, expecting ACK_FLAG;
			socket.receive(recPac);
								System.out.println("sent:");
					printData(sendPac);
					System.out.println("Received:");
					printData(recPac);
			if(getFlags(recPac.getData()) != ACK_FLAG) {
				System.out.println("ERROR: Did not receive ACK_FLAG for FIN");
			}
			System.out.println("receive fin");
			//receieve second reply, expecting FIN_FLAG
			socket.receive(recPac);
			if(getFlags(recPac.getData()) != FIN_FLAG) {
				System.out.println("ERROR: Did not receive FIN_FLAG for FIN");
			}
			sSeqNum += 1;
			buf = STPHeader(sSeqNum, sAckNum, ACK_FLAG, MWS, MSS, 0, 0, fis);
			sendPac = new DatagramPacket(buf, buf.length, receiver_host_ip, destPort);
			socket.send(sendPac);

			//need to wait for some time before closing


					System.out.println("Received:");
					printData(recPac);
					System.out.println("sent:");
					printData(sendPac);
			socket.close();
			System.out.println("file had size: " + Long.toString(f.length()));
			System.out.println("Connection closed.");




		

	}

	private static byte[] STPHeader(int seqNo, int ackNo, byte flags, int MWS, int MSS, int checksum, int stp_load_size, FileInputStream fis) throws Exception {
		
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
		//this value is 0 because checksum is calculated once the whle segment is filled in, think of this as a filler
		byteBuf.putInt(checksum);
		//byteBuf.put(stp_load);

		//makes it so the index of byteBuf goes back to 0 with limit at w/e index was at. allows get
		byteBuf.flip();
		byte[] holder = new byte[STP_HEADER_SIZE];
		//System.out.println("bytelength: " + Integer.toString(buf.length));
		byteBuf.get(holder);
		byte[] buf = new byte[length];
		System.arraycopy(holder, 0, buf, 0, holder.length);

		//puts MSS bytes into buf at an offset of STP_HEADER_LENGTH
		int read = fis.read(buf, STP_HEADER_SIZE, stp_load_size);
		byteBuf = ByteBuffer.wrap(buf);
		byteBuf.putInt(17, checkSum(buf));

		return buf;
	 }
	private static int PLDModule(Random random, float pDrop, float pDuplicate,float pCorr,float pOrder, float pDelay, float maxDelay, DatagramPacket sendPac, DatagramSocket socket) throws Exception {
		//random.nextFloat() is used to compare to probability of errors being simulated
		oldBuf = new byte[sendPac.getLength()];
		System.arraycopy(sendPac.getData(), 0, oldBuf, 0, sendPac.getLength());
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
		 else if(random.nextFloat() < pCorr) {
		 	System.out.println("corrupt the packet");
			//corrupt the packet
			error = PCOR;
			invertBit(sendPac.getData(),sendPac.getLength()); 
			socket.send(sendPac);
		}
		// else if(random.nextFloat() < pOrder) {
		// 	//reorder the packet
		// 	//store the packet somoeone else, so it is not sent
		// 	System.out.println("reorder the pack");
		// 	if(hasReOrder == 0) {
		// 		System.out.println("	NEED TO REORDER, PUT INTO QUEUE");
		// 		reOrder = sendPac;
		// 		hasReOrder = 1;
		// 	} else {
		// 		System.out.println("already have something in reorder queue, send segment normally");
		// 		socket.send(sendPac);
		// 	}
		// 	error = PORD;
		// } 
		else if(random.nextFloat() < pDelay) {
			//delay the packet
			System.out.println("delay for some time before sending");
			int sleep = random.nextInt((int) maxDelay);
			Thread.sleep(sleep);
			error = PDEL;
		} 
		else {
			//send the packet normally
			socket.send(sendPac);
		}


		return error;
	}

	private static void invertBit(byte[] segment, int length) {
		//inverts the 4th last bit in the MSS variable of the header
		segment[STP_HEADER_SIZE-5] = (byte) (segment[STP_HEADER_SIZE -5] ^ (1 << 3));

	
		// //use a bytebuffer representation of the data
		// ByteBuffer byteBuf = ByteBuffer.wrap(segment);
		// //convert the bytebuffer to an integer 
		// IntBuffer intBuf = byteBuf.asIntBuffer();
		// //convert the intBuffer into an integer array


		// //makes it so the index of byteBuf goes back to 0 with limit at w/e index was at. allows get
		// // byteBuf.flip();
		// // byte[] holder = new byte[STP_HEADER_SIZE];
		// // //System.out.println("bytelength: " + Integer.toString(buf.length));
		// // byteBuf.get(holder);
		// // byte[] buf = new byte[length];
		// // System.arraycopy(holder, 0, buf, 0, holder.length);

	}
	private static int checkSum(byte[] segment) {
		int b = 0;
		//range of header (excluding checksum variable);
		for(int i = 0; i < (STP_HEADER_SIZE -4); i++) {
			b += (int) segment[i];
		}
		return b;
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