import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;

/*
* the client
*/





/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

public class PingClient
{

   public static void main(String[] args) throws Exception
   {
      // Get command line argument.
      if (args.length != 2) {
         System.out.println("Required arguments: host port");
         return;
      }
      InetAddress host = InetAddress.getByName(args[0]);
      
      int port = Integer.parseInt(args[1]);

      // Create a datagram socket for sending UDP packets
      // finds its own available port
      DatagramSocket socket = new DatagramSocket();
    
      //make the socket only block for 1 second   
      socket.setSoTimeout(1000);
      
      ArrayList<Long> rttTimes = new ArrayList<Long>();
//      long rttTimes = new long[];
      
      //send ping 10 times
      for(int i = 0; i < 10; i++) {
  
         //get the current time
         long sendTime = System.currentTimeMillis();

         //initalise the stuff to send in the datagrampacket, i.e. data being pinged
         byte[] buf = new byte[1024];         
         String data = "PING " + i + " " + sendTime + "\r\n";
         //initialise the packet with 
         DatagramPacket toSend = new DatagramPacket(buf, buf.length, host, port);
  
         // Create a datagram packet to hold incomming UDP packet.
         DatagramPacket request = new DatagramPacket(new byte[1024], 1024);

         // Block until the host receives a UDP packet.
         socket.send(toSend);
         
         try{
            socket.receive(request);
            
            long receiveTime = System.currentTimeMillis();
            long rtt = receiveTime - sendTime;
            rttTimes.add(rtt);
            //printData(request);
            //System.out.println("send time: " + sendTime + " receive time" + receiveTime);
            System.out.println("ping to " + host + ", seq = " + i + ", rtt = " + rtt + " ms");

         } catch (SocketTimeoutException e) {
            System.out.println("ping to " + host + ", seq = " + i + ", timeout");         

         }
         
         //You will also need to report the minimum, maximum and the average RTTs of all packets received successfully at the end of your program's output. 
         
      }
     if(rttTimes.size() > 0) {
         long min = rttTimes.get(0);
         long max = rttTimes.get(0);
         long total = 0;
     
         for (long value : rttTimes) {
            if (value < min) {
                min = value;
            } else if (value > max) {
                max = value;
            }
            total += value;
         }
         long average = total / rttTimes.size();
         System.out.println("min: " + min + "ms, max: " + max + "ms, avg " + average + "ms");
         
     } else {
        System.out.println("all packets timed out");
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
