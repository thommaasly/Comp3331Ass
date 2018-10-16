import java.io.*;
import java.net.*;
import java.util.*;

/*
* the server
*java Sender receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop
pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed
*/
public class Sender {
	
	public static void main(String[] args) throws Exception
		{
			//number of bits in the header
			//consists of sourceport# (16b) destport# (16b) seq# (32b) ack# (32b) flags(4b) window size (16b) checksum (16b)
			private static final int STP_HEADER_SIZE = 132;
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
	      	bool connected = 0;

			//processing loop
			while (true) {
				//if not connected to receiver, send first SYN
				if(!connected) {
					//creating of SYN segment
					STPHeader(socket.getLocalPort(), destPort, nextSeqNum, ackNum, 1, 0, 0, 0, MWS, short checksum);
				}


				//buffer to store incoming data
				ByteBuffer buf =  ByteBuffer.allocate(STP_HEADER_SIZE);
				//packet receivered from client, holds the incoming UDP packet

				//datagram packet
				DatagramPacket request = new DatagramPacket(buf,buf.length);

				//store any request coming in from socket into datagrampacket
				socket.receive(request);


				if(datareceived from receiver) {
					create STP segment with seq num nextSeqNum
					if(timer not running) {
						start timer
					}
					pass segment to IP
					nextSeqNum = NextSeqNum + length(data);
				}
				//timer is associated with oldest non-acked segment 
				else if(timerTimeout) {
					retransmit not ack'd semgnets'
					start timer
				} 
				else if(ack received, with ack field value of y) {
					if (y> sendbase) {
						sendbase = y;
						if(there are currently any not yet acked segments){ 
							start timer
						}
					}
				}


				//initialise STP header
				//source port num
				sourcePort = socket.getLocalPort();
				//dest port num already initialised
				//sequence number already initialised
				if(ackNum > )
				nextSeqNum = ackNum;



				//checks the acks and stuff
				processData(request);

				//fill in buf with data to be sent back
				getData(&buf)

				//send data back 
				DatagramPacket reply = new DatagramPacket(buf, buf.length, clientHost, clientPort);
				socket.send(reply);
			}

	}

}
class STPHeader(int sourcePort, int destPort, int seqNo, int ackNo, bool SYN, bool ACK, bool RST, bool FIN, int MWS, int checksum) {
	public int sourcePort = sourcePort;
	public int destPort = destPort;
	public int seqNo = seqNo;
	public int ackNo = ackNo;
	public bool SYN = SYN;
	public bool	ACK = ACK;
	public bool RST = RST;
	public bool FIN = FIN;
	public int MWS = MWS;
	public int checksum = checksum;
 }

private static void handshake(DatagramPacket request) throws Exception
{
	
}

private static ByteBuffer handshake(DatagramPacket request) throws Exception
{
	
}

private static void PLDModel() throws Exception
{


//set to initially 1000ms i.e. 1second
TimeoutInterval = 1000;
	//when time timeout occurs, double the timeoutinterval
	if(timeout) {
		TimeoutInterval *= 2;
	} else {
		//otherwise, use the given formulae
		//for calculating timeoutinterval
		int SampleRTT = ??
		int DevRTT = 0.75 * DevRTT + 0.25 * (SampleRTT - EstimatedRTT);
		int EstimatedRTT = 0.875 * EstimatedRTT + 0.125 * SampleRTT;
		int TimeoutInterval = EstimatedRTT + gamma * DevRTT;

	}
}
/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

// public class PingServer
// {
//    private static final double LOSS_RATE = 0.3;
//    private static final int AVERAGE_DELAY = 100;  // milliseconds

//    public static void main(String[] args) throws Exception
//    {
//       // Get command line argument.
//       if (args.length != 1) {
//          System.out.println("Required arguments: port");
//          return;
//       }
//       int port = Integer.parseInt(args[0]);

//       // Create random number generator for use in simulating 
//       // packet loss and network delay.
//       Random random = new Random();

//       // Create a datagram socket for receiving and sending UDP packets
//       // through the port specified on the command line.
//       DatagramSocket socket = new DatagramSocket(port);

      // Processing loop.
      while (true) {
         // Create a datagram packet to hold incomming UDP packet.
         DatagramPacket request = new DatagramPacket(new byte[1024], 1024);

         // Block until the host receives a UDP packet.
         socket.receive(request);
         
         // Print the recieved data.
         printData(request);

         // Decide whether to reply, or simulate packet loss.
         if (random.nextDouble() < LOSS_RATE) {
            System.out.println("   Reply not sent.");
            continue; 
         }

         // Simulate network delay.
         Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

         // Send reply.
         InetAddress clientHost = request.getAddress();
         int clientPort = request.getPort();
         byte[] buf = request.getData();
         DatagramPacket reply = new DatagramPacket(buf, buf.length, clientHost, clientPort);
         socket.send(reply);

         System.out.println("   Reply sent.");
      }
   }

//    /* 
//     * Print ping data to the standard output stream.
//     */
//    private static void printData(DatagramPacket request) throws Exception
//    {
//       // Obtain references to the packet's array of bytes.
//       byte[] buf = request.getData();

//       // Wrap the bytes in a byte array input stream,
//       // so that you can read the data as a stream of bytes.
//       ByteArrayInputStream bais = new ByteArrayInputStream(buf);

//       // Wrap the byte array output stream in an input stream reader,
//       // so you can read the data as a stream of characters.
//       InputStreamReader isr = new InputStreamReader(bais);

//       // Wrap the input stream reader in a bufferred reader,
//       // so you can read the character data a line at a time.
//       // (A line is a sequence of chars terminated by any combination of \r and \n.) 
//       BufferedReader br = new BufferedReader(isr);

//       // The message data is contained in a single line, so read this line.
//       String line = br.readLine();

//       // Print host address and data received from it.
//       System.out.println(
//          "Received from " + 
//          request.getAddress().getHostAddress() + 
//          ": " +
//          new String(line) );
//    }
// }
