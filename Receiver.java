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

		// Create a datagram socket for sending UDP packets
		// bind to port passed in as variable
		DatagramSocket socket = new DatagramSocket(port);
		System.out.println("socket is bound to port" + Integer.toString(port) + "and is connected: " + socket.isConnected());
		// Create a datagram packet to hold incomming UDP packet.
		DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
		while(true) {
			System.out.println("asdf");
//			try{
				socket.receive(request);
				System.out.println("socket received of length" + Integer.toString(request.getLength()));
				//printData(request);
				int length = request.getLength();
				ByteBuffer byteBuf = ByteBuffer.wrap(request.getData())	;
				int sourcePort = byteBuf.getInt();
				System.out.println("the soruce port we got is: " + Integer.toString(sourcePort));
		// byteBuf.putInt(sourcePort);
		// byteBuf.putInt(destPort);
		// byteBuf.putInt(seqNo);
		// byteBuf.putInt(ackNo);
		// //assume bool is a byte 
		// byteBuf.put(flags);
		// byteBuf.putInt(MWS);
		// byteBuf.putInt(checksum);


		}
			 
	}

	/* 
	 * Print ping data to the standard output stream.
	 */
	private static void printData(DatagramPacket request) throws Exception
	{
		// Obtain references to the packet's array of bytes.
		byte[] buf = request.getData();

		// Wrap the bytes in a byte array input stream,
		// so that you can read the data as a stream of bytes.
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);

		// Wrap the byte array output stream in an input stream reader,
		// so you can read the data as a stream of characters.
		InputStreamReader isr = new InputStreamReader(bais);

		// Wrap the input stream reader in a bufferred reader,
		// so you can read the character data a line at a time.
		// (A line is a sequence of chars terminated by any combination of \r and \n.) 
		BufferedReader br = new BufferedReader(isr);

		// The message data is contained in a single line, so read this line.
		String line = br.readLine();

		// Print host address and data received from it.
		System.out.println(
			"Received from " + 
			request.getAddress().getHostAddress() + 
			": " +
			new String(line) );
	}
}
